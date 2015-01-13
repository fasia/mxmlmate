package org.xmlmate.execution;

import org.evosuite.ga.FitnessFunction;
import org.xmlmate.genetics.HybridCoverageFitnessFunction;
import org.xmlmate.genetics.XMLTestSuiteChromosomeFactory;

public class EvolveHybridCoverageUseCase extends EvolveBranchCoverageUseCase {

    public EvolveHybridCoverageUseCase(XMLTestSuiteChromosomeFactory factory) {
        super(factory);
    }

    @Override
    protected FitnessFunction chooseFitnessFunction() {
        return new HybridCoverageFitnessFunction();
    }
}
