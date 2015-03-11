package org.xmlmate.genetics;

import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.procedure.TLongObjectProcedure;
import gnu.trove.procedure.TObjectProcedure;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import org.msgpack.MessagePack;
import org.msgpack.unpacker.BufferUnpacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;

public class BasicBlockSuccessionFitnessFunction extends BinaryBackendFitnessFunction {
	private static final long serialVersionUID = 2226177116546744577L;
	private static final Logger logger = LoggerFactory.getLogger(BasicBlockSuccessionFitnessFunction.class);
	private final MessagePack msgUnpack = new MessagePack();
	
	@Override
	public double getFitness(XMLTestSuiteChromosome individual) {
	    evaluationClock.start();

	    final TLongObjectMap<TLongSet> targets = new TLongObjectHashMap<>();

	    for (XMLTestChromosome x : individual.getTestChromosomes()) {
	        if (!x.isChanged()) {
	            TLongObjectMap<long[]> cached = ((BasicBlockSuccessionMapExecutionResult) x.getLastExecutionResult()).getSuccessions();
	            cached.forEachEntry(new UpdateTargets(targets));
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

                TLongObjectMap<long[]> result = new TLongObjectHashMap<>();

                if (dead) {
                    logger.info("Chromosome {} crashed a worker!", num);
                    individual.getTestChromosome(num).writeToFile(new File(XMLProperties.OUTPUT_PATH,
                        "crash" + crashCounter.incrementAndGet() + XMLProperties.FILE_EXTENSION), true);
                } else {
                    int mapSize = unpk.readMapBegin();
                    result = new TLongObjectHashMap<>(mapSize);
                    for (int j = 0; j < mapSize; j++) {
                        long key = unpk.readLong();
                        long[] la = unpk.read(long[].class);
                        result.put(key, la);
                    }
                    unpk.readMapEnd();
                }

                result.forEachEntry(new UpdateTargets(targets));

                XMLTestChromosome x = individual.getTestChromosome(num);
                x.setLastExecutionResult(new BasicBlockSuccessionMapExecutionResult(result));
                CountResults countResults = new CountResults();
		result.forEachValue(countResults);
                x.setFitness(countResults.getCount());
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

        CountTargets countTargets = new CountTargets();
        targets.forEachValue(countTargets);
        long fitness = countTargets.getCount();
        individual.setFitness(fitness);
        individual.setChanged(false);
        evaluationClock.stop();
        return fitness;
    }

    @Override
    public boolean isMaximizationFunction() {
        return true;
    }

    private static class UpdateTargets implements TLongObjectProcedure<long[]> {
	private final TLongObjectMap<TLongSet> targets;

	public UpdateTargets(TLongObjectMap<TLongSet> targets) {
	    this.targets = targets;
	}

	@Override
	public boolean execute(long a, long[] b) {
	    if (null == targets.get(a))
		targets.put(a, new TLongHashSet());
	    targets.get(a).addAll(b);
	    return true;
	}
    }

    private static class CountTargets implements TObjectProcedure<TLongSet> {
        private int count = 0;

        public int getCount() {
            return count;
        }

        @Override
        public boolean execute(TLongSet object) {
            if (object != null) {
                count += object.size();
            }
            return true;
        }
    }
    
    private static class CountResults implements TObjectProcedure<long[]> {
        private int count = 0;

        public int getCount() {
            return count;
        }

	@Override
	public boolean execute(long[] arg0) {
	    count += arg0.length;
	    return true;
	}

    }
}
