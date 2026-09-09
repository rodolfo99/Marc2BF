package mx.ucol.marc2bf;

import java.io.*;
import java.nio.file.*;
import javax.xml.stream.*;
import org.marc4j.MarcStreamReader;
import org.marc4j.MarcXmlWriter;
import org.marc4j.marc.*;
import org.marc4j.marc.Record;

/** Lectura secuencial de ISO2709 y MARCXML; nunca omite registros con errores. */
final class MarcInput {
    static final String NS = "http://www.loc.gov/MARC21/slim";
    @FunctionalInterface interface Consumer { void accept(Record record) throws Exception; }

    static boolean isXml(Path file) throws IOException {
        try (var in = Files.newInputStream(file)) {
            byte[] prefix = in.readNBytes(256);
            String text = new String(prefix, java.nio.charset.StandardCharsets.UTF_8).replace("\uFEFF", "").stripLeading();
            return text.startsWith("<") || (prefix.length > 3 &&
                    ((prefix[0] == -1 && prefix[1] == -2 && prefix[2] == '<') ||
                     (prefix[0] == -2 && prefix[1] == -1 && prefix[3] == '<')));
        }
    }

    static void read(Path path, boolean xml, String encoding, Consumer consumer) throws Exception {
        try (var in = new BufferedInputStream(Files.newInputStream(path))) {
            if (xml) readXml(in, consumer);
            else {
                var reader = encoding == null ? new MarcStreamReader(in) : new MarcStreamReader(in, encoding);
                while (reader.hasNext()) consumer.accept(reader.next());
            }
        }
    }

    private static void readXml(InputStream in, Consumer consumer) throws Exception {
        var factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        factory.setXMLResolver((publicId, systemId, base, namespace) -> {
            throw new XMLStreamException("No se permiten entidades XML externas.");
        });
        var reader = factory.createXMLStreamReader(in);
        var marc = MarcFactory.newInstance();
        Record record = null;
        DataField field = null;
        boolean rootSeen = false;
        boolean leaderSeen = false;
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.DTD) throw new IOException("MARCXML no admite DTD.");
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    if (!NS.equals(reader.getNamespaceURI())) throw new IOException("Namespace MARCXML incorrecto: " + reader.getNamespaceURI());
                    if (!rootSeen) {
                        rootSeen = true;
                        if (!name.equals("collection") && !name.equals("record")) throw new IOException("Se esperaba record o collection MARCXML.");
                    }
                    switch (name) {
                        case "collection" -> { if (record != null) throw new IOException("collection dentro de record."); }
                        case "record" -> {
                            if (record != null) throw new IOException("Registro MARCXML anidado.");
                            record = marc.newRecord(); leaderSeen = false;
                            String type = reader.getAttributeValue(null, "type");
                            if (type != null && !type.equals("Bibliographic")) throw new IOException("Solo MARC bibliográfico; tipo recibido: " + type);
                        }
                        case "leader" -> {
                            requireRecord(record);
                            String value = reader.getElementText();
                            if (leaderSeen || value.length() != 24) throw new IOException("Leader debe tener 24 caracteres y aparecer una vez.");
                            record.setLeader(marc.newLeader(value)); leaderSeen = true;
                        }
                        case "controlfield" -> {
                            requireRecord(record);
                            String tag = tag(reader);
                            if (field != null) throw new IOException("Campo de control anidado.");
                            record.addVariableField(marc.newControlField(tag, reader.getElementText()));
                        }
                        case "datafield" -> {
                            requireRecord(record);
                            if (field != null) throw new IOException("Campo de datos anidado.");
                            field = marc.newDataField(tag(reader), character(reader, "ind1"), character(reader, "ind2"));
                        }
                        case "subfield" -> {
                            if (field == null) throw new IOException("Subcampo fuera de datafield.");
                            char code = character(reader, "code");
                            field.addSubfield(marc.newSubfield(code, reader.getElementText()));
                        }
                        default -> throw new IOException("Elemento MARCXML no reconocido: " + name);
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    switch (reader.getLocalName()) {
                        case "datafield" -> { record.addVariableField(field); field = null; }
                        case "record" -> {
                            if (!leaderSeen) throw new IOException("Registro sin leader.");
                            consumer.accept(record); record = null;
                        }
                        default -> { }
                    }
                }
            }
        } finally { reader.close(); }
    }

    private static void requireRecord(Record record) throws IOException {
        if (record == null) throw new IOException("Campo fuera de record.");
    }
    private static String tag(XMLStreamReader reader) throws IOException {
        String value = reader.getAttributeValue(null, "tag");
        if (value == null || !value.matches("[0-9]{3}")) throw new IOException("Etiqueta MARC inválida: " + value);
        return value;
    }
    private static char character(XMLStreamReader reader, String name) throws IOException {
        String value = reader.getAttributeValue(null, name);
        if (value == null || value.length() != 1) throw new IOException("Atributo " + name + " debe tener un carácter.");
        return value.charAt(0);
    }

    static byte[] toXml(Record record) {
        var bytes = new ByteArrayOutputStream();
        var writer = new MarcXmlWriter(bytes, true);
        writer.write(record);
        writer.close();
        return bytes.toByteArray();
    }

    static String identifier(Record record, String field) {
        String tag = field.substring(0, 3);
        var value = record.getVariableField(tag);
        if (value instanceof ControlField control) return control.getData();
        if (value instanceof DataField data) {
            var subfield = data.getSubfield(field.length() == 4 ? field.charAt(3) : 'a');
            return subfield == null ? "" : subfield.getData();
        }
        return "";
    }
    private MarcInput() {}
}
