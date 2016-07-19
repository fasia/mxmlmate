package org.xmlmate.genetics;

import org.evosuite.ga.ChromosomeFactory;

public class XMLTestSuiteChromosomeFactory implements ChromosomeFactory<XMLTestSuiteChromosome> {

    private static final long serialVersionUID = -7082500792362638695L;
    private final ChromosomeFactory<XMLTestChromosome> testChromosomeFactory;

    public XMLTestSuiteChromosomeFactory(ChromosomeFactory<XMLTestChromosome> testChromosomeFactory) {
        this.testChromosomeFactory = testChromosomeFactory;
    }

    @Override
    public XMLTestSuiteChromosome getChromosome() {
        return new XMLTestSuiteChromosome(testChromosomeFactory);
    }
}
