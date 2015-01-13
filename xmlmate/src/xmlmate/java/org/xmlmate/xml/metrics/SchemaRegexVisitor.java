package org.xmlmate.xml.metrics;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.State;
import org.apache.xerces.xs.*;
import org.xmlmate.XMLProperties;
import org.xmlmate.xml.ValueGenerator;

import java.util.HashSet;
import java.util.Set;

public class SchemaRegexVisitor implements SchemaElementVisitor {
    private final Set<XSSimpleTypeDefinition> visited;

    public SchemaRegexVisitor() {
        visited = new HashSet<>();
    }

    private void visitSimple(XSSimpleTypeDefinition type) {
        if (!visited.add(type))
            return;
        if (type.isDefinedFacet(XSSimpleTypeDefinition.FACET_PATTERN)) {
            ValueGenerator.generateValue(type); // make sure the automaton lands in the ValueGenerator's cache
            Automaton a = ValueGenerator.getCachedAutomaton(type);
            if (null != a) {
                a.minimize();
                for (State s : a.getStates())
                    XMLProperties.SCHEMA_ALL_TRANSITIONS.addAll(s.getTransitions());
            }
        }
    }

    @Override
    public void visit(XSElementDeclaration decl) {
        XSTypeDefinition type = decl.getTypeDefinition();
        if (type.getTypeCategory() == XSTypeDefinition.COMPLEX_TYPE) {
            XSComplexTypeDefinition complexType = (XSComplexTypeDefinition) type;
            XSObjectList attrs = complexType.getAttributeUses();
            for (int i = 0; i < attrs.getLength(); i++) {
                XSAttributeUse use = (XSAttributeUse) attrs.item(i);
                visitSimple(use.getAttrDeclaration().getTypeDefinition());
            }
            if (complexType.getContentType() == XSComplexTypeDefinition.CONTENTTYPE_SIMPLE)
                visitSimple(complexType.getSimpleType());
        } else
            visitSimple((XSSimpleTypeDefinition) type);
    }

}
