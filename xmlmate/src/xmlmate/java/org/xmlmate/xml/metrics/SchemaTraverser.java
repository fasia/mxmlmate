package org.xmlmate.xml.metrics;

import org.apache.xerces.xs.*;

import java.util.HashSet;

public class SchemaTraverser {
    private final HashSet<XSElementDeclaration> agenda;
    private final HashSet<XSParticle> visited;

    public SchemaTraverser() {
        agenda = new HashSet<>();
        visited = new HashSet<>();
    }

    private void expandAgenda(XSParticle p) {
        if (null == p)
            return;
        XSTerm term = p.getTerm();
        if (term instanceof XSElementDeclaration) {
            XSElementDeclaration decl = (XSElementDeclaration) term;
            if (!agenda.add(decl))
                return;
            visited.add(p);
            XSTypeDefinition type = decl.getTypeDefinition();
            if (type.getTypeCategory() == XSTypeDefinition.COMPLEX_TYPE) {
                XSParticle particle = ((XSComplexTypeDefinition) type).getParticle();
                if (!visited.contains(particle))
                    expandAgenda(particle);
            }
        } else if (term instanceof XSModelGroup) {
            XSObjectList particles = ((XSModelGroup) term).getParticles();
            for (int i = 0; i < particles.getLength(); i++) {
                XSParticle particle = (XSParticle) particles.item(i);
                if (!visited.contains(particle))
                    expandAgenda(particle);
            }
        }
    }

    public void traverse(XSElementDeclaration decl, SchemaElementVisitor... visitors) {
        for (SchemaElementVisitor visitor : visitors)
            visitor.visit(decl);
        agenda.clear();
        visited.clear();
        XSTypeDefinition type = decl.getTypeDefinition();
        if (type.getTypeCategory() == XSTypeDefinition.COMPLEX_TYPE) {
            expandAgenda(((XSComplexTypeDefinition) type).getParticle());
            for (XSElementDeclaration e : agenda)
                for (SchemaElementVisitor visitor : visitors)
                    visitor.visit(e);
        }
    }
}
