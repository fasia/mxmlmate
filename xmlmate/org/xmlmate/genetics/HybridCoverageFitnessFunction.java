package org.xmlmate.genetics;

import org.evosuite.coverage.exception.ExceptionCoverageSuiteFitness;
import org.evosuite.ga.FitnessFunction;

// XXX can be probably refactored in Scala as a mix-in for minimization functions
public class HybridCoverageFitnessFunction extends FitnessFunction<XMLTestSuiteChromosome> {

    private final SchemaCoverageFitnessFunction schemaCoverage;
    private final ExceptionCoverageSuiteFitness exceptionCoverage;

    public HybridCoverageFitnessFunction() {
        schemaCoverage = new SchemaCoverageFitnessFunction();
        exceptionCoverage = new ExceptionCoverageSuiteFitness();
    }

    @Override
    public double getFitness(XMLTestSuiteChromosome individual) {
        double fitness = (schemaCoverage.getFitness(individual) + exceptionCoverage.getFitness(individual)) / 2.0d;
        individual.setChanged(false);
        individual.setFitness(fitness);
        return fitness;
    }

    @Override
    public boolean isMaximizationFunction() {
        return false;
    }
}
