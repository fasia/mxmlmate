package org.xmlmate.monitoring;

import com.google.common.base.Stopwatch;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.GeneticAlgorithm;
import org.evosuite.ga.SearchListener;
import org.evosuite.testsuite.AbstractTestSuiteChromosome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class EventRecounter implements SearchListener {
    private static final Logger logger = LoggerFactory.getLogger(EventRecounter.class);
    private Stopwatch globalClock;
    private Stopwatch generationClock;

    @Override
    public void searchStarted(GeneticAlgorithm<?> algorithm) {
        globalClock = Stopwatch.createStarted();
        generationClock = Stopwatch.createStarted();
    }

    @Override
    public void iteration(GeneticAlgorithm<?> algorithm) {
        generationClock.stop();
        int length = 0;
        int size = 0;
        for (Chromosome chromosome : algorithm.getPopulation()) {
            length += ((AbstractTestSuiteChromosome) chromosome).totalLengthOfTestCases();
            size += chromosome.size();
        }
        Chromosome bestIndividual = algorithm.getBestIndividual();

        logger.info("Generation {} had size {}, length {}, and took {}. Best fitness {} from gen {}", algorithm.getAge(), size, length, generationClock, bestIndividual.getFitness(), bestIndividual.getAge());
        generationClock.reset();
        generationClock.start();
    }

    @Override
    public void searchFinished(GeneticAlgorithm<?> algorithm) {
        globalClock.stop();
        logger.info(String.format("Average generation duration: %s sec.", globalClock.elapsed(TimeUnit.SECONDS) / (double) (algorithm.getAge() + 1)));
    }

    @Override
    public void fitnessEvaluation(Chromosome individual) {
    }

    @Override
    public void modification(Chromosome individual) {
    }
}

