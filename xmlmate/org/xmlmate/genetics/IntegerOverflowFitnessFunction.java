package org.xmlmate.genetics;

import gnu.trove.map.TLongLongMap;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.procedure.TLongLongProcedure;
import gnu.trove.procedure.TLongProcedure;

import org.evosuite.Properties;
import org.msgpack.MessagePack;
import org.msgpack.unpacker.BufferUnpacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

public class IntegerOverflowFitnessFunction extends BinaryBackendFitnessFunction {
    private static final long serialVersionUID = 2226177116546744577L;
    private static final Logger logger = LoggerFactory.getLogger(IntegerOverflowFitnessFunction.class);
    private static final TLongLongMap EMPTY_RESULT = new TLongLongHashMap(0);
    private final MessagePack msgUnpack = new MessagePack();

    public IntegerOverflowFitnessFunction() {
        assert Properties.POPULATION == 1 : "The IntegerOverflowFitnessFunction can only be used with a singleton population!";
    }

    @Override
    public double getFitness(XMLTestSuiteChromosome individual) {
        evaluationClock.start();

        final TLongLongMap maxes = new TLongLongHashMap(Properties.MAX_SIZE);
        
        for (XMLTestChromosome x : individual.getTestChromosomes()) {
            if (!x.isChanged()) {
                TLongLongMap cached = ((DistanceMapExecutionResult) x.getLastExecutionResult()).getDistances();
                cached.forEachEntry(new UpdateMaximum(maxes));
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

                TLongLongMap result = EMPTY_RESULT;
                long max = 0L;

                if (dead) {
                    storeCrashChromosome(individual.getTestChromosome(num));
                } else {
                    int mapSize = unpk.readMapBegin();
                    logger.trace("Chromosome {} triggered {} mul|add instructions", num, mapSize);
                    result = new TLongLongHashMap(mapSize);
                    for (int j = 0; j < mapSize; j++) {
                        long key = unpk.readLong();
                        long[] values = unpk.read(long[].class);
                        long value = values[0] + values[1];
                        if (value > max)
                            max = value;
                        result.put(key, value);
                        if (value > maxes.get(key))
                            maxes.put(key, value);
                    }
                    unpk.readMapEnd();
                }
                XMLTestChromosome x = individual.getTestChromosome(num);
                x.setLastExecutionResult(new DistanceMapExecutionResult(result));
                x.setFitness(max);
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

        GetMaxValue minEntry = new GetMaxValue();
        maxes.forEachValue(minEntry);
        long fitness = minEntry.max;
        individual.setFitness(fitness);
        individual.setChanged(false);
        evaluationClock.stop();
        return fitness;
    }

    @Override
    public boolean isMaximizationFunction() {
        return true;
    }

    private static class GetMaxValue implements TLongProcedure {
        long max = 0L;

        @Override
        public boolean execute(long arg0) {
            if (arg0 > max)
                max = arg0;
            return true;
        }
    }

    private static class UpdateMaximum implements TLongLongProcedure {
        private final TLongLongMap maxes;

        public UpdateMaximum(TLongLongMap maxes) {
            this.maxes = maxes;
        }

        @Override
        public boolean execute(long a, long b) {
            long prev = maxes.get(a);
            if (b > prev)
                maxes.put(a, b);
            return true;
        }
    }
}
