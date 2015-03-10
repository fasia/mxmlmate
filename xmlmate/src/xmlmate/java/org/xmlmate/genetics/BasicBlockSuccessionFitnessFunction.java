package org.xmlmate.genetics;

import gnu.trove.map.TLongLongMap;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.procedure.TLongObjectProcedure;
import gnu.trove.procedure.TLongProcedure;
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
	
	public BasicBlockSuccessionFitnessFunction() {
	    // TODO secondary objectives
	}
	
	@Override
	public double getFitness(XMLTestSuiteChromosome individual) {
	    evaluationClock.start();
	
	    final TLongObjectMap<TLongSet> targets = new TLongObjectHashMap<TLongSet>();
	
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
            
            TLongObjectMap<TLongSet> result = new TLongObjectHashMap<TLongSet>();
            // TODO proceed from here
            
			if (dead) {
			    logger.info("Chromosome {} crashed a worker!", num);
			    individual.getTestChromosome(num).writeToFile(new File(XMLProperties.OUTPUT_PATH, 
				    "crash" + crashCounter.incrementAndGet() + XMLProperties.FILE_EXTENSION), true);
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
}
