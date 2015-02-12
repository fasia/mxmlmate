package org.xmlmate.xml;

import nu.xom.*;

import org.apache.xerces.xs.*;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;

import javax.xml.XMLConstants;

import java.util.*;

/**
 * This class represents an XML element that is aware of its declaration in the schema.
 */
public class AwareElement extends Element {
    private static final Logger logger = LoggerFactory.getLogger("AwareElement");
    /**
     * probability to mutate an element rather than ascending further up the tree
     */
    static double ELEM_MUTATION_PROB = 0.4;
    /**
     * probability to mutate the root, thus regenerating the tree completely
     */
    static double ROOT_MUTATION_PROB = 0.01;
    /**
     * probability to mutate an attribute
     */
    static double ATTR_MUTATION_PROB = 0.15;
    static double ATTR_DELETION_PROB = 0.05;
    private XSElementDeclaration decl = null;
    // private final Attribute nil = null;
    private boolean isAny = false;

    public AwareElement(String name, String namespace, XSElementDeclaration decl) {
        super(name, namespace);
        this.decl = decl;
    }

    private AwareElement(AwareElement other) {
        super(other);
        decl = other.decl;
        isAny = other.isAny;
        // seems to be a kind of a workaround
        removeChildren();
        for (int i = 0; i < other.getChildCount(); i++) {
            Node child = other.getChild(i);
            appendChild(child.copy()); // and this is why I overrode copy() as well ;)
        }
    }

    public static AwareElement anyElem(String uri) {
        NamespaceManager nsm = NamespaceManager.getInstance();
        String name = nsm.getQName("anyElement", uri);
        AwareElement el = new AwareElement(name, uri, null);
        el.isAny = true;
        return el;
    }

    private static boolean allowsSubstitution(XSElementDeclaration elem) {
        return (elem.getDisallowedSubstitutions() & XSConstants.DERIVATION_SUBSTITUTION) != XSConstants.DERIVATION_SUBSTITUTION;
    }

    private static boolean deletable(AwareAttribute attr) {
        // XXX investigate if w3c namespaces are ok to delete
        return attr.isAny() || !attr.getUse().getRequired();
    }

    public XSElementDeclaration getDecl() {
        return decl;
    }

    public void setDecl(XSElementDeclaration decl) {
        this.decl = decl;
    }

    public boolean isAny() {
        return isAny;
    }

    private void replaceDecl(XSElementDeclaration newDecl) {
        if (decl == newDecl)
            return;
        // change the element inplace
        setLocalName(newDecl.getName());
        NamespaceManager nsm = NamespaceManager.getInstance();
        String newNamespace = newDecl.getNamespace();
        if (null!=newNamespace && !newNamespace.equals(getNamespaceURI())) {
        	String prefix = nsm.getPrefix(newNamespace);
        	setNamespacePrefix(prefix);
        	setNamespaceURI(newNamespace);	
        }
        // deregister from old set
        Map<XSElementDeclaration, Set<AwareElement>> eleMap = getEleMap();
        Set<AwareElement> kindred = eleMap.get(decl);
        kindred.remove(this);
        // replace current declaration
        decl = newDecl;
        // register in new set
        kindred = eleMap.get(decl);
        if (null == kindred) {
            kindred = new HashSet<>();
            eleMap.put(decl, kindred);
        }
        kindred.add(this);
    }

    private boolean substitute() {
        XSElementDeclaration head = decl.getSubstitutionGroupAffiliation();
        if (head == null)
            head = decl; // maybe decl is head itself
        XSObjectList substitutionGroup = XMLProperties.SCHEMA_INSTANCE.getSubstitutionGroup(head);
        if (substitutionGroup != null && !substitutionGroup.isEmpty()) {
            List<XSElementDeclaration> acceptableSubstitutions = new ArrayList<>();
            if (!head.getAbstract())
                acceptableSubstitutions.add(head);
            for (int i = 0; i < substitutionGroup.getLength(); i++) {
                XSElementDeclaration elem = (XSElementDeclaration) substitutionGroup.item(i);
                if (!elem.getAbstract())
                    acceptableSubstitutions.add(elem);
            }
            if (!acceptableSubstitutions.isEmpty()) {
                replaceDecl(Randomness.choice(acceptableSubstitutions)); // replace declaration
                return true;
            }
        }
        return false;
    }

    public boolean mutate() {
        return mutate(getDepth());
    }

    private boolean mutate(int currentDepth) {
        if (isAny())
            return false; // XXX implement anyElement mutation
        boolean changed = false;
        if (decl.getAbstract() || allowsSubstitution(decl) && Randomness.nextDouble() < ELEM_MUTATION_PROB)
            changed = substitute();
        XSTypeDefinition type = decl.getTypeDefinition();
        switch (type.getTypeCategory()) {
            case XSTypeDefinition.COMPLEX_TYPE: {
                XSComplexTypeDefinition complexType = (XSComplexTypeDefinition) type;
            /*Elements children = getChildElements();
			final int kidCount = children.size();

			// mutate some of its children first
			int[] indices = new int[kidCount];
			for (int i = 0; i < kidCount; i++)
				indices[i] = i;
			ArrayUtil.fisherYates(indices);
			for (int count = 1; Randomness.nextDouble() <= Math.pow(0.6, count) && count - 1 < kidCount; count++)
				changed |= ((AwareElement) children.get(indices[count - 1])).mutate(currentDepth + 1);

			if (changed) {
				mutateAttributes(complexType);
				return true;
			}*/
                //			if (0 == kidCount || 0 == currentDepth && Randomness.nextDouble() < ROOT_MUTATION_PROB || currentDepth > 0 && Randomness.nextDouble() < ELEM_MUTATION_PROB) {
                removeChildren(); // recursive descent here
                mutateComplex(complexType, currentDepth); // mutate this subtree
                changed = true; // we probably changed a lot ;)
                //			}
                changed |= mutateAttributes(complexType);
            }
            break;
            case XSTypeDefinition.SIMPLE_TYPE:
                changed |= mutateSimple((XSSimpleTypeDefinition) type);
                break;
            default:
                logger.warn("Unsupported kind of type!");
                break;
        }
        return changed;
    }

    private boolean mutateAttributes(XSComplexTypeDefinition complexType) {
        boolean changed = false;
        // mutate existing attributes
        List<XSAttributeUse> existingAttrs = new ArrayList<>(getAttributeCount());
        for (int i = 0; i < getAttributeCount(); i++) {
            Attribute a = getAttribute(i);
            if (!(a instanceof AwareAttribute))
                continue; // leave out special attributes like xsi:type // XXX will this also skip nil?
            AwareAttribute attr = (AwareAttribute) a;
            if (null == attr.getUse() || deletable(attr) && Randomness.nextDouble() < ATTR_DELETION_PROB) {
                attr.detach();
                attr = null; // help cg free resources
                changed = true; // definitely changed something ;)
                continue; // no need to mutate a detached attribute - skip to next
            }
            if (Randomness.nextDouble() < ATTR_MUTATION_PROB)
                changed |= attr.mutate();

            existingAttrs.add(attr.getUse()); // should we care about adding nulls from anyAttrs?
        }
        // append optional / restore required attributes
        XSObjectList attrs = complexType.getAttributeUses();
        for (int i = 0; i < attrs.getLength(); i++) {
            XSAttributeUse use = (XSAttributeUse) attrs.item(i);
            if (!existingAttrs.contains(use) && (use.getRequired() || Randomness.nextBoolean())) {
                AwareAttribute newAttr = AwareAttribute.fromUse(use);
                addAttribute(newAttr);
                existingAttrs.add(use);
                changed |= true;
            }
        }
        return changed;
    }

    private List<XSComplexTypeDefinition> getConcreteSubTypes(XSComplexTypeDefinition complexType) {
        List<XSComplexTypeDefinition> subTypes = new ArrayList<>();
        XSNamedMap complexTypes = XMLProperties.SCHEMA_INSTANCE.getComponents(XSTypeDefinition.COMPLEX_TYPE);
        short anyDerivation = XSConstants.DERIVATION_EXTENSION | XSConstants.DERIVATION_LIST | XSConstants.DERIVATION_RESTRICTION
                | XSConstants.DERIVATION_SUBSTITUTION | XSConstants.DERIVATION_UNION;
        for (int i = 0; i < complexTypes.getLength(); i++) {
            XSComplexTypeDefinition type = (XSComplexTypeDefinition) complexTypes.item(i);
            if (!type.getAbstract() && type.derivedFromType(complexType, anyDerivation))
                subTypes.add(type);
        }
        return subTypes;
    }

    private void mutateComplex(XSComplexTypeDefinition complexType, int currentDepth) {
        mutateComplex(complexType, null, currentDepth);
    }

    private void mutateComplex(XSComplexTypeDefinition complexType, XSParticle particle, int currentDepth) {
        // XXX process nillable
        // if type is abstract - change to a subtype
        if (complexType.getAbstract()) {
            logger.debug("Mutating abstract complex type {}\t at deph: {}", complexType.getName(), currentDepth);
            List<XSComplexTypeDefinition> subTypes = getConcreteSubTypes(complexType);
            XSComplexTypeDefinition subType = Randomness.choice(subTypes);
            NamespaceManager nsm = NamespaceManager.getInstance();
            String name = nsm.getQName("type", XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI);
            String value = nsm.getQName(subType.getName(), subType.getNamespace());
            Attribute at = new Attribute(name, XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, value);
            logger.trace("It's abstract, so replacing it with {}", subType.getName());
            mutateComplex(subType, currentDepth); // no increase in depth here
            addAttribute(at);
            return;
        }
        if (particle == null)
            particle = complexType.getParticle();
        if (particle != null) {
            if (complexType.getContentType() == XSComplexTypeDefinition.CONTENTTYPE_MIXED)
                appendChild(ValueGenerator.randomString());
            int min = particle.getMinOccurs();
            if (min == 0 && currentDepth >= XMLProperties.OPTIONAL_DEPTH) {
                logger.trace("Optional depth exceeded!");
                // TODO cull existing (optional) elements?
                // System.out.print(getLocalName()+" exceeded depth "+Properties.XML_OPTIONAL_DEPTH);
                // Nodes rm = removeChildren();
                // System.out.println(" culled "+rm.size()+" children!");
                return; // optional depth exceeded
            }
            int max = particle.getMaxOccurs();
            if (particle.getMaxOccursUnbounded())
                max = Math.max(XMLProperties.MAX_ELEMENTS_GENERATED, min);
            int genOccurs = Randomness.nextInt(min, max + 1);
            XSTerm term = particle.getTerm();
            if (term instanceof XSModelGroup) {
                XSModelGroup group = (XSModelGroup) term;
                XSObjectList particles = group.getParticles();
                switch (group.getCompositor()) {
                    case XSModelGroup.COMPOSITOR_SEQUENCE:
                        logger.trace("Will generate {} sequences!", genOccurs);
                        for (int i = 0; i < genOccurs; i++)
                            for (int j = 0; j < particles.getLength(); j++) {
                                XSParticle part = (XSParticle) particles.item(j);
                                mutateComplex(complexType, part, currentDepth + 1);
                            }
                        break;
                    case XSModelGroup.COMPOSITOR_CHOICE:
                        logger.trace("Will generate {} choices!", genOccurs);
                        for (int i = 0; i < genOccurs; i++) {
                            int rand = Randomness.nextInt(particles.getLength());
                            XSParticle randomParticle = (XSParticle) particles.item(rand);
                            mutateComplex(complexType, randomParticle, currentDepth + 1);
                        }
                        break;
                    case XSModelGroup.COMPOSITOR_ALL: {
                        logger.trace("Will generate {} alls!", genOccurs);
                        List<XSParticle> mutableList = new ArrayList<>(particles.getLength());
                        for (int i = 0; i < particles.getLength(); i++)
                            mutableList.add((XSParticle) particles.item(i));
                        Randomness.shuffle(mutableList);
                        for (int i = 0; i < genOccurs; i++)
                            for (XSParticle xsParticle : mutableList)
                                if (xsParticle.getMinOccurs() == 1 || Randomness.nextBoolean())
                                    mutateComplex(complexType, xsParticle, currentDepth + 1);
                    }
                    break;
                }
            } else if (term instanceof XSElementDeclaration) {
                XSElementDeclaration elem = (XSElementDeclaration) term;
                NamespaceManager nsm = NamespaceManager.getInstance();
                String name = nsm.getQName(elem.getName(), elem.getNamespace());
                Map<XSElementDeclaration, Set<AwareElement>> eleMap = getEleMap();
                logger.trace("Will generate {} children of type {}", genOccurs, name);
                for (int i = 0; i < genOccurs; i++) {
                    AwareElement newChild = new AwareElement(name, elem.getNamespace(), elem);
                    appendChild(newChild);
                    newChild.register(eleMap);
                    newChild.mutate(currentDepth);
                }
            } else { // term must be a wildcard
                XSWildcard wild = (XSWildcard) term;
                // StringList nspaces = wild.getNsConstraintList();
                switch (wild.getConstraintType()) {
                    case XSWildcard.NSCONSTRAINT_LIST:
                    case XSWildcard.NSCONSTRAINT_NOT:
                        break; // XXX implement <any> for a given namespace
                    default: /* XSWildcard.NSCONSTRAINT_ANY: */
                        AwareElement anyElement = anyElem("http://bogus.org");
                        appendChild(anyElement);
                        break;
                }
            }
            // particle is null meaning that this isn't a mixed or element-only element.
        } else if (complexType.getContentType() == XSComplexTypeDefinition.CONTENTTYPE_SIMPLE)
            mutateSimple(complexType.getSimpleType());
    }

    /*
    private boolean nil() {
        if (!decl.getNillable()) return false;
        if (nil==null) {
            NamespaceManager nsm = NamespaceManager.getInstance();
            String nilName = nsm.getQName("nil", XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI);
            nil = new Attribute(nilName, "true");
            addAttribute(nil);
        }
        return true;
    }

    private void unnil() {
        if (nil!=null) {
            removeAttribute(nil);
            nil = null;
        }
    }
     */
    private boolean mutateSimple(XSSimpleTypeDefinition simpleType) {
        // XXX process nillable
        logger.trace("Mutating simple type {}", simpleType.getName());
        if (decl.getConstraintType() != XSConstants.VC_FIXED) {
            removeChildren();
            appendChild(ValueGenerator.generateValue(simpleType));
            return true;
        } else if (0 == getChildCount()) {
            appendChild(decl.getValueConstraintValue().getNormalizedValue());
            return true;
        } else
            return false;
    }

    /**
     * registers this element and all of its children in the given element map
     */
    public void register(Map<XSElementDeclaration, Set<AwareElement>> eleMap) {
        if (eleMap == null)
            logger.error("Tried to register an element with no document {}", this); // and then a NPE will be thrown
        @SuppressWarnings("null")
        Set<AwareElement> relatives = eleMap.get(decl);
        if (relatives == null) {
            relatives = new HashSet<>();
            eleMap.put(decl, relatives);
        }
        relatives.add(this);
        Elements children = getChildElements();
        for (int i = 0; i < children.size(); i++)
            ((AwareElement) children.get(i)).register(eleMap);
        // System.out.println("Registered "+getLocalName()+" for "+decl);
    }

    /**
     * removes this element and all of its children from the element map
     */
    void deregister(Map<XSElementDeclaration, Set<AwareElement>> eleMap) {
        if (eleMap == null)
            return;
        Set<AwareElement> relatives = eleMap.get(decl);
        if (relatives == null) {
            logger.debug("Tried to deregister from nonexistant eleMap {}", this);
            return;
        }
        relatives.remove(this);
        // let this be handled by the lazy cleaning on crossover
        // if (relatives.isEmpty())
        // eleMap.remove(decl);
        Elements children = getChildElements();
        for (int i = 0; i < children.size(); i++)
            ((AwareElement) children.get(i)).deregister(eleMap);
    }

    @Override
    public Node copy() {
        return new AwareElement(this);
    }

    @Override
    public Nodes removeChildren() {
        Map<XSElementDeclaration, Set<AwareElement>> eleMap = getEleMap();
        deregister(eleMap); // recursively deregister all children
        Nodes nodes = super.removeChildren();
        if (null != eleMap)
            register(eleMap); // reregister self
        return nodes;
    }

    @Override
    public void detach() {
        deregister(getEleMap());
        super.detach();
    }

    @Override
    public Node removeChild(int position) {
        Node child = getChild(position);
        if (child instanceof AwareElement)
            ((AwareElement) child).deregister(getEleMap());
        return super.removeChild(position);
    }

    @Override
    public String toString() {
        return getNamespacePrefix() + ':' + getLocalName();
    }

    /**
     * Prints the tree in a format that can be pasted
     * <a href="http://ironcreek.net/phpsyntaxtree/">here</a>.
     */
    public void phpPrint(StringBuilder builder) {
        builder.append('[');
        builder.append(getLocalName());
        for (int i = 0; i < getChildCount(); i++) {
            builder.append(' ');
            Node child = getChild(i);
            if (child instanceof AwareElement)
                ((AwareElement) child).phpPrint(builder);
        }
        builder.append(']');
    }

    public Map<XSElementDeclaration, Set<AwareElement>> getEleMap() {
        if (getDocument() == null)
            return null;
        return ((AwareDocument) getDocument()).getEleMap();
    }

    /**
     * Replaces all the prefixes of namespace in this subtree with prefix.
     */
    public void replaceNSPrefix(String namespace, String prefix) {
        if (getNamespaceURI().equals(namespace))
            setNamespacePrefix(prefix);
        for (int i = 0; i < getAttributeCount(); i++) {
            Attribute attribute = getAttribute(i);
            if (attribute.getNamespaceURI().equals(namespace))
                attribute.setNamespace(prefix, namespace);
        }
        Elements childElements = getChildElements();
        for (int i = 0; i < childElements.size(); i++)
            ((AwareElement) childElements.get(i)).replaceNSPrefix(namespace, prefix);
    }

    public boolean isRoot() {
        return this == getDocument().getRootElement();
    }

    private int getDepth() {
        int depth = 0;
        ParentNode p = this;
        while (p instanceof Element) {
            depth += 1;
            p = p.getParent();
        }
        return depth;
    }
}
