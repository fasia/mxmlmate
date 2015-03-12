package org.xmlmate.xml.metrics;

import org.apache.xerces.xs.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;

public class TypeNameLister implements SchemaElementVisitor {
    private static final Logger logger = LoggerFactory.getLogger(TypeNameLister.class);
    private final HashSet<String> seenTypes;


    public TypeNameLister() {
        seenTypes = new HashSet<>();
    }

    @Override
    public void visit(XSElementDeclaration decl) {
        XSTypeDefinition td = decl.getTypeDefinition();
        XSSimpleTypeDefinition stype = null;
        if (td.getTypeCategory() == XSTypeDefinition.SIMPLE_TYPE) {
            stype = (XSSimpleTypeDefinition) td;
        } else {
            XSComplexTypeDefinition ctype = (XSComplexTypeDefinition) td;
            XSObjectList attrs = ctype.getAttributeUses();
            for (int i = 0; i < attrs.getLength(); i++) {
                XSAttributeUse use = (XSAttributeUse) attrs.item(i);
                String name = use.getAttrDeclaration().getTypeDefinition().getName();
                if (seenTypes.add(name))
                    logger.info("Type {}", name);
            }
            if (ctype.getContentType() == XSComplexTypeDefinition.CONTENTTYPE_SIMPLE) {
                stype = ctype.getSimpleType();
            }
        }
        if (stype != null) {
            String name = stype.getName();
            if (seenTypes.add(name))
                logger.info("Type {}", name);
        }
    }
}
