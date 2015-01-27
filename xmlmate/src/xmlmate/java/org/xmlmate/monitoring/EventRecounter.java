package org.xmlmate.monitoring;

import java.util.Arrays;

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
        double[] fitnesses = new double[algorithm.getPopulationSize()];
        int[] sizes = new int[algorithm.getPopulationSize()];
        int cursor = 0;
        for (Chromosome chromosome : algorithm.getPopulation()) {
            length += ((AbstractTestSuiteChromosome) chromosome).totalLengthOfTestCases();
            size += chromosome.size();
            sizes[cursor] = chromosome.size();
            fitnesses[cursor] = chromosome.getFitness();
            cursor += 1;
        }
        Chromosome bestIndividual = algorithm.getBestIndividual();
        long duration = now - last;
        durations += duration;
        logger.info("Generation {} had size {}, length {}, and took {} sec. Best fitness {} from gen {}", algorithm.getAge(), size, length, duration / 1000d, bestIndividual.getFitness(), bestIndividual.getAge());
        logger.info("Fitnesses: "+Arrays.toString(fitnesses));
        logger.info("Sizes:     "+Arrays.toString(sizes));
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

