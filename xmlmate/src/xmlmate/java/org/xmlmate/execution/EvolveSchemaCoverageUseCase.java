package org.xmlmate.execution;

import org.evosuite.ga.*;
import org.evosuite.ga.stoppingconditions.MaxTimeStoppingCondition;
import org.evosuite.ga.stoppingconditions.StoppingCondition;
import org.evosuite.ga.stoppingconditions.ZeroFitnessStoppingCondition;
import org.evosuite.testsuite.MinimizeTotalLengthSecondaryObjective;
import org.evosuite.testsuite.RelativeSuiteLengthBloatControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;
import org.xmlmate.genetics.*;
import org.xmlmate.monitoring.EventRecounter;
import org.xmlmate.monitoring.SchemaCoverageMonitor;

public class EvolveSchemaCoverageUseCase implements UseCase {
    private static final Logger logger = LoggerFactory.getLogger(EvolveSchemaCoverageUseCase.class);
    private final XMLTestSuiteChromosomeFactory factory;

    public EvolveSchemaCoverageUseCase(XMLTestSuiteChromosomeFactory factory) {
        this.factory = factory;
    }

    @Override
    public void run() {
        GeneticAlgorithm<XMLTestSuiteChromosome> ga = new SteadyStateGA<>(factory);

        // stopping conditions
        ga.setStoppingCondition(new ZeroFitnessStoppingCondition()); // this resets default stopping conditions
        StoppingCondition stoppingCondition = new MaxTimeStoppingCondition();
        stoppingCondition.setLimit(XMLProperties.GLOBAL_TIMEOUT);
        ga.addStoppingCondition(stoppingCondition);

        // fitness function
        FitnessFunction<XMLTestSuiteChromosome> fitnessFunction = new SchemaCoverageFitnessFunction();
        ga.setFitnessFunction(fitnessFunction);

        //secondary objectives
        XMLTestSuiteChromosome.addSecondaryObjective(new MinimizeTotalLengthSecondaryObjective());
        XMLTestSuiteChromosome.addSecondaryObjective(new MinimizeSizeSecondaryObjective());

        XMLTestChromosome.addSecondaryObjective(new MinimizeSizeSecondaryObjective());

        // crossover function
        CrossOverFunction crossoverFunction = new XMLCrossOverFunction();
        ga.setCrossOverFunction(crossoverFunction);

        // selection function
        SelectionFunction<XMLTestSuiteChromosome> selectionFunction = new RankSelection<>();
        selectionFunction.setMaximize(false);
        ga.setSelectionFunction(selectionFunction);

        // bloat control
        ga.addBloatControl(new RelativeSuiteLengthBloatControl());
        ga.addBloatControl(new MaxSizeBloatControl());

        // progress monitor
        ga.addListener(new SchemaCoverageMonitor());
//        ga.addListener(new EventRecounter());

        // start actual search
        ga.generateSolution();

        // report
        XMLTestSuiteChromosome solution = (XMLTestSuiteChromosome) ga.getBestIndividual();
        logger.info("Search finished with fitness {} after {} generations.", solution.getFitness(), ga.getAge());
        solution.writeToFiles();
    }
}
