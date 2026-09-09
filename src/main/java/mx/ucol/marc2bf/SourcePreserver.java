package mx.ucol.marc2bf;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.marc4j.marc.*;
import org.marc4j.marc.Record;

/** Extensión optativa: conserva TODOS los campos y su orden, también los ya convertidos por LC. */
final class SourcePreserver {
    static final String NS = "https://bibliotecas.ucol.mx/vocab/marc/";

    static void add(Model model, Record record, String uri) {
        Resource source = model.createResource(uri);
        source.addProperty(RDF.type, model.createResource(NS + "Record"));
        source.addLiteral(model.createProperty(NS, "leader"), record.getLeader().toString());
        int position = 0;
        for (VariableField field : record.getVariableFields()) {
            Resource node = model.createResource(uri + "/field/" + (++position));
            source.addProperty(model.createProperty(NS, "field"), node);
            node.addProperty(RDF.type, model.createResource(NS + "Field"));
            node.addLiteral(model.createProperty(NS, "position"), position);
            node.addLiteral(model.createProperty(NS, "tag"), field.getTag());
            if (field instanceof ControlField control) node.addLiteral(RDF.value, control.getData());
            if (field instanceof DataField data) {
                node.addLiteral(model.createProperty(NS, "indicator1"), String.valueOf(data.getIndicator1()));
                node.addLiteral(model.createProperty(NS, "indicator2"), String.valueOf(data.getIndicator2()));
                int subPosition = 0;
                for (Subfield sf : data.getSubfields()) {
                    Resource sub = model.createResource(node.getURI() + "/subfield/" + (++subPosition));
                    node.addProperty(model.createProperty(NS, "subfield"), sub);
                    sub.addProperty(RDF.type, model.createResource(NS + "Subfield"));
                    sub.addLiteral(model.createProperty(NS, "position"), subPosition);
                    sub.addLiteral(model.createProperty(NS, "code"), String.valueOf(sf.getCode()));
                    sub.addLiteral(RDF.value, sf.getData());
                }
            }
        }
    }
    private SourcePreserver() {}
}
