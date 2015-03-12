package org.xmlmate.genetics;

import org.evosuite.Properties;
import org.msgpack.unpacker.BufferUnpacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

public class SingletonPopulationMemoryAccessFitnessFunction extends MemoryAccessFitnessFunction {
    private static final long serialVersionUID = -5379138832761549718L;
    private static final Logger logger = LoggerFactory.getLogger(SingletonPopulationMemoryAccessFitnessFunction.class);

    public SingletonPopulationMemoryAccessFitnessFunction() {
        assert Properties.POPULATION == 1 : "The SingletonPopulationMemoryAccessFitnessFunction can only be used with a singleton population!";
        XMLTestSuiteChromosome.addSecondaryObjective(new MaximizeCumulativeFitnessSecondaryObjective());
    }

    @Override
    public double getFitness(XMLTestSuiteChromosome individual) {
        evaluationClock.start();
        int max = 0;

        for (int i = 0; i < individual.size(); ++i) {
            XMLTestChromosome x = individual.getTestChromosome(i);
            if (!x.isChanged()) {
                int cachedSize = ((AddressStoringExecutionResult) x.getLastExecutionResult()).getAddresses().length;
                if (cachedSize > max)
                    max = cachedSize;
            }
        }
        Map<Integer, File> awaited = sendChangedChromosomes(individual.getTestChromosomes());

        logger.trace("Waiting for coverage of {} files", awaited.size());

        for (int i = 0; i < awaited.size(); i++) {
            try {
                ByteBuffer buffer = receiveResult();
                BufferUnpacker unpk = msgUnpack.createBufferUnpacker(buffer);
                unpk.setArraySizeLimit(10000000);
                int num = unpk.readInt();
                boolean dead = unpk.readBoolean();
                long[] la = EMPTY_RESULT;
                if (dead) {
                    logger.info("Chromosome {} crashed a worker!", num);
                    individual.getTestChromosome(num).writeToFile(new File(XMLProperties.OUTPUT_PATH,
                        "crash" + crashCounter.incrementAndGet() + XMLProperties.FILE_EXTENSION), true);
                } else {
                    la = unpk.read(long[].class);
                    logger.trace("Received {} items for chromosome {}", la.length, num);
                }

                XMLTestChromosome x = individual.getTestChromosome(num);
                x.setLastExecutionResult(new AddressStoringExecutionResult(la));
                x.setFitness(la.length);
                x.setChanged(false);

                if (la.length > max)
                    max = la.length;

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

        individual.setFitness(max);
        individual.setChanged(false);
        evaluationClock.stop();
        return max;
    }

}
