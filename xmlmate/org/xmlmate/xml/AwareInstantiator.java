package org.xmlmate.xml;

import nu.xom.Attribute;
import org.apache.xerces.xs.StringList;
import org.apache.xerces.xs.XSConstants;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSNamedMap;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AwareInstantiator {
    private static final Logger logger = LoggerFactory.getLogger(AwareInstantiator.class);

    private AwareInstantiator() {
        // prohibit creation
    }

    private static QName guessRootElem() {
        XSNamedMap elemDecls = XMLProperties.SCHEMA_INSTANCE.getComponents(XSConstants.ELEMENT_DECLARATION);
        List<XSElementDeclaration> elements = new ArrayList<>(elemDecls.size());
        for (int i = 0; i < elemDecls.getLength(); i++) {
            XSElementDeclaration elem = (XSElementDeclaration) elemDecls.item(i);
            elements.add(elem);
        }
        for (Iterator<XSElementDeclaration> iterator = elements.iterator(); iterator.hasNext(); ) {
            XSElementDeclaration elem = iterator.next();
            if (elem.getSubstitutionGroupAffiliation() != null)
                iterator.remove();
            // XXX also remove elements referenced by others
        }
        if (!elements.isEmpty()) {
            XSElementDeclaration elem = elements.get(Randomness.nextInt(elements.size()));
            QName qname = new QName(elem.getNamespace(), elem.getName());
            logger.debug("Guessed {} as root element.", qname);
            return qname;
        }
        throw new IllegalArgumentException("There are no root elements in given schema!");
    }

    public static AwareDocument generate(QName rootElement) {
        XSElementDeclaration rootDecl = XMLProperties.SCHEMA_INSTANCE.getElementDeclaration(rootElement.getLocalPart(), rootElement.getNamespaceURI());
        if (rootDecl == null)
            throw new IllegalArgumentException("Element " + rootElement + " is not found!");

        NamespaceManager nsm = NamespaceManager.getInstance();
        String rootName = nsm.getQName(rootElement.getLocalPart(), rootElement.getNamespaceURI());
        AwareElement rootElem = new AwareElement(rootName, rootElement.getNamespaceURI(), rootDecl);
        StringList lst = XMLProperties.SCHEMA_INSTANCE.getNamespaces();
        for (int i = 0; i < lst.getLength(); i++) {
            String it = lst.item(i);
            if (it != null && !it.equals(XMLConstants.W3C_XML_SCHEMA_NS_URI))
                rootElem.addNamespaceDeclaration(nsm.getPrefix(it), it);
        }
        AwareDocument doc = new AwareDocument(rootElem);// the root element is not in the element map!
        rootElem.mutate();
        // patch in schema location
        if (null != XMLProperties.TARGET_NAMESPACE && !XMLProperties.TARGET_NAMESPACE.isEmpty()) {
            Attribute schemaLoc = rootElem.getAttribute("xsi:schemaLocation", "http://www.w3.org/2001/XMLSchema-instance");
            if (null != schemaLoc)
                rootElem.removeAttribute(schemaLoc);
            rootElem.addAttribute(new Attribute("xsi:schemaLocation", "http://www.w3.org/2001/XMLSchema-instance", XMLProperties.TARGET_NAMESPACE + ' ' + XMLProperties.SCHEMA_PATH));
        } else {
            Attribute schemaLoc = rootElem.getAttribute("xsi:noNamespaceSchemaLocation", "http://www.w3.org/2001/XMLSchema-instance");
            if (null != schemaLoc)
                rootElem.removeAttribute(schemaLoc);
            rootElem.addAttribute(new Attribute("xsi:noNamespaceSchemaLocation", "http://www.w3.org/2001/XMLSchema-instance", XMLProperties.SCHEMA_PATH));
        }

        return doc;
    }

    public static AwareDocument generate() {
        return generate(guessRootElem());
    }
}
