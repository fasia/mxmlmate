package org.xmlmate.xml.metrics;

import org.apache.xerces.xs.*;
import org.xmlmate.XMLProperties;

/**
 * This visitor records all elements and attributes to {@link org.evosuite.Properties} XML_SCHEMA_ALL_ELEMENTS and XML_SCHEMA_ALL_ATTRIBUTES
 */
public class SchemaAllVisitor implements SchemaElementVisitor {

    @Override
    public void visit(XSElementDeclaration decl) {
        XSTypeDefinition type = decl.getTypeDefinition();
        if (XMLProperties.SCHEMA_ALL_ELEMENTS.add(decl)) {
            // if this declaration is new, also consider attributes
            if (type.getTypeCategory() == XSTypeDefinition.COMPLEX_TYPE) {
                XSObjectList attrs = ((XSComplexTypeDefinition) type).getAttributeUses();
                for (int i = 0; i < attrs.getLength(); i++) {
                    XSAttributeUse use = (XSAttributeUse) attrs.item(i);
                    XMLProperties.SCHEMA_ALL_ATTRS.add(use.getAttrDeclaration());
                }
            }
        }
    }

}
