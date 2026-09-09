package mx.ucol.marc2bf;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.xml.stream.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.riot.system.*;
import org.apache.jena.vocabulary.RDF;
import org.marc4j.MarcXmlWriter;
import org.marc4j.marc.MarcFactory;

/** Orquesta lectura, motor oficial, serialización, respaldo y reporte. */
final class ConversionJob {
    static final String BF = "http://id.loc.gov/ontologies/bibframe/";
    private final LcEngine engine;
    ConversionJob(LcEngine engine) { this.engine = engine; }

    long run(Options o) throws Exception {
        if (!Files.isRegularFile(o.input)) throw new IOException("No se puede leer la entrada: " + o.input);
        boolean xml = o.inputFormat.equals("marcxml") || o.inputFormat.equals("auto") && MarcInput.isXml(o.input);
        if (xml && (o.repair || o.encoding != null)) throw new IllegalArgumentException("--repair/--encoding son para ISO2709; MARCXML declara su codificación.");
        Path sourcePath = Path.of(o.output + (xml ? ".source.xml" : ".source.mrc"));
        Path normalizedPath = Path.of(o.output + ".marcxml");
        Path fieldsPath = Path.of(o.output + ".fields.csv");
        Path manifestPath = Path.of(o.output + ".manifest.properties");
        var counts = new long[2];
        var tags = new TreeMap<String, Long>();
        var ids = new HashSet<String>();
        var workUris = new HashSet<String>();
        try (var bundle = new OutputBundle(o.input)) {
            Path stagedSource = bundle.stage(sourcePath);
            Path stagedNormalized = bundle.stage(normalizedPath);
            Path stagedReport = bundle.stage(o.report);
            Path stagedFields = bundle.stage(fieldsPath);
            Path stagedRdf = bundle.stage(o.output);
            Path stagedManifest = bundle.stage(manifestPath);
            Files.copy(o.input, stagedSource, StandardCopyOption.REPLACE_EXISTING);
            String sourceHash = sha256(stagedSource);
            // Convertir la copia garantiza que el respaldo corresponde a la entrada procesada.
            Path readPath = stagedSource;
            var manifest = new Properties();
            manifest.setProperty("source.sha256", sourceHash);
            manifest.setProperty("source.filename", o.input.getFileName().toString());
            manifest.setProperty("lc.commit", engine.commit());
            manifest.setProperty("lc.version", engine.version());
            manifest.setProperty("preprocess", Boolean.toString(o.preprocess));
            manifest.setProperty("preserve.in.rdf", Boolean.toString(o.preserveInRdf));
            manifest.setProperty("generate.ids", Boolean.toString(o.generateIds));
            manifest.setProperty("limit", Integer.toString(o.limit));
            manifest.setProperty("input.encoding.override", o.encoding == null ? "none" : o.encoding);
            o.parameters().forEach((key, value) -> manifest.setProperty("parameter." + key, value.toString()));
            if (o.repair) {
                readPath = bundle.stage(Path.of(o.output + ".repaired.mrc"));
                var repaired = MarcRepairService.repair(stagedSource, readPath, o.caret);
                manifest.setProperty("repair.encoding", repaired.encodingConversion());
                manifest.setProperty("repair.records", Integer.toString(repaired.recordsRebuilt()));
                manifest.setProperty("repair.carets", Integer.toString(repaired.caretSubfieldsConverted()));
            }
            Model aggregate = ModelFactory.createDefaultModel();
            try (var out = new BufferedOutputStream(Files.newOutputStream(stagedRdf));
                 var normalized = new BufferedOutputStream(Files.newOutputStream(stagedNormalized));
                 var report = Files.newBufferedWriter(stagedReport, StandardCharsets.UTF_8)) {
                var marcWriter = new MarcXmlWriter(normalized, true);
                boolean streaming = o.format.equals(Lang.NTRIPLES);
                StreamRDF sink = streaming ? StreamRDFWriter.getWriterStream(out, RDFFormat.NTRIPLES_UTF8) : null;
                if (sink != null) sink.start();
                report.write("record_index,record_id,source_fields,lc_triples,works,instances,items,status\n");
                try {
                    MarcInput.read(readPath, xml, o.repair ? "UTF-8" : o.encoding, record -> {
                        if (o.limit > 0 && counts[0] >= o.limit) throw new LimitReached();
                        long index = ++counts[0];
                        Model preserved = ModelFactory.createDefaultModel();
                        try {
                            if (o.preserveInRdf) SourcePreserver.add(preserved, record, o.base + "_source/" + sourceHash + "/" + index);
                            if (o.generateIds) {
                                for (var existing : new ArrayList<>(record.getVariableFields("001"))) record.removeVariableField(existing);
                                record.addVariableField(MarcFactory.newInstance().newControlField("001", "src-" + sourceHash + "-" + index));
                            }
                            String id = MarcInput.identifier(record, o.idField);
                            if (id == null || id.isBlank()) throw new IOException("Falta " + o.idField + ". Puedes usar --generate-ids.");
                            if (!ids.add(id)) throw new IOException("Identificador repetido: " + id + ". Puedes usar --generate-ids.");
                            for (var field : record.getVariableFields()) tags.merge(field.getTag(), 1L, Long::sum);
                            marcWriter.write(record);
                            byte[] rdf = engine.convert(MarcInput.toXml(record), o.parameters(), o.preprocess);
                            for (String work : primaryWorks(rdf)) {
                                if (!workUris.add(work)) throw new IOException("Colisión de URI de obra: " + work + ". Puedes usar --generate-ids.");
                            }
                            Model graph = ModelFactory.createDefaultModel();
                            try {
                                RDFParser.create().source(new ByteArrayInputStream(rdf)).lang(Lang.RDFXML).parse(graph);
                                long triples = graph.size();
                                report.write(index + "," + csv(id) + "," + record.getVariableFields().size() + "," + triples + "," +
                                        instancesOf(graph, "Work") + "," + instancesOf(graph, "Instance") + "," + instancesOf(graph, "Item") + ",converted\n");
                                graph.add(preserved);
                                counts[1] += graph.size();
                                if (sink != null) StreamRDFOps.sendGraphToStream(graph.getGraph(), sink);
                                else aggregate.add(graph);
                            } finally { graph.close(); }
                            if (index % 100 == 0) System.out.println("Procesados: " + index);
                        } catch (Exception e) {
                            throw new IOException("Registro " + index + ": " + e.getMessage(), e);
                        } finally { preserved.close(); }
                    });
                } catch (LimitReached ignored) {
                    // Límite explícito solicitado por el usuario, registrado en el manifiesto.
                } finally { marcWriter.close(); }
                if (counts[0] == 0) throw new IOException("La entrada no contiene registros MARC bibliográficos.");
                if (sink != null) sink.finish();
                else RDFDataMgr.write(out, aggregate, o.format);
                manifest.setProperty("records.converted", Long.toString(counts[0]));
                manifest.setProperty("triples.per.record.sum", Long.toString(counts[1]));
                if (!streaming) manifest.setProperty("triples.unique", Long.toString(aggregate.size()));
            } finally { aggregate.close(); }
            if (o.validate) {
                RDFParser.create().source(stagedRdf).lang(o.format).parse(new StreamRDFBase() {});
                manifest.setProperty("rdf.syntax.validation", "passed");
            } else manifest.setProperty("rdf.syntax.validation", "not-requested");
            try (var writer = Files.newBufferedWriter(stagedFields)) {
                writer.write("tag,occurrences\n");
                for (var entry : tags.entrySet()) writer.write(entry.getKey() + "," + entry.getValue() + "\n");
            }
            manifest.setProperty("output.sha256", sha256(stagedRdf));
            manifest.setProperty("normalized.sha256", sha256(stagedNormalized));
            try (var writer = Files.newBufferedWriter(stagedManifest, StandardCharsets.UTF_8)) {
                manifest.store(writer, "Marc2BF 2.0.0; converted no equivale a cobertura semantica de cada campo");
            }
            bundle.publish();
        }
        System.out.println("Conversión terminada: " + counts[0] + " registros; motor LC " + engine.version());
        System.out.println("RDF: " + o.output);
        System.out.println("Original intacto: " + sourcePath);
        System.out.println("MARCXML normalizado: " + normalizedPath);
        System.out.println("Reporte: " + o.report);
        return counts[0];
    }

    private static Set<String> primaryWorks(byte[] xml) throws Exception {
        var factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        var reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
        var result = new HashSet<String>();
        int depth = 0;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                if (depth == 2 && BF.equals(reader.getNamespaceURI()) && "Work".equals(reader.getLocalName())) {
                    String uri = reader.getAttributeValue(RDF.uri, "about");
                    if (uri != null) result.add(uri);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) depth--;
        }
        reader.close();
        if (result.isEmpty()) throw new IOException("El motor LC no generó una obra principal.");
        return result;
    }
    private static long instancesOf(Model model, String name) {
        var iterator = model.listResourcesWithProperty(RDF.type, model.createResource(BF + name));
        try { return iterator.toList().size(); } finally { iterator.close(); }
    }
    static String sha256(Path path) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = new DigestInputStream(Files.newInputStream(path), digest)) { input.transferTo(OutputStream.nullOutputStream()); }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static String csv(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
    private static final class LimitReached extends RuntimeException {}
}
