package org.xmlmate.xml;

import nu.xom.Attribute;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.ParentNode;
import org.apache.xerces.impl.xs.opti.DefaultXMLDocumentHandler;
import org.apache.xerces.xni.Augmentations;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLAttributes;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xs.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;

public class SchemaPatcher extends DefaultXMLDocumentHandler {
    private static final Logger logger = LoggerFactory.getLogger(SchemaPatcher.class);
    private ParentNode currentElement;

    public SchemaPatcher(AwareDocument doc) {
        currentElement = doc;
    }

    /**
     * @return the attribute of <code>currentElement</code> or null.
     */
    private AwareAttribute getAttr(String name, String uri) {
        if (null == uri)
            uri = ""; // workaround for xerces bug
        Attribute att = ((Element) currentElement).getAttribute(name, uri);
        // if the following condition doesn't hold - the attribute isn't in the document anyway
        if (null != att && att instanceof AwareAttribute)
            return (AwareAttribute) att;
        return null;
    }

    private void patchAttributes(XMLAttributes attributes, XSObjectList uses) {
        for (int i = 0; i < attributes.getLength(); i++) {
            String name = attributes.getLocalName(i);
            String uri = attributes.getURI(i);
            AwareAttribute att = getAttr(name, uri);
            if (null != att) {
                Augmentations augs = attributes.getAugmentations(i);
                AttributePSVI apsvi = (AttributePSVI) augs.getItem("ATTRIBUTE_PSVI");
                // set Declaration
                XSAttributeDeclaration decl = apsvi.getAttributeDeclaration();
                att.setDecl(decl);
                // set AttributeUse
                for (int j = 0; j < uses.getLength(); j++) {
                    XSAttributeUse use = (XSAttributeUse) uses.item(j);
                    if (use.getAttrDeclaration() == decl) {
                        att.setUse(use);
                        break;
                    }
                }
                // handle invalid attributes
                if (ItemPSVI.VALIDITY_VALID != apsvi.getValidity()) {
                    if (null != decl && null != att.getUse()) { // either regenerate them from scratch
                        logger.warn("Regenerating invalid attribute {}", att.getLocalName());
                        att.mutate();
                    } else { // or delete them
                        logger.warn("Removing invalid attribute {}", att.getLocalName());
                        ((Element) currentElement).removeAttribute(att);
                    }
                }
            }
        }
    }

    private void patchElement(QName element, XMLAttributes attributes, Augmentations augs) {
        ElementPSVI psvi = (ElementPSVI) augs.getItem("ELEMENT_PSVI");
        XSElementDeclaration decl = psvi.getElementDeclaration();

        AwareElement child = null;
        // if we are at the documentlevel take the root
        if (currentElement.getDocument() == currentElement)
            child = (AwareElement) currentElement.getDocument().getRootElement();
        else {
            // find this element in the currentElement's children
            Elements children = ((Element) currentElement).getChildElements(element.localpart, element.uri);
            for (int i = 0; i < children.size(); i++) {
                child = (AwareElement) children.get(i);
                if (null == child.getDecl()) {
                    break;
                }
            }
        }
        assert null != child; // TODO handle case where child is not found

        if (null == decl) {
            // if it doesn't appear in the schema - it's alien and vile, and must therefore be deleted
            child.setNamespacePrefix("");
            child.setNamespaceURI(XMLProperties.DEL_NS);
            currentElement = child; // set the child to be the new current element
            return;
        }

        currentElement = child; // set the child to be the new current element
        // patch in the element declaration
        ((AwareElement) currentElement).setDecl(decl);
        // if there are attributes, this must be a complex type!
        if (attributes.getLength() > 0) {
            XSComplexTypeDefinition def = (XSComplexTypeDefinition) decl.getTypeDefinition();
            XSObjectList uses = def.getAttributeUses();
            // patch attributes of this element
            patchAttributes(attributes, uses);
        }
    }

    @Override
    public void startElement(QName element, XMLAttributes attributes, Augmentations augs) throws XNIException {
        patchElement(element, attributes, augs);
    }

    @Override
    public void emptyElement(QName element, XMLAttributes attributes, Augmentations augs) throws XNIException {
        // no endElement event is fired for this
        ParentNode tmp = currentElement;
        patchElement(element, attributes, augs);
        currentElement = tmp;
    }

    @Override
    public void endElement(QName element, Augmentations augs) throws XNIException {
        if (currentElement instanceof AwareDocument)
            return;
        AwareElement tmp = (AwareElement) currentElement;
        currentElement = currentElement.getParent();
        ElementPSVI psvi = (ElementPSVI) augs.getItem("ELEMENT_PSVI");
        if (ItemPSVI.VALIDITY_VALID != psvi.getValidity() && tmp.getDocument().getRootElement() != tmp) {
            if (null != tmp.getDecl()) { // try to recreate from scratch
                logger.warn("Regenerating invalid element {}", tmp.getLocalName());
                tmp.mutate();
            } else { // or remove it
                logger.warn("Removing invalid element {}", tmp.getLocalName());
                currentElement.removeChild(tmp);
            }
        }
        tmp = null;
    }

}
