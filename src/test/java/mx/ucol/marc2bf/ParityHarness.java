package mx.ucol.marc2bf;

import java.nio.file.*;
import java.util.*;

/** Adaptador de prueba para comparar Saxon/JAR con libxslt, sin modificar los fixtures LC. */
public final class ParityHarness {
    public static void main(String[] args) throws Exception {
        var engine = new LcEngine();
        Map<String, Object> params = Map.of("baseuri", "https://example.org/catalog/",
                "pGenerationDatestamp", "2026-09-09T00:00:00Z", "localfields", false,
                "bcp47inferrence", true, "serialization", "rdfxml");
        int count = 0;
        for (String line : Files.readAllLines(Path.of(args[0]))) {
            String[] task = line.split("\t");
            byte[] rdf = engine.convert(Files.readAllBytes(Path.of(task[0])), params, Boolean.parseBoolean(task[2]));
            Files.write(Path.of(task[1]), rdf);
            System.out.println("Converted " + (++count) + ": " + task[0] + " split=" + task[2]);
        }
    }
}
