package org.xmlmate.xml;

import nu.xom.Attribute;
import nu.xom.Node;
import org.apache.xerces.xs.XSAttributeDeclaration;
import org.apache.xerces.xs.XSAttributeUse;
import org.apache.xerces.xs.XSConstants;
import org.evosuite.utils.Randomness;

/**
 * This class represents an XML attribute that is aware of its declaration in the schema.
 */
public class AwareAttribute extends Attribute {
    private XSAttributeDeclaration decl = null;

    private XSAttributeUse use = null;
    private boolean isAny = false;

    AwareAttribute(String name, String URI, String value, XSAttributeUse use, XSAttributeDeclaration decl) {
        super(name, URI, value);
        this.use = use;
        this.decl = decl;
    }

    private AwareAttribute(AwareAttribute other) {
        super(other);
        decl = other.decl;
        use = other.use;
        isAny = other.isAny;
    }

    public static AwareAttribute anyAttr(String uri) {
        // XXX implement random value and name generation
        NamespaceManager nsManager = NamespaceManager.getInstance();
        String name = nsManager.getQName("anyAttr", uri);
        AwareAttribute at = new AwareAttribute(name, uri, "anyValue", null, null);
        at.isAny = true;
        return at;
    }

    public static AwareAttribute fromUse(XSAttributeUse use) {
        NamespaceManager nsManager = NamespaceManager.getInstance();
        XSAttributeDeclaration decl = use.getAttrDeclaration();
        String name = nsManager.getQName(decl.getName(), decl.getNamespace());
        String value = use.getConstraintValue();
        if (use.getConstraintType() != XSConstants.VC_FIXED)
            value = ValueGenerator.generateValue(decl.getTypeDefinition());
        return new AwareAttribute(name, decl.getNamespace(), value, use, decl);
    }

    public XSAttributeDeclaration getDecl() {
        return decl;
    }

    public void setDecl(XSAttributeDeclaration decl) {
        this.decl = decl;
    }

    public XSAttributeUse getUse() {
        return use;
    }

    public void setUse(XSAttributeUse use) {
        this.use = use;
    }

    public boolean isAny() {
        return isAny;
    }

    public boolean mutate() {
        if (isAny())
            return false; // XXX implement anyAttr mutation
        if (use.getConstraintType() == XSConstants.VC_FIXED)
            setValue(use.getConstraintValue());
        else if (Randomness.nextBoolean()) {
            setValue(ValueGenerator.generateValue(decl.getTypeDefinition()));
            return true;
        }
        return false;
    }

    @Override
    public Node copy() {
        return new AwareAttribute(this);
    }
}
