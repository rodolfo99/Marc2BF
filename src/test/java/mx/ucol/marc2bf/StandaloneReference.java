package mx.ucol.marc2bf;

import java.nio.file.*;
import java.util.*;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.*;

/** Referencia independiente: API s9api, hojas originales en disco, resolución estándar.
 * No utiliza LcEngine, su URIResolver ni recursos extraídos del JAR.
 */
public final class StandaloneReference {
    public static void main(String[] args) throws Exception {
        var processor = new Processor(false);
        var compiler = processor.newXsltCompiler();
        var main = compiler.compile(new StreamSource(Path.of("vendor/lc/xsl/marc2bibframe2.xsl").toFile()));
        var split = compiler.compile(new StreamSource(Path.of("vendor/lc/xsl/ConvSpec-Preprocess0-Splitting.xsl").toFile()));
        Map<String, XdmAtomicValue> params = Map.of(
                "baseuri", new XdmAtomicValue("https://example.org/catalog/"),
                "pGenerationDatestamp", new XdmAtomicValue("2026-09-09T00:00:00Z"),
                "localfields", new XdmAtomicValue(false),
                "bcp47inferrence", new XdmAtomicValue(true),
                "serialization", new XdmAtomicValue("rdfxml"));
        int count = 0;
        for (String line : Files.readAllLines(Path.of(args[0]))) {
            String[] task = line.split("\t");
            var source = processor.newDocumentBuilder().build(new StreamSource(Path.of(task[0]).toFile()));
            if (Boolean.parseBoolean(task[2])) {
                var transform = split.load();
                params.forEach((key, value) -> transform.setParameter(new QName(key), value));
                transform.setInitialContextNode(source);
                var intermediate = new XdmDestination();
                transform.setDestination(intermediate);
                transform.transform();
                source = intermediate.getXdmNode();
            }
            var transform = main.load();
            params.forEach((key, value) -> transform.setParameter(new QName(key), value));
            transform.setInitialContextNode(source);
            transform.setDestination(processor.newSerializer(Path.of(task[1] + ".reference.rdf").toFile()));
            transform.transform();
            System.out.println("Reference " + (++count) + ": " + task[0]);
        }
    }
}
