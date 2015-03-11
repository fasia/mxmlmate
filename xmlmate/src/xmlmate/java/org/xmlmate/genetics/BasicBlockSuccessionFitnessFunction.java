package org.xmlmate.genetics;

import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.procedure.TLongObjectProcedure;
import gnu.trove.procedure.TObjectProcedure;
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

public class BasicBlockSuccessionFitnessFunction extends BinaryBackendFitnessFunction {
	private static final long serialVersionUID = 2226177116546744577L;
	private static final Logger logger = LoggerFactory.getLogger(BasicBlockSuccessionFitnessFunction.class);
	private final MessagePack msgUnpack = new MessagePack();

	public BasicBlockSuccessionFitnessFunction() {
	    // TODO secondary objectives
	}

	@Override
	public double getFitness(XMLTestSuiteChromosome individual) {
	    evaluationClock.start();

	    final TLongObjectMap<TLongSet> targets = new TLongObjectHashMap<>();

	    for (XMLTestChromosome x : individual.getTestChromosomes()) {
	        if (!x.isChanged()) {
	            TLongObjectMap<TLongSet> cached = ((BasicBlockSuccessionMapExecutionResult) x.getLastExecutionResult()).getSuccessions();
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

                TLongObjectMap<TLongSet> result = new TLongObjectHashMap<>();

                if (dead) {
                    logger.info("Chromosome {} crashed a worker!", num);
                    individual.getTestChromosome(num).writeToFile(new File(XMLProperties.OUTPUT_PATH,
                        "crash" + crashCounter.incrementAndGet() + XMLProperties.FILE_EXTENSION), true);
                } else {
                    int mapSize = unpk.readMapBegin();
                    result = new TLongObjectHashMap<>(mapSize);
                    for (int j = 0; j < mapSize; j++) {
                        int size = unpk.readMapBegin();
                        long key = unpk.readLong();
                        if (null==result.get(key))
                            result.put(key, new TLongHashSet());
                        TLongSet set = result.get(key);
                        for (int k = 0; k < size; k++)
                            set.add(unpk.readLong());
                        unpk.readMapEnd();
                    }
                    unpk.readMapEnd();
                }

                result.forEachEntry(new UpdateTargets(targets));

                XMLTestChromosome x = individual.getTestChromosome(num);
                x.setLastExecutionResult(new BasicBlockSuccessionMapExecutionResult(result));
                CountTargets countTargets = new CountTargets();
                result.forEachValue(countTargets);
                x.setFitness(countTargets.getCount());
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

    private static class UpdateTargets implements TLongObjectProcedure<TLongSet> {
        private final TLongObjectMap<TLongSet>	targets;

        public UpdateTargets(TLongObjectMap<TLongSet> targets) {
            this.targets = targets;
        }

		@Override
		public boolean execute(long a, TLongSet b) {
			if (null==targets.get(a))
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
}
