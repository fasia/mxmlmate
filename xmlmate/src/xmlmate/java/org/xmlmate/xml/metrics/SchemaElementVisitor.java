package org.xmlmate.xml.metrics;

import org.apache.xerces.xs.XSElementDeclaration;

public interface SchemaElementVisitor {
    public void visit(XSElementDeclaration decl);
}
