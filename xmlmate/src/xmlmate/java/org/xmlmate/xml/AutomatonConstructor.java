package org.xmlmate.xml;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.BasicOperations;
import dk.brics.automaton.DatatypesAutomatonProvider;
import dk.brics.automaton.RegExp;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import regex.XSDRegBaseVisitor;
import regex.XSDRegParser.*;

import java.util.LinkedList;

/**
 * @author Nikolas
 *         <p/>
 *         This class visits an XSD regexp parse tree and constructs an
 *         Automaton that matches that regexp.
 */
public class AutomatonConstructor extends XSDRegBaseVisitor<Automaton> {
    private final DatatypesAutomatonProvider autoProvider;
    /**
     * used to pass an atom-automaton to its corresponding quantifier tree
     */
    private Automaton currentAtom;

    public AutomatonConstructor(DatatypesAutomatonProvider provider) {
        autoProvider = provider;
    }

    @Override
    protected boolean shouldVisitNextChild(RuleNode node, Automaton currentResult) {
        return node.getChildCount() > 0; // no need to visit terminal nodes
    }

    @Override
    protected Automaton defaultResult() {
        return Automaton.makeEmptyString();
    }

    @Override
    protected Automaton aggregateResult(Automaton aggregate, Automaton nextResult) {
        return aggregate.concatenate(nextResult);
    }

    @Override
    public Automaton visitRegExp(RegExpContext ctx) {
        LinkedList<Automaton> branches = new LinkedList<>();
        for (BranchContext b : ctx.branch())
            branches.add(visit(b));
        return BasicOperations.union(branches);
    }

    @Override
    public Automaton visitPiece(PieceContext ctx) {
        currentAtom = visit(ctx.atom());
        QuantifierContext quant = ctx.quantifier();
        return null == quant ? currentAtom : visit(ctx.quantifier());
    }

    @Override
    public Automaton visitNormCharAtom(NormCharAtomContext ctx) {
        return Automaton.makeChar(ctx.getText().charAt(0));
    }

    @Override
    public Automaton visitPrioOverrideAtom(PrioOverrideAtomContext ctx) {
        return visit(ctx.regExp());
    }

	/* Quantifiers */

    @Override
    public Automaton visitOptional(OptionalContext ctx) {
        assert null != currentAtom;
        return currentAtom.optional();
    }

    @Override
    public Automaton visitKleeneStar(KleeneStarContext ctx) {
        assert null != currentAtom;
        return currentAtom.repeat();
    }

    @Override
    public Automaton visitKleenePlus(KleenePlusContext ctx) {
        assert null != currentAtom;
        return currentAtom.repeat(1);
    }

    @Override
    public Automaton visitComplexQuantity(ComplexQuantityContext ctx) {
        return visit(ctx.quantity());
    }

    @Override
    public Automaton visitQuantMin(QuantMinContext ctx) {
        assert null != currentAtom;
        return currentAtom.repeat(Integer.parseInt(ctx.quantExact().getText()));
    }

    @Override
    public Automaton visitQuantRange(QuantRangeContext ctx) {
        assert null != currentAtom;
        int min = Integer.parseInt(ctx.quantExact(0).getText());
        int max = Integer.parseInt(ctx.quantExact(1).getText());
        return currentAtom.repeat(min, max);
    }

    @Override
    public Automaton visitQuantExact(QuantExactContext ctx) {
        assert null != currentAtom;
        int quant = Integer.parseInt(ctx.getText());
        return currentAtom.repeat(quant, quant);
    }

	/* Escapes */

    @Override
    public Automaton visitWildcard(WildcardContext ctx) {
        return autoProvider.getAutomaton("Char").minus(Automaton.makeCharSet("\r\n"));
    }

    @Override
    public Automaton visitIsCategory(IsCategoryContext ctx) {
        Automaton res = autoProvider.getAutomaton(ctx.getText());
        assert null != res;
        return res;
    }

    @Override
    public Automaton visitTab(TabContext ctx) {
        return Automaton.makeChar('\t');
    }

    @Override
    public Automaton visitRet(RetContext ctx) {
        return Automaton.makeChar('\r');
    }

    @Override
    public Automaton visitNewLn(NewLnContext ctx) {
        return Automaton.makeChar('\n');
    }

    @Override
    public Automaton visitSimpleCharEscape(SimpleCharEscapeContext ctx) {
        return Automaton.makeChar(ctx.getText().charAt(1));
    }

	/* Multichar escapes */

    @Override
    public Automaton visitSpace(SpaceContext ctx) {
        return autoProvider.getAutomaton("whitespacechar");
    }

    @Override
    public Automaton visitNotSpace(NotSpaceContext ctx) {
        return autoProvider.getAutomaton("Char").minus(autoProvider.getAutomaton("whitespacechar"));
    }

    private Automaton createInit() {
        return BasicOperations.union(autoProvider.getAutomaton("L"), Automaton.makeCharSet("_:"));
    }

    @Override
    public Automaton visitInit(InitContext ctx) {
        return createInit();
    }

    @Override
    public Automaton visitNonInit(NonInitContext ctx) {
        return autoProvider.getAutomaton("Char").minus(createInit());
    }

    @Override
    public Automaton visitNameChar(NameCharContext ctx) {
        return autoProvider.getAutomaton("NameChar");
    }

    @Override
    public Automaton visitNonNameChar(NonNameCharContext ctx) {
        return autoProvider.getAutomaton("Char").minus(autoProvider.getAutomaton("NameChar"));
    }

    @Override
    public Automaton visitMultiCharEscNumbers(MultiCharEscNumbersContext ctx) {
        // return autoProvider.getAutomaton("Nd");
        return Automaton.makeCharRange('0', '9');
    }

    @Override
    public Automaton visitNonMultiCharEscNumbers(NonMultiCharEscNumbersContext ctx) {
        return autoProvider.getAutomaton("Char").minus(autoProvider.getAutomaton("Nd"));
    }

    /*
     * this method is actually for "punctuation"
     */
    @Override
    public Automaton visitNonPrintable(NonPrintableContext ctx) {
        String dashes = new String(new char[]{
                // [Z]
                '\u2028', '\u2029',
                '\u0020', '\u00A0',
                '\u1680', '\u2000',
                '\u2001', '\u2002',
                '\u2003', '\u2004',
                '\u2005', '\u2006',
                '\u2007', '\u2008',
                '\u2009', '\u200A',
                '\u202F', '\u205F',
                '\u3000',
                // [Pd]
                '\u002D',
                '\u058A', '\u05BE', '\u1400', '\u1806',
                '\u2010', '\u2011', '\u2012', '\u2013',
                '\u2014', '\u2015', '\u2E17', '\u2E1A',
                '\u2E3A', '\u2E3B', '\u301C', '\u3030',
                '\u30A0', '\uFE31', '\uFE32', '\uFE58',
                '\uFE63', '\uFF0D', '\u005F',
                // [Pc]
                '\u203F', '\u2040',
                '\u2054', '\uFE33',
                '\uFE34', '\uFE4D',
                '\uFE4E', '\uFE4F',
                '\uFF3F',
                // [Pe]
                '\u0029', '\u005D',
                '\u007D', '\u0F3B',
                '\u0F3D', '\u169C',
                '\u2046', '\u207E',
                '\u208E', '\u2309',
                '\u230B', '\u232A',
                '\u2769', '\u276B',
                '\u276D', '\u276F',
                '\u2771', '\u2773',
                '\u2775', '\u27C6',
                '\u27E7', '\u27E9',
                '\u27EB', '\u27ED',
                '\u27EF', '\u2984',
                '\u2986', '\u2988',
                '\u298A', '\u298C',
                '\u298E', '\u2990',
                '\u2992', '\u2994',
                '\u2996', '\u2998',
                '\u29D9', '\u29DB',
                '\u29FD', '\u2E23',
                '\u2E25', '\u2E27',
                '\u2E29', '\u3009',
                '\u300B', '\u300D',
                '\u300F', '\u3011',
                '\u3015', '\u3017',
                '\u3019', '\u301B',
                '\u301E', '\u301F',
                '\uFD3F', '\uFE18',
                '\uFE36', '\uFE38',
                '\uFE3A', '\uFE3C',
                '\uFE3E', '\uFE40',
                '\uFE42', '\uFE44',
                '\uFE48', '\uFE5A',
                '\uFE5C', '\uFE5E',
                '\uFF09', '\uFF3D',
                '\uFF5D', '\uFF60',
                '\uFF63',
                // [Sm]

        });
        return Automaton.makeCharSet(dashes);
    }

    /*
     * this method is actually for "non-punctuation"
     */
    @Override
    public Automaton visitPrintable(PrintableContext ctx) {
        return new RegExp("[a-zA-Z0-9]").toAutomaton();
    }

	/* Category Escapes */

    @Override
    public Automaton visitCatEsc(CatEscContext ctx) {
        return visit(ctx.charProp());
    }

    @Override
    public Automaton visitComplEsc(ComplEscContext ctx) {
        return autoProvider.getAutomaton("Char").minus(visit(ctx.charProp()));
    }

	/* Ranges */

    @Override
    public Automaton visitCharGroup(CharGroupContext ctx) {
        Automaton minuend = visit(ctx.getChild(0));
        CharClassExprContext subtrahend = ctx.charClassExpr();
        if (null != subtrahend)
            minuend = minuend.minus(visit(subtrahend));
        return minuend;
    }

    @Override
    public Automaton visitNegCharGroup(NegCharGroupContext ctx) {
        return autoProvider.getAutomaton("Char").minus(visit(ctx.posCharGroup()));
    }

    @Override
    public Automaton visitPosCharGroup(PosCharGroupContext ctx) {
        LinkedList<Automaton> children = new LinkedList<>();
        for (ParseTree child : ctx.children)
            children.add(visit(child));
        return BasicOperations.union(children);
    }

    @Override
    public Automaton visitXmlCharIncDash(XmlCharIncDashContext ctx) {
        SingleCharEscContext esc = ctx.singleCharEsc();
        if (null != esc)
            return visit(esc);
        return Automaton.makeChar(ctx.getText().charAt(0));
    }

    @Override
    public Automaton visitXmlChar(XmlCharContext ctx) {
        return Automaton.makeChar(ctx.getText().charAt(0));
    }

    @Override
    public Automaton visitSeRange(SeRangeContext ctx) {
        Automaton start = visit(ctx.charOrEsc(0));
        Automaton end = visit(ctx.charOrEsc(1));
        // the resulting automata must be singletons
        assert null != start.getSingleton();
        // therefore it is easy to get their corresponding chars
        char startChar = start.getShortestExample(true).charAt(0);
        char endChar = end.getShortestExample(true).charAt(0);
        return Automaton.makeCharRange(startChar, endChar);
    }

}
