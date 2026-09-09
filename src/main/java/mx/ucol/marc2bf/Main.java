package mx.ucol.marc2bf;

/** Punto de entrada del JAR ejecutable. */
public final class Main {
    public static void main(String[] args) {
        try {
            if (args.length == 0 || args.length == 1 && (args[0].equals("--help") || args[0].equals("-h"))) {
                help(); return;
            }
            if (args.length == 1 && args[0].equals("--version")) {
                var properties = new java.util.Properties();
                try (var input = Main.class.getResourceAsStream("/lc/upstream.properties")) { properties.load(input); }
                System.out.println("Marc2BF 2.0.0 | LC " + properties.getProperty("converter.version") + " | " + properties.getProperty("commit"));
                return;
            }
            var options = Options.parse(args);
            new ConversionJob(new LcEngine()).run(options);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
    private static void help() {
        System.out.println("""
                Marc2BF 2.0.0 — Java 21 + motor completo Library of Congress 3.1.0
                Uso: java -jar target/marc2bf.jar entrada.mrc salida.rdf [opciones]

                  --input-format auto|marc|marcxml  Detectado por contenido por defecto
                  --format rdfxml|turtle|ntriples|jsonld  Inferido por extensión
                  --base URI                      Base de URI de tu catálogo
                  --id-field 001                  Campo identificador; también 035a, etc.
                  --id-source URI                 URI de entidad asignadora
                  --generate-ids                  Sustituye 001 por SHA256 de entrada + posición
                  --encoding UTF-8                Fuerza codificación de ISO2709
                  --repair                        Repara UTF-16/BOM/directorio de ISO2709
                  --repair-carets                 También convierte ^a, ^b, etc. a subcampos
                  --no-preprocess                 Desactiva separación oficial de manifestaciones
                  --lc-localfields                Activa reglas LOCALES DE LC (p. ej. 859)
                  --no-script-inference           No infiere escritura en códigos BCP47
                  --preserve-in-rdf                Añade TODOS los campos MARC como RDF local
                  --generation-date ISO8601       Fecha fija para conversiones reproducibles
                  --report archivo.csv            Reporte por registro (también se crea por defecto)
                  --limit N                       Convierte los primeros N registros
                  --validate                      Relee el RDF; valida sintaxis, no catalogación
                  --version                       Versión y commit del motor oficial

                Siempre guarda original intacto, MARCXML normalizado, inventario y manifiesto.
                No sobrescribe archivos. Si hay un error, no publica resultados parciales.
                Para catálogos grandes usa N-Triples; los otros formatos acumulan el grafo en RAM.
                """);
    }
    private Main() {}
}
