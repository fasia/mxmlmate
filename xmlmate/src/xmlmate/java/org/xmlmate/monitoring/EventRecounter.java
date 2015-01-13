package org.xmlmate.monitoring;

import org.evosuite.ga.Chromosome;
import org.evosuite.ga.GeneticAlgorithm;
import org.evosuite.ga.SearchListener;
import org.evosuite.testsuite.AbstractTestSuiteChromosome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventRecounter implements SearchListener {
    private static final Logger logger = LoggerFactory.getLogger(EventRecounter.class);
    private long last;
    private long durations = 0;

    @Override
    public void searchStarted(GeneticAlgorithm<?> algorithm) {
        last = System.currentTimeMillis();
    }

    @Override
    public void iteration(GeneticAlgorithm<?> algorithm) {
        long now = System.currentTimeMillis();
        int length = 0;
        int size = 0;
        for (Chromosome chromosome : algorithm.getPopulation()) {
            length += ((AbstractTestSuiteChromosome) chromosome).totalLengthOfTestCases();
            size += chromosome.size();
        }
        long duration = now - last;
        durations += duration;
        logger.info("Generation {} had size {}, length {}, and took {} sec.", algorithm.getAge(), size, length, duration / 1000d);
        last = now;
    }

    @Override
    public void searchFinished(GeneticAlgorithm<?> algorithm) {
        logger.info(String.format("Average generation duration: %s sec.", durations / algorithm.getAge() / 1000.0d));
    }

    @Override
    public void fitnessEvaluation(Chromosome individual) {
    }

    @Override
    public void modification(Chromosome individual) {
    }
}

