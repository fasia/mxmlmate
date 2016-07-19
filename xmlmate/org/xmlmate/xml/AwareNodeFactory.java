package org.xmlmate.xml;

import nu.xom.*;
import org.xmlmate.XMLProperties;

public class AwareNodeFactory extends NodeFactory {

    @Override
    public Element startMakingElement(String name, String namespace) {
        return new AwareElement(name, namespace, null);
    }

    @Override
    public Document startMakingDocument() {
        return new AwareDocument(new AwareElement("fakeRoot", "http://bogus.org", null));
    }

    @Override
    public Element makeRootElement(String name, String namespace) {
        // XXX could theoretically avoid requiring the namespace by using some of the following code
        // XSNamespaceItemList ns = schema.getNamespaceItems();
        // for (int i = 0; i < ns.getLength(); i++) {
        // XSNamespaceItem item = ns.item(i);
        // System.out.println("Item "+i+": "+item.getSchemaNamespace());
        // StringList locs = item.getDocumentLocations();
        // for (int j = 0; j < locs.getLength(); j++) {
        // System.out.println("\t"+locs.item(j));
        // }
        // System.out.println();
        // }
        Element root = startMakingElement(name, namespace);
        // fix up schema location for the PSVI parser
        if (null != XMLProperties.TARGET_NAMESPACE && !XMLProperties.TARGET_NAMESPACE.isEmpty()) {
            Attribute schemaLoc = root.getAttribute("xsi:schemaLocation", "http://www.w3.org/2001/XMLSchema-instance");
            if (null != schemaLoc)
                root.removeAttribute(schemaLoc);
            root.addAttribute(new Attribute("xsi:schemaLocation", "http://www.w3.org/2001/XMLSchema-instance", XMLProperties.TARGET_NAMESPACE + ' ' + XMLProperties.SCHEMA_PATH));
        } else {
            Attribute schemaLoc = root.getAttribute("xsi:noNamespaceSchemaLocation", "http://www.w3.org/2001/XMLSchema-instance");
            if (null != schemaLoc)
                root.removeAttribute(schemaLoc);
            root.addAttribute(new Attribute("xsi:noNamespaceSchemaLocation", "http://www.w3.org/2001/XMLSchema-instance", XMLProperties.SCHEMA_PATH));
        }

        return root;
    }

    @Override
    public Nodes makeAttribute(String name, String URI, String value, Attribute.Type type) {
        return new Nodes(new AwareAttribute(name, URI, value, null, null));
    }

    @Override
    public Nodes makeText(String data) {
        // ignore whitespace-only text nodes
        if (data.trim().isEmpty())
            return new Nodes();
        return super.makeText(data);
    }

    @Override
    public Nodes makeComment(String data) {
        // ignore Comments
        return new Nodes();
    }

    @Override
    public Nodes makeDocType(String rootElementName, String publicID, String systemID) {
        // ignore DTDs
        return new Nodes();
    }

    @Override
    public void finishMakingDocument(Document document) {
        // make the namespace prefixes consistent with our namespace manager
        AwareElement root = (AwareElement) document.getRootElement();
        NamespaceManager nsm = NamespaceManager.getInstance();
        for (int i = 0; i < root.getNamespaceDeclarationCount(); i++) {
            String prefix = root.getNamespacePrefix(i);
            String uri = root.getNamespaceURI(prefix);
            if (prefix.isEmpty() && uri.isEmpty())
                continue;
            String newPrefix = nsm.getPrefix(uri);
            if (!newPrefix.equals(prefix)) {
                root.removeNamespaceDeclaration(prefix);
                root.addNamespaceDeclaration(newPrefix, uri);
                root.replaceNSPrefix(uri, newPrefix);
            }
        }
    }

    @Override
    public Nodes finishMakingElement(Element element) {
        if (XMLProperties.DEL_NS.equals(element.getNamespaceURI()))
            return new Nodes();
        return super.finishMakingElement(element);
    }

}
