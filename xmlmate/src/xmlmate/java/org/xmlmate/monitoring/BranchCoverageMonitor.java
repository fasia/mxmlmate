package org.xmlmate.monitoring;

import org.evosuite.ga.Chromosome;
import org.evosuite.ga.GeneticAlgorithm;
import org.evosuite.ga.SearchListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

/**
 * This class assumes the fitness function is a minimization function!
 */
public class BranchCoverageMonitor implements SearchListener {
    private static final Logger logger = LoggerFactory.getLogger(BranchCoverageMonitor.class);
    private final BufferedWriter writer;
    private int lastUpdate = 0;
    private double lastFitness = Double.MAX_VALUE;

    public BranchCoverageMonitor() {
        File output = new File(XMLProperties.OUTPUT_PATH, XMLProperties.RUN_NAME + " fitness.log");
        logger.info("Will write the fitness log to '{}'", output);
        BufferedWriter bufferedWriter = null;
        try {
            bufferedWriter = new BufferedWriter(new FileWriter(output, true));
        } catch (IOException e) {
            logger.error("Could not create a schema coverage log due to {}", e.getMessage());
        }
        writer = bufferedWriter;
    }

    @Override
    public void searchStarted(GeneticAlgorithm<?> algorithm) {
        if (null == writer) {
            algorithm.removeListener(this);
            return;
        }
        try {
            writer.write(XMLProperties.getRunParams());
            writer.newLine();
        } catch (IOException e) {
            logger.error("Could not write a fitness log entry due to {}", e.getMessage());
            algorithm.removeListener(this);
        }
    }

    @Override
    public void iteration(GeneticAlgorithm<?> algorithm) {
        Chromosome suite = algorithm.getBestIndividual();
        double newFitness = suite.getFitness();
        if (newFitness < lastFitness) {
            int age = algorithm.getAge();
            try {
                writer.write(String.format(Locale.US, "%04d,%.4f,%.4f", age, suite.getCoverage(), newFitness));
                writer.newLine();
                if (10 <= age - lastUpdate) {
                    writer.flush();
                    lastUpdate = age;
                }
                lastFitness = newFitness;
            } catch (IOException e) {
                logger.error("Could not write a fitness log entry due to {}", e.getMessage());
                algorithm.removeListener(this);
            }
        }
    }

    @Override
    public void searchFinished(GeneticAlgorithm<?> algorithm) {
        try {
            writer.flush();
        } catch (IOException ignored) {
        } finally {
            try {
                if (null != writer)
                    writer.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void fitnessEvaluation(Chromosome individual) {

    }

    @Override
    public void modification(Chromosome individual) {

    }
}
