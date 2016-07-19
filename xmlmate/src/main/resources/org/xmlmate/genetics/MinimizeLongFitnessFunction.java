package org.xmlmate.genetics;


import org.evosuite.Properties;
import org.evosuite.testcase.ExecutionResult;
import org.msgpack.MessagePack;
import org.msgpack.unpacker.BufferUnpacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class MinimizeLongFitnessFunction extends BinaryBackendFitnessFunction {
    private static final long serialVersionUID = 7313215088569640113L;
    private static final Logger logger = LoggerFactory.getLogger(MinimizeLongFitnessFunction.class);
    private final MessagePack msgUnpack = new MessagePack();

    public MinimizeLongFitnessFunction() {
        assert Properties.POPULATION == 1 : "The MinimizeLongFitnessFunction can only be used with a singleton population!";
    }

    @Override
    public double getFitness(XMLTestSuiteChromosome individual) {
        evaluationClock.start();

        long min = Long.MAX_VALUE;

        for (XMLTestChromosome x : individual.getTestChromosomes()) {
            if (!x.isChanged()) {
                long time = x.getLastExecutionResult().getExecutionTime();
                if (time < min)
                    min = time;
            }
        }
        Map<Integer, File> awaited = sendChangedChromosomes(individual.getTestChromosomes());

        logger.trace("Waiting for coverage of {} files", awaited.size());

        for (int i = 0; i < awaited.size(); i++) {
            try (BufferUnpacker unpk = msgUnpack.createBufferUnpacker(receiveResult())) {
                int num = unpk.readInt();
                boolean dead = unpk.readBoolean();
                long time = Long.MAX_VALUE;

                if (dead) {
                    storeCrashChromosome(individual.getTestChromosome(num));
                } else {
                    time = unpk.readLong();
                    logger.trace("Chromosome {} lasted {} nanoseconds", num, time);
                    if (time < min)
                        min = time;
                }
                XMLTestChromosome x = individual.getTestChromosome(num);
                ExecutionResult result = new ExecutionResult(null);
                result.setExecutionTime(time);
                x.setLastExecutionResult(result);
                x.setFitness(time);
                x.setChanged(false);

                File outputFile = awaited.get(num);
                // clean up temporary file
                if (outputFile.exists() && !outputFile.delete()) {
                    logger.warn("Could not delete temporary file after evaluating {}", outputFile.getAbsolutePath());
                    outputFile.deleteOnExit();
                }

            } catch (IOException e) {
                logger.error("Could not read fitness message for chromosome", e);
            }
        }

        individual.setFitness(min);
        individual.setChanged(false);
        evaluationClock.stop();
        return min;
    }

    @Override
    public boolean isMaximizationFunction() {
        return false;
    }

}
