package org.xmlmate.genetics;


import org.evosuite.Properties;
import org.evosuite.testcase.ExecutionResult;
import org.msgpack.MessagePack;
import org.msgpack.unpacker.BufferUnpacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;

public class MinimizeTimeMaximizeSizeFitnessFunction extends BinaryBackendFitnessFunction {
    private static final long serialVersionUID = 7313215088569640113L;
    private static final Logger logger = LoggerFactory.getLogger(MinimizeTimeMaximizeSizeFitnessFunction.class);
    private final MessagePack msgUnpack = new MessagePack();

    public MinimizeTimeMaximizeSizeFitnessFunction() {
        assert Properties.POPULATION == 1 : "The MinimizeTimeMaximizeSizeFitnessFunction can only be used with a singleton population!";
    }

    @Override
    public double getFitness(XMLTestSuiteChromosome individual) {
        evaluationClock.start();

        long minTime = Long.MAX_VALUE;
        int maxSize = 0;

        for (XMLTestChromosome x : individual.getTestChromosomes()) {
            if (!x.isChanged()) {
                long time = x.getLastExecutionResult().getExecutionTime();
                int size = x.getLastExecutionResult().getExecutedStatements();
                if (size > maxSize && time <= minTime) {
                    minTime = time;
                    maxSize = size;
                }
            }
        }
        Map<Integer, File> awaited = sendChangedChromosomes(individual.getTestChromosomes());

        logger.trace("Waiting for coverage of {} files", awaited.size());

        for (int i = 0; i < awaited.size(); i++) {
            try (BufferUnpacker unpk = msgUnpack.createBufferUnpacker(receiveResult())) {
                int num = unpk.readInt();
                boolean dead = unpk.readBoolean();
                long time = Long.MAX_VALUE;

                File outputFile = awaited.get(num);
                int size = (int) outputFile.length();
                if (dead) {
                    storeCrashChromosome(individual.getTestChromosome(num));
                } else {
                    long realTime = unpk.readLong();
                    logger.trace("Chromosome {} lasted {}", num, realTime);
                    time = realTime / 10; // scale down
                    if (size > maxSize && time <= minTime) {
                        minTime = time;
                        maxSize = size;
                    }
                }

                XMLTestChromosome x = individual.getTestChromosome(num);
                ExecutionResult result = new ExecutionResult(null);
                result.setExecutionTime(time);
                result.setExecutedStatements(size);
                x.setLastExecutionResult(result);
                x.setFitness(size);
                x.setChanged(false);


                // clean up temporary file
                if (outputFile.exists() && !outputFile.delete()) {
                    logger.warn("Could not delete temporary file after evaluating {}", outputFile.getAbsolutePath());
                    outputFile.deleteOnExit();
                }

            } catch (Exception e) {
                logger.error("Could not read fitness message for chromosome", e);
            }
        }

        double fitness = maxSize;
        individual.setFitness(fitness);
        individual.setChanged(false);
        evaluationClock.stop();
        return fitness;
    }

    @Override
    public boolean isMaximizationFunction() {
        return true;
    }

}
