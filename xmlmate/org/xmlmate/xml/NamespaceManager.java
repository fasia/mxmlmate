package org.xmlmate.xml;

import javax.xml.XMLConstants;
import java.util.HashMap;
import java.util.Map;

public class NamespaceManager {
    private final Map<String, String> namespaces = new HashMap<>(); // make threadsafe?
    private int nsId = 0;

    private NamespaceManager() {
    }

    private static class NamespaceManagerHolder {
        private static final NamespaceManager instance = new NamespaceManager();
    }

    public static NamespaceManager getInstance() {
        return NamespaceManagerHolder.instance;
    }

    public String getPrefix(String namespace) {
        String prefix = namespaces.get(namespace);
        if (prefix == null) {
            if (namespace.equals(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI))
                prefix = "xsi";
            else if (namespace.equals(XMLConstants.W3C_XML_SCHEMA_NS_URI))
                prefix = "xsd";
            else if (namespace.equals(XMLConstants.XML_NS_URI))
                prefix = XMLConstants.XML_NS_PREFIX;
            else prefix = "ns" + nsId++;
            namespaces.put(namespace, prefix);
        }
        return prefix;
    }

    public String getQName(String localName, String namespaceURI) {
        if (null == namespaceURI || namespaceURI.trim().isEmpty()) return localName;
        String prefix = getPrefix(namespaceURI);
        return prefix + ':' + localName;
    }

    @Override
    public String toString() {
        return namespaces.toString();
    }
}
