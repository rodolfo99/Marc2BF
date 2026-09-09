package mx.ucol.marc2bf;

import java.io.ByteArrayInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import org.xml.sax.InputSource;

/** XML de entrada sin DTD, entidades externas ni acceso a red. */
final class Xml {
    static SAXSource source(byte[] bytes) throws Exception {
        var factory = SAXParserFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return new SAXSource(factory.newSAXParser().getXMLReader(), new InputSource(new ByteArrayInputStream(bytes)));
    }
    private Xml() {}
}
