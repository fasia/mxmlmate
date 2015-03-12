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
import org.xmlmate.XMLProperties;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

public class DivByZeroFitnessFunction extends BinaryBackendFitnessFunction {
    private static final long serialVersionUID = 2226177116546744577L;
    private static final Logger logger = LoggerFactory.getLogger(DivByZeroFitnessFunction.class);
    private final MessagePack msgUnpack = new MessagePack();

    public DivByZeroFitnessFunction() {
        assert Properties.POPULATION == 1 : "The DivByZeroFitnessFunction can only be used with a singleton population!";
        XMLTestSuiteChromosome.addSecondaryObjective(new MaximizeDivisionsSecondaryObjective());
        XMLTestSuiteChromosome.addSecondaryObjective(new MinimizeCumulativeZeroDistanceSecondaryObjective());
    }

    @Override
    public double getFitness(XMLTestSuiteChromosome individual) {
        evaluationClock.start();

        final TLongLongMap mins = new TLongLongHashMap(Properties.MAX_SIZE, 0.68f, 0, Long.MAX_VALUE);

        for (XMLTestChromosome x : individual.getTestChromosomes()) {
            if (!x.isChanged()) {
                TLongLongMap cached = ((DistanceMapExecutionResult) x.getLastExecutionResult()).getDistances();
                cached.forEachEntry(new UpdateMinimum(mins));
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

                TLongLongMap result = new TLongLongHashMap(0, 0.68f, 0, Long.MAX_VALUE);
                long min = Long.MAX_VALUE;

                if (dead) {
                    storeCrashChromosome(individual.getTestChromosome(num));
                } else {
                    int mapSize = unpk.readMapBegin();
                    logger.trace("Chromosome {} triggered {} div instructions", num, mapSize);
                    result = new TLongLongHashMap(mapSize, 0.68f, 0, Long.MAX_VALUE);
                    for (int j = 0; j < mapSize; j++) {
                        long key = unpk.readLong();
                        long value = unpk.readLong();
                        if (value < min)
                            min = value;
                        result.put(key, value);
                        if (value < mins.get(key))
                            mins.put(key, value);
                    }
                    unpk.readMapEnd();
                }
                XMLTestChromosome x = individual.getTestChromosome(num);
                x.setLastExecutionResult(new DistanceMapExecutionResult(result));
                x.setFitness(min);
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

        GetMinValue minEntry = new GetMinValue();
        mins.forEachValue(minEntry);
        long fitness = minEntry.min;
        individual.setFitness(fitness);
        individual.setChanged(false);
        evaluationClock.stop();
        return fitness;
    }

    @Override
    public boolean isMaximizationFunction() {
        return false;
    }

    private static class GetMinValue implements TLongProcedure {
        long min = Long.MAX_VALUE;

        @Override
        public boolean execute(long arg0) {
            if (arg0 < min)
                min = arg0;
            return true;
        }
    }

    private static class UpdateMinimum implements TLongLongProcedure {
        private final TLongLongMap mins;

        public UpdateMinimum(TLongLongMap mins) {
            this.mins = mins;
        }

        @Override
        public boolean execute(long a, long b) {
            long prev = mins.get(a);
            if (b < prev)
                mins.put(a, b);
            return true;
        }
    }
}
