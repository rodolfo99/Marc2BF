package mx.ucol.marc2bf;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Map;
import java.util.Properties;
import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import net.sf.saxon.TransformerFactoryImpl;

/** Ejecuta sin modificar las hojas XSLT y tablas oficiales incluidas en el JAR. */
public final class LcEngine {
    private final Templates preprocess;
    private final Templates conversion;
    private final URIResolver resolver;
    private final Properties provenance = new Properties();

    public LcEngine() throws Exception {
        var root = LcEngine.class.getResource("/lc/xsl/");
        if (root == null) throw new IllegalStateException("Faltan los recursos oficiales LC en el JAR.");
        String prefix = root.toExternalForm();
        resolver = (href, base) -> {
            try {
                // URL resuelve también rutas relativas dentro de jar:file:.
                var baseUrl = new java.net.URL(base == null ? prefix : base);
                // document('') debe leer la propia hoja; URL(jarBase, "") pierde su nombre.
                var url = href.isEmpty() ? baseUrl : new java.net.URL(baseUrl, href);
                String resolved = url.toExternalForm();
                if (!resolved.startsWith(prefix) || href.contains("..")) {
                    throw new TransformerException("Recurso XSLT externo no permitido: " + href);
                }
                return new StreamSource(url.openStream(), resolved);
            } catch (Exception e) {
                throw new TransformerException(e);
            }
        };
        var factory = new TransformerFactoryImpl();
        factory.setURIResolver(resolver);
        preprocess = compile(factory, "ConvSpec-Preprocess0-Splitting.xsl");
        conversion = compile(factory, "marc2bibframe2.xsl");
        try (var in = LcEngine.class.getResourceAsStream("/lc/upstream.properties")) {
            provenance.load(in);
        }
    }

    private Templates compile(TransformerFactory factory, String name) throws Exception {
        var url = LcEngine.class.getResource("/lc/xsl/" + name);
        try (var in = url.openStream()) {
            return factory.newTemplates(new StreamSource(in, url.toExternalForm()));
        }
    }

    /** Parámetros tipados: Boolean para localfields y bcp47inferrence, String para el resto. */
    public byte[] convert(byte[] marcXml, Map<String, Object> parameters, boolean split) throws Exception {
        byte[] source = split ? transform(preprocess, marcXml, parameters) : marcXml;
        return transform(conversion, source, parameters);
    }

    private byte[] transform(Templates templates, byte[] source, Map<String, Object> parameters) throws Exception {
        var transformer = templates.newTransformer();
        transformer.setURIResolver(resolver);
        parameters.forEach(transformer::setParameter);
        var output = new ByteArrayOutputStream();
        transformer.transform(Xml.source(source), new StreamResult(output));
        return output.toByteArray();
    }

    public String version() { return provenance.getProperty("converter.version"); }
    public String commit() { return provenance.getProperty("commit"); }
}
