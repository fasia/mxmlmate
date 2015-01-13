package org.xmlmate.genetics;

import org.evosuite.ga.ChromosomeFactory;

public class XMLTestChromosomeFactory implements ChromosomeFactory<XMLTestChromosome> {
    private static final long serialVersionUID = -928455033961047707L;
    private String root = null;

    public XMLTestChromosomeFactory(String rootElement) {
        root = rootElement;
    }

    @Override
    public XMLTestChromosome getChromosome() {
        if (root != null)
            return new XMLTestChromosome(root);
        return new XMLTestChromosome();
    }
}
