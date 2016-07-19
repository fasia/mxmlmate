package org.xmlmate.genetics;

import org.evosuite.ga.FitnessFunction;

public class SchemaCoverageFitnessFunction extends FitnessFunction<XMLTestSuiteChromosome> {

    @Override
    public double getFitness(XMLTestSuiteChromosome individual) {
        double fitness = 1.0d - (individual.getSchemaElementCoverage() + individual.getSchemaAttributeCoverage() + individual.getSchemaRegexCoverage()) / 3.0d;
        individual.setChanged(false);
        individual.setFitness(fitness);
        return fitness;
    }

    @Override
    public boolean isMaximizationFunction() {
        return false;
    }
}
