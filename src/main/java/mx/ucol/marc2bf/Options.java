package mx.ucol.marc2bf;

import java.net.URI;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;
import org.apache.jena.riot.Lang;

final class Options {
    Path input;
    Path output;
    Path report;
    String base = "https://bibliotecas.ucol.mx/bibframe/";
    String idField = "001";
    String idSource = "";
    String encoding;
    String inputFormat = "auto";
    String datestamp = OffsetDateTime.now().toString();
    Lang format;
    boolean repair;
    boolean caret;
    boolean preprocess = true;
    boolean localFields;
    boolean inferScript = true;
    boolean preserveInRdf;
    boolean generateIds;
    boolean validate;
    int limit;

    static Options parse(String[] args) {
        if (args.length < 2) throw new IllegalArgumentException("Indica archivo de entrada y archivo de salida. Usa --help.");
        var options = new Options();
        options.input = Path.of(args[0]).toAbsolutePath().normalize();
        options.output = Path.of(args[1]).toAbsolutePath().normalize();
        for (int i = 2; i < args.length; i++) {
            String name = args[i];
            switch (name) {
                case "--repair" -> options.repair = true;
                case "--repair-carets" -> { options.repair = true; options.caret = true; }
                case "--no-preprocess" -> options.preprocess = false;
                case "--lc-localfields" -> options.localFields = true;
                case "--no-script-inference" -> options.inferScript = false;
                case "--preserve-in-rdf" -> options.preserveInRdf = true;
                case "--generate-ids" -> options.generateIds = true;
                case "--validate" -> options.validate = true;
                case "--base", "--id-field", "--id-source", "--encoding", "--input-format", "--format", "--report", "--limit", "--generation-date" -> {
                    if (++i >= args.length) throw new IllegalArgumentException("Falta el valor de " + name);
                    String value = args[i];
                    switch (name) {
                        case "--base" -> options.base = value;
                        case "--id-field" -> options.idField = value;
                        case "--id-source" -> options.idSource = value;
                        case "--encoding" -> options.encoding = java.nio.charset.Charset.forName(value).name();
                        case "--input-format" -> options.inputFormat = value;
                        case "--format" -> options.format = parseFormat(value);
                        case "--report" -> options.report = Path.of(value).toAbsolutePath().normalize();
                        case "--limit" -> options.limit = Integer.parseInt(value);
                        case "--generation-date" -> { OffsetDateTime.parse(value); options.datestamp = value; }
                    }
                }
                default -> throw new IllegalArgumentException("Opción desconocida: " + name);
            }
        }
        if (!options.idField.matches("[0-9]{3}[a-z0-9]?")) throw new IllegalArgumentException("--id-field debe ser 001, 035a u otra etiqueta/subcampo MARC.");
        if (options.generateIds && !options.idField.equals("001")) throw new IllegalArgumentException("--generate-ids requiere --id-field 001.");
        if (options.repair && options.encoding != null && !options.encoding.equals("UTF-8")) throw new IllegalArgumentException("--repair admite UTF-8/UTF-16; no se combina con otra --encoding.");
        if (options.limit < 0) throw new IllegalArgumentException("--limit no puede ser negativo.");
        if (!Set.of("auto", "marc", "marcxml").contains(options.inputFormat)) throw new IllegalArgumentException("--input-format: auto, marc o marcxml.");
        requireHttpUri(options.base, "--base");
        if (!options.base.endsWith("/")) options.base += "/";
        if (!options.idSource.isEmpty()) requireHttpUri(options.idSource, "--id-source");
        if (options.format == null) {
            String name = options.output.toString().toLowerCase(Locale.ROOT);
            options.format = name.endsWith(".nt") ? Lang.NTRIPLES : name.endsWith(".ttl") ? Lang.TURTLE :
                    name.endsWith(".jsonld") || name.endsWith(".json") ? Lang.JSONLD : Lang.RDFXML;
        }
        if (options.report == null) options.report = Path.of(options.output + ".report.csv");
        return options;
    }

    static Lang parseFormat(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "rdfxml", "rdf", "rdf/xml" -> Lang.RDFXML;
            case "turtle", "ttl" -> Lang.TURTLE;
            case "ntriples", "nt" -> Lang.NTRIPLES;
            case "jsonld", "json-ld" -> Lang.JSONLD;
            default -> throw new IllegalArgumentException("Formato no soportado: " + name);
        };
    }
    private static void requireHttpUri(String value, String name) {
        var uri = URI.create(value);
        if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getFragment() != null || uri.getQuery() != null) {
            throw new IllegalArgumentException(name + " requiere una URI HTTP(S) absoluta sin fragmento ni query.");
        }
    }
    Map<String, Object> parameters() {
        return Map.of("baseuri", base, "idfield", idField, "idsource", idSource,
                "localfields", localFields, "bcp47inferrence", inferScript,
                "pGenerationDatestamp", datestamp, "serialization", "rdfxml");
    }
}
