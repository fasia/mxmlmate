package org.xmlmate.genetics;

import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import org.msgpack.MessagePack;
import org.msgpack.unpacker.BufferUnpacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

public class MemoryAccessFitnessFunction extends BinaryBackendFitnessFunction {
    private static final long serialVersionUID = -6682118782013137115L;
    private static final Logger logger = LoggerFactory.getLogger(MemoryAccessFitnessFunction.class);
    protected static final long[] EMPTY_RESULT = new long[0];
    protected final MessagePack msgUnpack = new MessagePack();

    @Override
    public double getFitness(XMLTestSuiteChromosome individual) {
        evaluationClock.start();
        TLongSet addrs = new TLongHashSet();
        for (XMLTestChromosome x : individual.getTestChromosomes()) {
            if (!x.isChanged())
                addrs.addAll(((AddressStoringExecutionResult) x.getLastExecutionResult()).getAddresses());
        }
        Map<Integer, File> awaited = sendChangedChromosomes(individual.getTestChromosomes());

        logger.trace("Waiting for coverage of {} files", awaited.size());

        for (int i = 0; i < awaited.size(); i++) {
            try {
                ByteBuffer buffer = receiveResult();
                BufferUnpacker unpk = msgUnpack.createBufferUnpacker(buffer);
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

                addrs.addAll(la);
                File outputFile = awaited.get(num);
                assert null != outputFile;
                // clean up temporary file
                if (outputFile.exists() && !outputFile.delete()) {
                    logger.warn("Could not delete temporary file after evaluating {}", outputFile.getAbsolutePath());
                    outputFile.deleteOnExit();
                }
            } catch (IOException e) {
                logger.error("Could not read fitness message for chromosome");
            }
        }

        individual.setFitness(addrs.size());
        individual.setChanged(false);
        evaluationClock.stop();
        return addrs.size();
    }

    @Override
    public boolean isMaximizationFunction() {
        return true;
    }

}
