package org.xmlmate.xml;

import dk.brics.automaton.*;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.xerces.xs.StringList;
import org.apache.xerces.xs.XSFacet;
import org.apache.xerces.xs.XSObjectList;
import org.apache.xerces.xs.XSSimpleTypeDefinition;
import org.evosuite.utils.Randomness;
import org.xmlmate.XMLProperties;

import regex.XSDRegLexer;
import regex.XSDRegParser;
import regex.XSDRegParser.RegExpContext;

import javax.xml.XMLConstants;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ValueGenerator {
    /** Stores used automata. */
    private static final ConcurrentHashMap<XSSimpleTypeDefinition, Automaton> cache = new ConcurrentHashMap<>();
    private static final DatatypesAutomatonProvider automatonProvider = new DatatypesAutomatonProvider()
    {
//        private final Automaton	charAutomaton	= new RegExp("[\t\n\r\u0020-\uD7FF\ue000-\ufffd]").toAutomaton();
        private final Automaton	charAutomaton	= new RegExp("[a-zA-Z0-9]").toAutomaton();
        private final Automaton hexBinary = new RegExp("([A-Fa-f0-9][A-Fa-f0-9])+").toAutomaton();
        
        @Override
        public Automaton getAutomaton(String name) {
            if ("Char".equalsIgnoreCase(name))
                return charAutomaton;
            if ("string".equalsIgnoreCase(name))
                return charAutomaton.repeat();
            if ("hexBinary".equalsIgnoreCase(name))
            	return hexBinary;
            return super.getAutomaton(name);
        }
    };
    private static Automaton rndString = Automaton.minimize(automatonProvider.getAutomaton("Char").repeat(XMLProperties.MIN_STRING_LENGTH, XMLProperties.MAX_STRING_LENGTH));
    private static Set<String> knownTypes = new HashSet<>(Arrays.asList(
            "NCName",
            // "QName",
            // "URI",
            "string",
            "boolean",
            "decimal",
            "float",
            "double", // not really known - use float instead
            "integer",
            "hexBinary",
            "base64Binary",
            "language",
            // these work unreliably
            "duration",
            "dateTime",
            "time",
            "date",
            "gYearMonth",
            "gYear",
            "gMonthDay",
            "gDay",
            // untested
            "NCNames",
            "Nmtokens",
            "Names"
    ));

    private ValueGenerator() {
    }

    private static Automaton applyDecimalNumericFacets(Automaton base, XSSimpleTypeDefinition simpleType) {
        String minInclusive = simpleType.getLexicalFacetValue(XSSimpleTypeDefinition.FACET_MININCLUSIVE);
        String minExclusive = simpleType.getLexicalFacetValue(XSSimpleTypeDefinition.FACET_MINEXCLUSIVE);
        String maxInclusive = simpleType.getLexicalFacetValue(XSSimpleTypeDefinition.FACET_MAXINCLUSIVE);
        String maxExclusive = simpleType.getLexicalFacetValue(XSSimpleTypeDefinition.FACET_MAXEXCLUSIVE);
        // TODO fix double generation range
        double min = -(Double.MAX_VALUE / 2d);
        double max = Double.MAX_VALUE / 2d;
        try {
            if (null != minInclusive)
                min = Double.parseDouble(minInclusive);
        } catch (NumberFormatException ignored) {
        }
        try {
            if (null != minExclusive)
                min = Double.parseDouble(minExclusive) + Double.MIN_VALUE;
        } catch (NumberFormatException ignored) {
        }
        try {
            if (null != maxInclusive)
                max = Double.parseDouble(maxInclusive);
        } catch (NumberFormatException ignored) {
        }
        try {
            if (null != maxExclusive)
                max = Double.parseDouble(maxExclusive) - Double.MIN_VALUE;
        } catch (NumberFormatException ignored) {
        }

        XSFacet facet_td = (XSFacet) simpleType.getFacet(XSSimpleTypeDefinition.FACET_TOTALDIGITS);
        if (null != facet_td) {
            int total = facet_td.getIntFacetValue();
            assert 0 < total;
            double lim = StrictMath.pow(10, total);
            if (!Double.isNaN(lim) && !Double.isInfinite(lim)) {
                min = Math.max(min, -lim);
                max = Math.min(max, lim);
            }
        }

        // idea: concatenate the integer-part automaton with fraction-part automaton
        Automaton facetAutomaton = createLimitAutomaton(Math.round(min), Math.round(max));

        XSFacet facet_fd = (XSFacet) simpleType.getFacet(XSSimpleTypeDefinition.FACET_FRACTIONDIGITS);
        if (null != facet_fd) {
            int fraction = facet_fd.getIntFacetValue();
            assert fraction >= 0;
            if (fraction > 0) {
                Automaton frac = Automaton.minimize(new RegExp("(\\.[0-9]{1," + fraction + "}0*)?").toAutomaton());
                facetAutomaton = facetAutomaton.concatenate(frac);
            }

        }

        if (null == base)
            return facetAutomaton;
        return base.intersection(facetAutomaton);
    }

    private static Automaton applyIntegerNumericFacets(Automaton base, XSSimpleTypeDefinition simpleType) {
        String minInclusive = simpleType.getLexicalFacetValue(XSSimpleTypeDefinition.FACET_MININCLUSIVE);
        String minExclusive = simpleType.getLexicalFacetValue(XSSimpleTypeDefinition.FACET_MINEXCLUSIVE);
        String maxInclusive = simpleType.getLexicalFacetValue(XSSimpleTypeDefinition.FACET_MAXINCLUSIVE);
        String maxExclusive = simpleType.getLexicalFacetValue(XSSimpleTypeDefinition.FACET_MAXEXCLUSIVE);
        long min = Long.MIN_VALUE;
        long max = Long.MAX_VALUE;
        try {
            if (minInclusive != null)
                min = Long.parseLong(minInclusive);
        } catch (NumberFormatException e) {
        }

        try {
            if (minExclusive != null)
                min = Math.max(Long.parseLong(minExclusive) + 1, min);
        } catch (NumberFormatException e) {
        }

        try {
            if (maxInclusive != null)
                max = Long.parseLong(maxInclusive);
        } catch (NumberFormatException e) {
        }

        try {
            if (maxExclusive != null)
                max = Math.min(Long.parseLong(maxExclusive) - 1, max);
        } catch (NumberFormatException e) {
        }

        Automaton facetAutomaton = createLimitAutomaton(min, max);

        XSFacet facet = (XSFacet) simpleType.getFacet(XSSimpleTypeDefinition.FACET_TOTALDIGITS);
        if (null != facet) {
            int total = facet.getIntFacetValue();
            assert total > 0;
            facetAutomaton = facetAutomaton.intersection(Automaton.makeTotalDigits(total));
        }

        if (null == base)
            return facetAutomaton;
        return base.intersection(facetAutomaton);
    }

    private static Automaton createLimitAutomaton(long min, long max) {
        String minS = Long.toString(Math.abs(min));
        String maxS = Long.toString(Math.abs(max));

        Automaton facetAutomaton = null;
        if (min < 0L && max < 0L)
            // - (makeMin(max) & makeMax(min))
            facetAutomaton = Automaton.makeChar('-').concatenate(BasicOperations.intersection(Automaton.makeMinInteger(maxS), Automaton.makeMaxInteger(minS)));
        else if (min < 0L && max > 0L)
            // (- makeMax(min)) | makeMax(max)
            facetAutomaton = Automaton.makeChar('-').concatenate(Automaton.makeMaxInteger(minS)).union(Automaton.makeMaxInteger(maxS));
        else
            // min and max must both be positive (a fourth case is illegal)
            // makeMin(min) & makeMax(max)
            facetAutomaton = Automaton.makeMinInteger(minS).intersection(Automaton.makeMaxInteger(maxS));

        assert null != facetAutomaton;
        return facetAutomaton;
    }

    private static Automaton applyNonNumericFacet(Automaton base, XSFacet facet) {
        int intValue = facet.getIntFacetValue();
        assert intValue > -1;
        switch (facet.getFacetKind()) {
            case XSSimpleTypeDefinition.FACET_LENGTH:
                return automatonProvider.getAutomaton("Char").repeat(intValue, intValue).intersection(base);
            case XSSimpleTypeDefinition.FACET_MINLENGTH:
                return automatonProvider.getAutomaton("Char").repeat(intValue).intersection(base); // FIXME bug when used with e.g. hexBinary
            case XSSimpleTypeDefinition.FACET_MAXLENGTH:
                return automatonProvider.getAutomaton("Char").repeat(0, intValue).intersection(base);
            case XSSimpleTypeDefinition.FACET_WHITESPACE:
                // TODO implement
            default:
                return base;
        }
    }

    private static Automaton constructPattern(String regex) {
        CharStream inp = new ANTLRInputStream(regex);
        XSDRegLexer lex = new XSDRegLexer(inp);
        TokenStream tok = new CommonTokenStream(lex);
        XSDRegParser parser = new XSDRegParser(tok);
        AutomatonConstructor ac = new AutomatonConstructor(automatonProvider);
        RegExpContext regExp = parser.regExp();
        return ac.visit(regExp);
    }

    private static String generate(Automaton a) {
        assert a.isDeterministic();
        StringBuilder str = new StringBuilder();
        State start = a.getInitialState();
        generate(str, start);
        return str.toString();
    }

    private static void generate(StringBuilder builder, State state) {
        Set<Transition> transitions = state.getTransitions();
        boolean accept = state.isAccept();
        if (transitions.isEmpty()) {
            assert accept : builder.toString();
            return;
        }

        int nroptions = transitions.size();
        if (accept && 100 < nroptions)
            return;

        if (accept)
            nroptions += 1; // add one option for stopping in this accepting state
        int option = Randomness.nextInt(nroptions); // generates in [0,nroptions)
        if (accept && 0 == option)
            return; // stop in this state
        if (accept)
            option -= 1; // deduct additional option

        Iterator<Transition> iterator = transitions.iterator();
        for (int i = 0; i < option; i++) {
            iterator.next();
        }
        Transition transition = iterator.next();

        int min = transition.getMin();
        int max = transition.getMax();
        int dif = max - min + 1;
        int res = min + Randomness.nextInt(dif);
        builder.append((char) res);
        generate(builder, transition.getDest());
    }

    private static int generateListLength(XSSimpleTypeDefinition simpleType) {
        assert simpleType.getVariety() == XSSimpleTypeDefinition.VARIETY_LIST;
        String facetValue = simpleType.getLexicalFacetValue(XSSimpleTypeDefinition.FACET_LENGTH);
        if (facetValue != null)
            return Integer.parseInt(facetValue);
        int minOccurs = 0;
        facetValue = simpleType.getLexicalFacetValue(XSSimpleTypeDefinition.FACET_MINLENGTH);
        if (facetValue != null)
            minOccurs = Integer.parseInt(facetValue);
        int maxOccurs = -1;
        facetValue = simpleType.getLexicalFacetValue(XSSimpleTypeDefinition.FACET_MAXLENGTH);
        if (facetValue != null)
            maxOccurs = Integer.parseInt(facetValue);
        if (maxOccurs == -1)
            maxOccurs = Math.max(minOccurs, XMLProperties.MAX_LIST_ITEMS_GENERATED);
        int min = Math.min(Math.max(minOccurs, XMLProperties.MIN_LIST_ITEMS_GENERATED), maxOccurs);
        int max = Math.max(Math.min(maxOccurs, XMLProperties.MAX_LIST_ITEMS_GENERATED), minOccurs);
        return min == max ? min : Randomness.nextInt(min, max + 1);
    }

    private static String chooseEnum(XSSimpleTypeDefinition simpleType) {
        StringList enums = simpleType.getLexicalEnumeration();
        assert !enums.isEmpty();
        int rand = Randomness.nextInt(enums.getLength());
        return enums.item(rand);
    }

    private static boolean isKnown(XSSimpleTypeDefinition simpleType) {
        return XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(simpleType.getNamespace()) && knownTypes.contains(simpleType.getName());
    }

    private static String getKnownSuperType(XSSimpleTypeDefinition simpleType) {
        XSSimpleTypeDefinition knownType = simpleType;
        while (!isKnown(knownType)) {
            knownType = (XSSimpleTypeDefinition) knownType.getBaseType();
            if (null == knownType || "anySimpleType".equals(knownType.getName()))
                return null;
        }
        return knownType.getName();
    }

    public static String generateValue(XSSimpleTypeDefinition simpleType) {
        // if there are enumerated values - take one of them
        if (simpleType.isDefinedFacet(XSSimpleTypeDefinition.FACET_ENUMERATION))
            return chooseEnum(simpleType);

        switch (simpleType.getVariety()) {
            case XSSimpleTypeDefinition.VARIETY_LIST: {
                int len = generateListLength(simpleType);
                XSSimpleTypeDefinition itemType = simpleType.getItemType();
                StringBuilder str = new StringBuilder();
                for (int i = 0; i < len; i++)
                    str.append(' ').append(generateValue(itemType).trim());
                return str.toString().trim();
            }
            case XSSimpleTypeDefinition.VARIETY_UNION: {
                if (simpleType.getMemberTypes().getLength() == 0)
                    throw new IllegalArgumentException("Empty unions not supported!");
                XSObjectList members = simpleType.getMemberTypes();
                int rand = Randomness.nextInt(members.getLength());
                return generateValue((XSSimpleTypeDefinition) members.item(rand));
            }
            default: { // atomic or absent variety
                String knownName = getKnownSuperType(simpleType);
                if ("double".equals(knownName))
                    knownName = "float";

                Automaton a = cache.get(simpleType);
                if (null == a) {
                    // try predefined types first
                    boolean known = isKnown(simpleType);

                    if (null != knownName) {
                        a = automatonProvider.getAutomaton(knownName);
                        assert null != a;
                    }

                    if (null == a || !known) { // manual processing needed
                        // process patterns
                        if (simpleType.isDefinedFacet(XSSimpleTypeDefinition.FACET_PATTERN)) {
                            StringList patterns = simpleType.getLexicalPattern();
                            assert !patterns.isEmpty();
                            // if no automaton found previously - start by accepting everything
                            if (null == a)
                                a = automatonProvider.getAutomaton("string");
                            for (int i = 0; i < patterns.getLength(); i++)
                                a = a.intersection(constructPattern(patterns.item(i)));
                        }
                        // process additional facets
                        if (simpleType.getNumeric())
                            if ("decimal".equals(knownName) || "float".equals(knownName))
                                a = applyDecimalNumericFacets(a, simpleType);
                            else
                                a = applyIntegerNumericFacets(a, simpleType);
                        else {
                            XSObjectList facets = simpleType.getFacets();
                            // if no automaton found previously - start by accepting everything
                            if (null == a)
                                a = automatonProvider.getAutomaton("string");
                            for (int i = 0; i < facets.getLength(); i++)
                                a = applyNonNumericFacet(a, (XSFacet) facets.get(i));
                        }
                    } // end manual processing
                    assert null != a;
                    cache.put(simpleType, a);
                } // end not found in cache
                return StringEscapeUtils.escapeXml(generate(a));
            } // end atomic or default
        } // end variety
    }

    public static String randomString() {
        return StringEscapeUtils.escapeXml(generate(rndString));
    }

    public static Automaton getCachedAutomaton(XSSimpleTypeDefinition simpleType) {
        return cache.get(simpleType);
    }
}
