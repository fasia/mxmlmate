package org.xmlmate.genetics;

import org.evosuite.ga.Chromosome;
import org.msgpack.MessagePack;
import org.msgpack.unpacker.BufferUnpacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class NoFitnessFunction extends BinaryBackendFitnessFunction {
    private static final long serialVersionUID = -8606280164079495508L;
    private static final Logger logger = LoggerFactory.getLogger(NoFitnessFunction.class);
    private final MessagePack msgUnpack = new MessagePack();

    // TODO implement a simple fitness function that maximizes a long number. (i.e. the time) (make it single pop only)

    @Override
    public double getFitness(XMLTestSuiteChromosome individual) {
        evaluationClock.start();

        Map<Integer, File> awaited = sendChangedChromosomes(individual.getTestChromosomes());

        logger.trace("Waiting for coverage of {} files", awaited.size());

        for (int i = 0; i < awaited.size(); i++) {
            try (BufferUnpacker unpk = msgUnpack.createBufferUnpacker(receiveResult())) {
                int num = unpk.readInt();
                // check if crashed
                if (unpk.readBoolean()) storeCrashChromosome(individual.getTestChromosome(num));

                Chromosome x = individual.getTestChromosome(num);

                x.setFitness(1.0);
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

        individual.setFitness(1.0);
        individual.setChanged(false);
        evaluationClock.stop();
        return 1.0;
    }

    @Override
    public boolean isMaximizationFunction() {
        return false;
    }

}
