package org.xmlmate.genetics;

import com.google.common.collect.Sets;
import nu.xom.Node;
import nu.xom.ParentNode;
import org.apache.xerces.xs.*;
import org.evosuite.Properties;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.ConstructionFailedException;
import org.evosuite.ga.CrossOverFunction;
import org.evosuite.utils.Randomness;
import org.xmlmate.XMLProperties;
import org.xmlmate.xml.AwareElement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class XMLCrossOverFunction extends CrossOverFunction {
    private static final long serialVersionUID = -3420371603988147237L;

    private static boolean ancestorOf(ParentNode n, ParentNode anc) {
        assert null != n;
        while (null != (n = n.getParent()))
            if (anc == n)
                return true;
        return false;
    }

    /**
     * Swaps the two given elements if neither of them is root
     *
     * @return whether a swap has taken place
     */
    private static boolean swap(AwareElement el1, AwareElement el2) {
        if (el1.isRoot() || el2.isRoot())
            return false;
        logger.debug("Swapping {} <-> {}", el1, el2);
        AwareElement p1 = (AwareElement) el1.getParent();
        AwareElement p2 = (AwareElement) el2.getParent();

        if (ancestorOf(el2, p1) || ancestorOf(el1, p2))
            return false;

        int idx1 = p1.indexOf(el1);
        int idx2 = p2.indexOf(el2);
        el1.detach();
        el2.detach();

        if (0 == p1.getChildCount())
            p1.appendChild(el2);
        else
            p1.insertChild(el2, idx1);
        el2.register(p1.getEleMap());

        if (0 == p2.getChildCount())
            p2.appendChild(el1);
        else
            p2.insertChild(el1, idx2);
        el1.register(p2.getEleMap());

        return true;
    }

    /**
     * Swaps ~prob of between the given sets.<br/>
     * Never swapps back.
     *
     * @param prob swap probability. must be between 0 and 1.
     * @return the number of swaps performed
     */
    private static int swapSeries(ArrayList<AwareElement> elements1, ArrayList<AwareElement> partners, double prob) {
        assert 0d < prob && prob < 1d;
        // save local copies to be able to modify
        int initialSize = partners.size();
        for (AwareElement el : elements1) {
            if (partners.isEmpty())
                break;
            if (Randomness.nextDouble() < prob) { // swap prob of elements belonging to decl
                AwareElement partner = Randomness.choice(partners);
                swap(el, partner);
                partners.remove(partner);
            }
        }
        return initialSize - partners.size();
    }

    // XXX implement attribute-only crossover as an experiment
    // TODO implement substitution group crossover

    /**
     * Approx. prob elements will be either swapped, or transplanted from left to right.
     *
     * @param from            the set of potential donors
     * @param recipientParent the parent node for the recipient side
     * @param prob            the probability of change. must be between 0 and 1.
     * @param minFrom         minimum valid number of elements on the donor's side
     * @param maxTo           maximum valid number of elements on the recipient's side
     * @return if any changes have been performed
     */
    private static boolean swapOrTransplantSeries(HashSet<AwareElement> from, AwareElement recipientParent, double prob, int minFrom, int maxTo) {
        assert 0d < prob && prob < 1d;
        boolean changed = false;
        ArrayList<AwareElement> partners = new ArrayList<>(recipientParent.getChildCount());
        for (int i = 0; i < recipientParent.getChildCount(); i++) {
            Node child = recipientParent.getChild(i);
            if (child instanceof AwareElement)
                partners.add((AwareElement) child);
        }
        int leftSize = from.size(), rightSize = partners.size();
        for (AwareElement donor : from) {// iterate over left side
            if (0 == leftSize || partners.isEmpty())
                break; // nothing left to transplant, also cannot swap
            if (donor.isRoot())
                continue; // cannot crossover root

            if (Randomness.nextDouble() < prob) { // decide if we want to touch this
                if (0 == rightSize) { // can only transplant now
                    donor.detach();
                    recipientParent.appendChild(donor);
                    donor.register(recipientParent.getEleMap());
                    leftSize -= 1;
                    rightSize += 1;
                    changed = true;
                } else if (leftSize <= minFrom || rightSize >= maxTo) { // can only swap now
                    AwareElement partner = Randomness.choice(partners);
                    if (swap(donor, partner)) {
                        partners.remove(partner);
                        changed = true;
                    }
                } else { // can swap and transplant
                    if (Randomness.nextBoolean()) { // this could be refactored into the above if, but it's clearer this way
                        // swap
                        AwareElement partner = Randomness.choice(partners);
                        if (swap(donor, partner)) {
                            partners.remove(partner);
                            changed = true;
                        }
                    } else {
                        // transplant
                        donor.detach();
                        recipientParent.appendChild(donor);
                        donor.register(recipientParent.getEleMap());
                        leftSize -= 1;
                        rightSize += 1;
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private void crossOverSuites(XMLTestSuiteChromosome s1, XMLTestSuiteChromosome s2) {
        logger.debug("Crossing over two test suites!");

        ArrayList<XMLTestChromosome> partners = new ArrayList<>(s2.getTestChromosomes());

        for (XMLTestChromosome chrom1 : new ArrayList<>(s1.getTestChromosomes())) {

            XMLTestChromosome partner = Randomness.choice(partners);
            if (null == partner || Randomness.nextDouble() < 0.35d)
                continue;

            if (s1.size() > 1 && s2.size() < Properties.MAX_SIZE && Randomness.nextDouble() < 0.15d) {
                s2.deleteTest(partner);
                s1.addTest(partner);
                partners.remove(partner);
                continue;
            }

            if (Randomness.nextDouble() < 0.25d) {
                s1.deleteTest(chrom1);
                s2.deleteTest(partner);
                s2.addTest(chrom1);
                s1.addTest(partner);
                partners.remove(partner);
                continue;
            }

            crossOverXMLs(chrom1, partner, new HashSet<XSElementDeclaration>());
            s1.setChanged(true);
            s2.setChanged(true);
        }

    }

    public void crossOverXMLs(XMLTestChromosome x1, XMLTestChromosome x2, Set<XSElementDeclaration> visited) {
        // if (visited.isEmpty()) logger.debug("Crossing over \n" + x1 + "\n" + x2); // commented out because inefficient
        Map<XSElementDeclaration, Set<AwareElement>> eleMap1 = x1.getEleMap();
        Map<XSElementDeclaration, Set<AwareElement>> eleMap2 = x2.getEleMap();

        Sets.SetView<XSElementDeclaration> common = Sets.intersection(eleMap1.keySet(), eleMap2.keySet());
        Sets.SetView<XSElementDeclaration> agenda = Sets.difference(common, visited);
        if (agenda.isEmpty()) {
            logger.debug("Resulting crossover:\n{}\n{}", x1, x2);
            return; // we have considered all declarations
        }

        // choose next element declaration to be considered
        XSElementDeclaration decl = Randomness.choice(agenda);

        // these are no longer needed
        common = null;
        agenda = null;
        // record the current declaration as visited
        visited.add(decl);

        // skip if no declaration is available (e.g. anyElement).
        if (null == decl) {
            crossOverXMLs(x1, x2, visited);
            return;
        }

        ArrayList<AwareElement> elements1, elements2;
        try {
            elements1 = new ArrayList<AwareElement>(eleMap1.get(decl));
            elements2 = new ArrayList<AwareElement>(eleMap2.get(decl));
        } catch (NullPointerException e) {
            crossOverXMLs(x1, x2, visited);
            return;
        }
        // skip this declaration if no elements exist
        if (elements1.isEmpty()) {
            eleMap1.remove(decl); // lazy cleaning
            crossOverXMLs(x1, x2, visited);
            return;
        }
        if (elements2.isEmpty()) {
            eleMap2.remove(decl); // lazy cleaning
            crossOverXMLs(x1, x2, visited);
            return;
        }

        // 25% chance to skip over this declaration even if it's ok
        if (Randomness.nextDouble() < 0.25d) {
            crossOverXMLs(x1, x2, visited);
            return;
        }

        XSTypeDefinition type = decl.getTypeDefinition();
        switch (type.getTypeCategory()) {
            case XSTypeDefinition.COMPLEX_TYPE:
                XSParticle part = ((XSComplexTypeDefinition) type).getParticle();
                // if there is a particle and we are 85% lucky, we will swap some children
                if (null != part && Randomness.nextDouble() < 0.85D) {
                    // otherwise see if it's possible to swap children
                    XSTerm term = part.getTerm();
                    if (term instanceof XSModelGroup) {
                        XSModelGroup mgroup = (XSModelGroup) term;
                        // choose elements whose children might get swapped
                        AwareElement swapRoot1 = Randomness.choice(elements1), swapRoot2 = Randomness.choice(elements2);
                        switch (mgroup.getCompositor()) {
                            case XSModelGroup.COMPOSITOR_SEQUENCE:
                                // TODO implement the subtle differences in handling of SEQUENCE
                                break;
                            case XSModelGroup.COMPOSITOR_ALL:
                                // it's the same for choice and all for now...
                                break;
                            case XSModelGroup.COMPOSITOR_CHOICE: {
                                // we can freely swap any children of choice elements
                                HashSet<AwareElement> choices1 = new HashSet<>();
                                for (int i = 0; i < swapRoot1.getChildCount(); i++) {
                                    Node child = swapRoot1.getChild(i);
                                    if (child instanceof AwareElement)
                                        choices1.add((AwareElement) child);
                                }
                                int maxTo = part.getMaxOccursUnbounded() ? XMLProperties.MAX_ELEMENTS_GENERATED : part.getMaxOccurs();
                                if (swapOrTransplantSeries(choices1, swapRoot2, 0.25d, part.getMinOccurs(), maxTo)) {
                                    x1.setChanged(true);
                                    x2.setChanged(true);
                                }
                                break;
                            }
                            default:
                                throw new IllegalStateException("Element declaration " + decl.getName() + " has illegal model group compositor!");
                        }
                    }
                    // the term is not a model group, so we can still fall through to swapping
                } // if there is no particle or in 15% of all cases, simply swap a few elements
                //$FALL-THROUGH$
            case XSTypeDefinition.SIMPLE_TYPE:
                // maybe just swap equal elements, especially if it's a simple type
                if (swapSeries(elements1, elements2, 0.15d) > 0) { // swap 15%
                    x1.setChanged(true);
                    x2.setChanged(true);
                }
                break;
            default:
                throw new IllegalStateException("Element declaration " + decl.getName() + " has illegal type category!");
        } // end type switch

        crossOverXMLs(x1, x2, visited); // call recursively until all declarations have been considered
    } // end of crossOverXMLs

    @Override
    public void crossOver(Chromosome parent1, Chromosome parent2) throws ConstructionFailedException {
        if (parent1 == parent2) return;
        assert parent1 instanceof XMLTestSuiteChromosome && parent2 instanceof XMLTestSuiteChromosome : "Tried to crossover "
            + parent1.getClass().getName() + " and " + parent2.getClass().getName();
        crossOverSuites((XMLTestSuiteChromosome) parent1, (XMLTestSuiteChromosome) parent2);
    }
}
