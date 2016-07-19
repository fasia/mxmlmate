package org.xmlmate.execution;

import org.evosuite.ga.*;
import org.xmlmate.genetics.BinaryBackendFitnessFunction;
import org.xmlmate.genetics.XMLTestSuiteChromosome;
import org.xmlmate.genetics.XMLTestSuiteChromosomeFactory;
import org.xmlmate.monitoring.EventRecounter;

public class BinaryBackendUseCase extends EvolveBranchCoverageUseCase {

    private BinaryBackendFitnessFunction fitnessFunction;

    public BinaryBackendUseCase(XMLTestSuiteChromosomeFactory factory, BinaryBackendFitnessFunction fitnessFunction) {
        super(factory);
        this.fitnessFunction = fitnessFunction;
    }

    @Override
    public void initialize() {
        fitnessFunction.startAndReadyWorkers();
    }

    @Override
    protected void setupGAAdditions(SteadyStateGA<XMLTestSuiteChromosome> ga) {
        // selection functions
        SelectionFunction<XMLTestSuiteChromosome> selectionFunction = new TournamentSelection<>();
        selectionFunction.setMaximize(fitnessFunction.isMaximizationFunction());
        ga.setSelectionFunction(selectionFunction);
        XMLTestSuiteChromosome.selectionFunction.setMaximize(fitnessFunction.isMaximizationFunction());
        // replacement function
        ga.setReplacementFunction(new FitnessReplacementFunction(fitnessFunction.isMaximizationFunction()));
        // monitoring
        ga.addListener(new EventRecounter());
    }

    @Override
    protected FitnessFunction chooseFitnessFunction() {
        return fitnessFunction;
    }

    @Override
    protected void freeResources() {
        fitnessFunction.freeSockets();
    }

}
