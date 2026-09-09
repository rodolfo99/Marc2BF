package mx.ucol.marc2bf;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.marc4j.MarcStreamWriter;
import static org.junit.jupiter.api.Assertions.*;

class ConverterTest {
    static LcEngine engine;
    @TempDir Path temp;
    static final String DATE = "2026-09-09T00:00:00Z";
    static final String BF = ConversionJob.BF;
    static final String SAMPLE = """
            <record xmlns="http://www.loc.gov/MARC21/slim" type="Bibliographic">
              <leader>00000nam a2200000 i 4500</leader>
              <controlfield tag="001">ucol-1</controlfield>
              <controlfield tag="008">260909s2026    mx            000 0 spa d</controlfield>
              <datafield tag="020" ind1=" " ind2=" "><subfield code="a">9786070000001</subfield></datafield>
              <datafield tag="050" ind1=" " ind2="4"><subfield code="a">QA76.9</subfield><subfield code="b">.V35 2026</subfield></datafield>
              <datafield tag="100" ind1="1" ind2=" "><subfield code="a">Valencia, Rodolfo</subfield><subfield code="e">autor</subfield><subfield code="4">aut</subfield></datafield>
              <datafield tag="245" ind1="1" ind2="0"><subfield code="a">Inteligencia artificial en bibliotecas :</subfield><subfield code="b">catálogos enlazados /</subfield><subfield code="c">Rodolfo Valencia.</subfield><subfield code="6">880-01</subfield></datafield>
              <datafield tag="264" ind1=" " ind2="1"><subfield code="a">Colima :</subfield><subfield code="b">Universidad de Colima,</subfield><subfield code="c">2026.</subfield></datafield>
              <datafield tag="264" ind1=" " ind2="4"><subfield code="c">©2026</subfield></datafield>
              <datafield tag="300" ind1=" " ind2=" "><subfield code="a">120 páginas ;</subfield><subfield code="c">24 cm</subfield></datafield>
              <datafield tag="336" ind1=" " ind2=" "><subfield code="a">texto</subfield><subfield code="b">txt</subfield><subfield code="2">rdacontent</subfield></datafield>
              <datafield tag="337" ind1=" " ind2=" "><subfield code="a">sin mediación</subfield><subfield code="b">n</subfield><subfield code="2">rdamedia</subfield></datafield>
              <datafield tag="338" ind1=" " ind2=" "><subfield code="a">volumen</subfield><subfield code="b">nc</subfield><subfield code="2">rdacarrier</subfield></datafield>
              <datafield tag="650" ind1=" " ind2="0"><subfield code="a">Libraries</subfield><subfield code="x">Automation</subfield></datafield>
              <datafield tag="655" ind1=" " ind2="7"><subfield code="a">Manuales</subfield><subfield code="2">local</subfield></datafield>
              <datafield tag="880" ind1="1" ind2="0"><subfield code="6">245-01/(N</subfield><subfield code="a">図書館</subfield></datafield>
              <datafield tag="999" ind1="1" ind2="2"><subfield code="a">local uno</subfield><subfield code="a">local dos</subfield><subfield code="9">dato adicional</subfield></datafield>
            </record>
            """;

    @BeforeAll static void start() throws Exception { engine = new LcEngine(); }

    private Path source(String content) throws Exception {
        Path path = temp.resolve(UUID.randomUUID() + ".xml"); Files.writeString(path, content); return path;
    }
    private Options options(Path in, String name, String... extra) {
        var args = new ArrayList<>(List.of(in.toString(), temp.resolve(name).toString(), "--generation-date", DATE));
        args.addAll(List.of(extra)); return Options.parse(args.toArray(String[]::new));
    }
    private Model run(Options options) throws Exception {
        new ConversionJob(engine).run(options);
        return RDFDataMgr.loadModel(options.output.toString(), options.format);
    }

    @Test void officialMappingsAddressThePreviousErrors() throws Exception {
        var options = options(source(SAMPLE), "catalog.rdf", "--no-preprocess", "--validate");
        Model model = run(options);
        try {
            Resource work = model.getResource(options.base + "ucol-1#Work");
            Resource instance = model.getResource(options.base + "ucol-1#Instance");
            assertTrue(work.hasProperty(model.createProperty(BF, "content")));
            assertFalse(instance.hasProperty(model.createProperty(BF, "content")));
            Resource classification = work.getPropertyResourceValue(model.createProperty(BF, "classification"));
            assertEquals("QA76.9", classification.getProperty(model.createProperty(BF, "classificationPortion")).getString());
            assertEquals(".V35 2026", classification.getProperty(model.createProperty(BF, "itemPortion")).getString());
            assertTrue(instance.hasProperty(model.createProperty(BF, "copyrightDate")));
            assertTrue(work.hasProperty(model.createProperty(BF, "genreForm")));
            assertTrue(model.contains(null, model.createProperty("http://www.loc.gov/mads/rdf/v1#componentList")));
            assertTrue(model.contains(null, model.createProperty(BF, "mainTitle"), (RDFNode) null));
            assertTrue(model.listObjectsOfProperty(model.createProperty(BF, "mainTitle")).toList().stream().anyMatch(n -> n.isLiteral() && n.asLiteral().getString().equals("図書館")));
        } finally { model.close(); }
        assertArrayEquals(Files.readAllBytes(options.input), Files.readAllBytes(Path.of(options.output + ".source.xml")));
    }

    @Test void preservationIncludesMappedFieldsRepeatedSubfieldsAndOrder() throws Exception {
        Model model = run(options(source(SAMPLE), "preserved.ttl", "--preserve-in-rdf"));
        try {
            var tag = model.createProperty(SourcePreserver.NS, "tag");
            assertTrue(model.contains(null, tag, "245"));
            Resource local = model.listResourcesWithProperty(tag, "999").next();
            var sub = model.createProperty(SourcePreserver.NS, "subfield");
            assertEquals(3, local.listProperties(sub).toList().size());
            assertEquals("local dos", model.getResource(local.getURI() + "/subfield/2").getProperty(RDF.value).getString());
            assertEquals("2", local.getProperty(model.createProperty(SourcePreserver.NS, "indicator2")).getString());
        } finally { model.close(); }
    }

    @Test void iso2709AndMarcXmlProduceEquivalentGraphs() throws Exception {
        Path xml = source(SAMPLE);
        Path marc = temp.resolve("source.mrc");
        try (var out = Files.newOutputStream(marc)) {
            var writer = new MarcStreamWriter(out, "UTF-8");
            MarcInput.read(xml, true, null, writer::write); writer.close();
        }
        Model a = run(options(xml, "xml.rdf"));
        Model b = run(options(marc, "iso.rdf"));
        try { assertTrue(a.isIsomorphicWith(b)); } finally { a.close(); b.close(); }
    }

    @Test void duplicateIdsFailWithoutPublishingPartialResults() throws Exception {
        Path xml = source("<collection xmlns=\"" + MarcInput.NS + "\">" + SAMPLE + SAMPLE + "</collection>");
        var options = options(xml, "duplicate.rdf");
        assertThrows(IOException.class, () -> new ConversionJob(engine).run(options));
        assertFalse(Files.exists(options.output));
        assertFalse(Files.exists(Path.of(options.output + ".source.xml")));
        Model generated = run(options(xml, "generated.rdf", "--generate-ids"));
        try { assertEquals(2, generated.listSubjectsWithProperty(RDF.type, generated.createResource(BF + "Work")).toList().size()); }
        finally { generated.close(); }
    }

    @Test void outputCannotOverwriteInputOrExistingFiles() throws Exception {
        Path input = source(SAMPLE);
        var options = options(input, "existing.rdf");
        Files.writeString(options.output, "original existente");
        assertThrows(IOException.class, () -> new ConversionJob(engine).run(options));
        assertEquals("original existente", Files.readString(options.output));
        options.output = input;
        assertThrows(IOException.class, () -> new ConversionJob(engine).run(options));
        assertEquals(SAMPLE, Files.readString(input));
    }

    @Test void rejectsDoctypeAndExternalEntities() throws Exception {
        Path input = source("<!DOCTYPE record [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>" + SAMPLE.replace("local uno", "&x;"));
        assertThrows(Exception.class, () -> new ConversionJob(engine).run(options(input, "xxe.rdf")));
        assertThrows(Exception.class, () -> engine.convert(Files.readAllBytes(input), Map.of(), false));
    }

    @Test void allFourSerializationsPreserveTheSameGraph() throws Exception {
        Path input = source("<collection xmlns=\"" + MarcInput.NS + "\">" + SAMPLE + SAMPLE.replace("ucol-1", "ucol-2") + "</collection>");
        Model expected = run(options(input, "baseline.rdf"));
        try {
            for (String extension : List.of("ttl", "nt", "jsonld")) {
                Model actual = run(options(input, "catalog." + extension, "--validate"));
                try { assertTrue(expected.isIsomorphicWith(actual), extension); } finally { actual.close(); }
            }
        } finally { expected.close(); }
    }

    @Test void repairUtf16CaretsPreservesTheExactOriginal() throws Exception {
        Path fixture = Path.of("examples/catalogo-utf16-caret.marc");
        var options = options(fixture, "repair.rdf", "--repair-carets", "--generate-ids");
        Model graph = run(options);
        try { assertFalse(graph.isEmpty()); } finally { graph.close(); }
        assertArrayEquals(Files.readAllBytes(fixture), Files.readAllBytes(Path.of(options.output + ".source.mrc")));
    }

    @Test void repairRejectsInvalidEncodingRatherThanReplacingCharacters() throws Exception {
        Path invalid = temp.resolve("invalid.mrc");
        Files.write(invalid, new byte[]{(byte) 0xc3, 0x28});
        assertThrows(IOException.class, () -> MarcRepairService.repair(invalid, temp.resolve("out.mrc"), false));
    }

    @Test void embeddedUpstreamFilesMatchTheirPinnedChecksums() throws Exception {
        var manifest = Path.of("vendor/lc/SHA256SUMS");
        for (String line : Files.readAllLines(manifest)) {
            String[] parts = line.split("  ", 2);
            assertEquals(parts[0], ConversionJob.sha256(Path.of("vendor/lc").resolve(parts[1])), parts[1]);
        }
    }

    @Test void preprocessorProducesSeveralInstancesForPhysicalAndElectronicVersions() throws Exception {
        Path fixture = Path.of("vendor/lc/test/data/ConvSpec-Preprocess0-Splitting/two007s-oneAoneC-one856.marc.xml");
        Model graph = run(options(fixture, "split.rdf", "--validate"));
        try {
            assertTrue(graph.listSubjectsWithProperty(RDF.type, graph.createResource(BF + "Instance")).toList().size() >= 2);
        } finally { graph.close(); }
    }

    @Test void codedDatesCanReadTheStylesheetInternalTable() throws Exception {
        String record = SAMPLE.replace("</record>", "<datafield tag=\"045\" ind1=\" \" ind2=\" \"><subfield code=\"a\">d5d9</subfield></datafield></record>");
        Model graph = run(options(source(record), "coded.rdf"));
        try { assertTrue(graph.contains(null, graph.createProperty(BF, "temporalCoverage"))); }
        finally { graph.close(); }
    }
}
