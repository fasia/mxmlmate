package org.xmlmate.execution;

import org.evosuite.Properties;
import org.evosuite.ga.MinimizeSizeSecondaryObjective;
import org.evosuite.ga.SteadyStateGA;
import org.evosuite.testsuite.MinimizeTotalLengthSecondaryObjective;
import org.xmlmate.genetics.BinaryBackendFitnessFunction;
import org.xmlmate.genetics.SingletonPopulationGA;
import org.xmlmate.genetics.XMLTestSuiteChromosome;
import org.xmlmate.genetics.XMLTestSuiteChromosomeFactory;

public class SingletonPopulationBackendUseCase extends BinaryBackendUseCase {

    public SingletonPopulationBackendUseCase(XMLTestSuiteChromosomeFactory factory, BinaryBackendFitnessFunction fitnessFunction) {
        super(factory, fitnessFunction);
        Properties.NUM_TESTS = Properties.MAX_SIZE / 2;
    }

    @Override
    protected void setupGAAdditions(SteadyStateGA<XMLTestSuiteChromosome> ga) {
        super.setupGAAdditions(ga);
        XMLTestSuiteChromosome.removeSecondaryObjective(new MinimizeSizeSecondaryObjective());
        XMLTestSuiteChromosome.removeSecondaryObjective(new MinimizeTotalLengthSecondaryObjective());
    }

    @Override
    protected SteadyStateGA<XMLTestSuiteChromosome> chooseGA(XMLTestSuiteChromosomeFactory fac) {
        return new SingletonPopulationGA(fac);
    }
}
