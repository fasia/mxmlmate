package org.xmlmate.xml;

import com.google.common.collect.Lists;
import dk.brics.automaton.Automaton;
import dk.brics.automaton.State;
import dk.brics.automaton.Transition;
import nu.xom.Attribute;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Node;
import org.apache.xerces.xs.*;
import org.evosuite.utils.Randomness;

import java.util.*;

public class AwareDocument extends Document {
    private final Map<XSElementDeclaration, Set<AwareElement>> eleMap;

    public AwareDocument(AwareDocument other) {
        super(other);
        eleMap = new HashMap<>();
        ((AwareElement) getRootElement()).register(eleMap);
    }

    public AwareDocument(AwareElement root) {
        super(root);
        eleMap = new HashMap<>();
        root.register(eleMap);
    }

    private static Set<Transition> usedTransitions(XSSimpleTypeDefinition type, String value) {
        if (!type.isDefinedFacet(XSSimpleTypeDefinition.FACET_PATTERN))
            return Collections.emptySet();
        ValueGenerator.generateValue(type);// force the type into cache
        Automaton a = ValueGenerator.getCachedAutomaton(type);
        if (null == a)
            return Collections.emptySet();
        Set<Transition> transitions = new HashSet<>();
        State s = a.getInitialState();
        nextChar:
        for (char c : Lists.charactersOf(value)) {
            for (Transition t : s.getTransitions())
                if (t.getMin() <= c && c <= t.getMax()) {
                    transitions.add(t);
                    s = t.getDest();
                    continue nextChar;
                }
            // if we come off track, return at least a partial solution
            return transitions;
        }
        assert s.isAccept();
        return transitions;
    }

    public void resetEleMap() {
        eleMap.clear();
    }

    public Set<XSElementDeclaration> getElementDeclarations() {
        return eleMap.keySet();
    }

    public Set<XSAttributeDeclaration> getAttributeDeclarations() {
        HashSet<XSAttributeDeclaration> attrs = new HashSet<>();
        for (Set<AwareElement> elems : eleMap.values()) {
            if (null != elems)
                for (AwareElement e : elems) {
                    for (int i = 0; i < e.getAttributeCount(); i++) {
                        Attribute attribute = e.getAttribute(i);
                        if (attribute instanceof AwareAttribute)
                            attrs.add(((AwareAttribute) attribute).getDecl());
                    }
                }
        }
        return attrs;
    }

    public Set<Transition> getRegexTransitions() {
        Set<Transition> transitions = new HashSet<>();
        for (Set<AwareElement> elems : eleMap.values()) {
            if (null == elems)
                continue;
            for (AwareElement e : elems) {
                // process simply typed element
                XSElementDeclaration decl = e.getDecl();
                if (null == decl)
                    continue;
                XSTypeDefinition type = decl.getTypeDefinition();
                if (type.getTypeCategory() == XSTypeDefinition.SIMPLE_TYPE) {
                    transitions.addAll(usedTransitions((XSSimpleTypeDefinition) type, e.getValue()));
                } else {
                    XSComplexTypeDefinition ctype = (XSComplexTypeDefinition) type;
                    if (ctype.getContentType() == XSComplexTypeDefinition.CONTENTTYPE_SIMPLE)
                        transitions.addAll(usedTransitions(ctype.getSimpleType(), e.getValue()));
                    // process attributes
                    for (int i = 0; i < e.getAttributeCount(); i++) {
                        Attribute attribute = e.getAttribute(i);
                        if (attribute instanceof AwareAttribute) {
                            XSAttributeDeclaration attrDecl = ((AwareAttribute) attribute).getDecl();
                            if (null != attrDecl)
                                transitions.addAll(usedTransitions(attrDecl.getTypeDefinition(), attribute.getValue()));
                        }
                    }
                }
            }
        }
        return transitions;
    }

    private AwareElement chooseRandom() {
        XSElementDeclaration key = Randomness.choice(eleMap.keySet());
        assert null != key;
        Set<AwareElement> set = eleMap.get(key);
        assert null != set;
        set.remove(null);
        if (set.isEmpty()) {
            eleMap.remove(key); // lazy cleaning
            return chooseRandom();
        }
        return Randomness.choice(set);
    }

    public boolean mutate() {
        return chooseRandom().mutate();
    }

    public boolean smallNumericMutation() {
        boolean changed = false;
        while (true) {
            if (Randomness.nextDouble() > 0.65d) break;
            AwareElement element = chooseRandom(); // XXX might want to optimize the choice for this use case
            changed |= element.smallNumericMutation();
        }
        return changed;
    }

    @Override
    public Node copy() {
        return new AwareDocument(this);
    }

    /**
     * Prints the tree in a format that can be pasted
     * <a href="http://ironcreek.net/phpsyntaxtree/">here</a>.
     */
    public String phpPrint() {
        Element root = getRootElement();
        if (!(root instanceof AwareElement))
            return "unAware " + toXML();
        StringBuilder builder = new StringBuilder();
        ((AwareElement) root).phpPrint(builder);
        return builder.toString();
    }

    public Map<XSElementDeclaration, Set<AwareElement>> getEleMap() {
        return eleMap;
    }

}
