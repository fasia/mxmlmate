package org.xmlmate.genetics;

import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.evosuite.utils.Randomness;
import org.msgpack.unpacker.BufferUnpacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;

public class SingletonMemoryAccessFitnessFunction extends MemoryAccessFitnessFunction {
	private static final Logger logger = LoggerFactory.getLogger(SingletonMemoryAccessFitnessFunction.class);
	
	@Override
	public double getFitness(XMLTestSuiteChromosome individual) {
		int max = 0;
		for (XMLTestChromosome x : individual.getTestChromosomes()) {
			if (!x.isChanged()) {
				int cachedSize = ((MemoryAccessExecutionResult) x.getLastExecutionResult()).getAddresses().size();
				if (cachedSize > max)
					max = cachedSize;
			} else {
				File f = new File(XMLProperties.OUTPUT_PATH, String.format( "%d-%d%s", System.currentTimeMillis(), Randomness.nextShort(), XMLProperties.FILE_EXTENSION));
				try {
					File outputFile = x.writeToFile(f);
					String path = outputFile.getAbsolutePath();
					logger.trace("Sending file {}", path);
					dataOut.send(path);
					logger.trace("Waiting for coverage");
					
					ByteBuffer buffer = ByteBuffer.wrap(coverageIn.recv());
					BufferUnpacker unpk = msg.createBufferUnpacker(buffer);
					long[] la = unpk.read(long[].class);
					logger.trace("received {} items", la.length);
					TLongSet set = new TLongHashSet(la);
					la = null;
					
					x.setLastExecutionResult(new MemoryAccessExecutionResult(set));
					x.setFitness(set.size());
					x.setChanged(false);
					if (set.size() > max)
						max = set.size();
					
					// clean up temporary file
					if (outputFile.exists() && !outputFile.delete()) {
						logger.warn("Could not delete temporary file after evaluating {}", path);
						outputFile.deleteOnExit();
					}
				} catch (IOException e) {
					logger.error("Could not write chromosome out to file " + f.getAbsolutePath());
					if (f.exists() && !f.delete()) {
						logger.warn("Could not delete temporary file " + f.getAbsolutePath());
						f.deleteOnExit();
					}
				}
			}
		}
		
		individual.setFitness(max);
		individual.setChanged(false);
		return max;
	}

}
