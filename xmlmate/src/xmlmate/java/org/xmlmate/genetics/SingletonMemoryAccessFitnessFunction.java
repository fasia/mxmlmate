package org.xmlmate.genetics;

import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.evosuite.utils.Randomness;
import org.msgpack.packer.BufferPacker;
import org.msgpack.unpacker.BufferUnpacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;

public class SingletonMemoryAccessFitnessFunction extends MemoryAccessFitnessFunction {
	private static final Logger logger = LoggerFactory.getLogger(SingletonMemoryAccessFitnessFunction.class);

	@Override
	public double getFitness(XMLTestSuiteChromosome individual) {
		int max = 0;
		Map<Integer, File> awaited = new HashMap<Integer, File>();
		
		for (int i = 0; i < individual.size(); ++i) {
			XMLTestChromosome x = individual.getTestChromosome(i);
			if (!x.isChanged()) {
				int cachedSize = ((MemoryAccessExecutionResult) x.getLastExecutionResult()).getAddresses().size();
				if (cachedSize > max)
					max = cachedSize;
			} else {
				File f = new File(XMLProperties.OUTPUT_PATH, String.format( "%d-%d%s", System.currentTimeMillis(), Randomness.nextShort(), XMLProperties.FILE_EXTENSION));
				try {
					File outputFile = x.writeToFile(f);
					String path = outputFile.getAbsolutePath();

					BufferPacker packer = msg.createBufferPacker();
					packer.write(i);
					packer.write(path);

					logger.trace("Sending task {}:{}", i, path);
					dataOut.send(packer.toByteArray());
					logger.trace("Waiting for coverage");
					awaited.put(i, outputFile);

				} catch (IOException e) {
					logger.error("Could not evaluate chromosome {}", i);
				}
			}
		}
			
		for (int i = 0; i < awaited.size(); i++) {
			try {
				ByteBuffer buffer = ByteBuffer.wrap(coverageIn.recv());
				BufferUnpacker unpk = msg.createBufferUnpacker(buffer);
				int num = unpk.readInt();
				long[] la = unpk.read(long[].class);
				logger.trace("received {} items", la.length);
				TLongSet set = new TLongHashSet(la);
				la = null;

				XMLTestChromosome x = individual.getTestChromosome(num);
				x.setLastExecutionResult(new MemoryAccessExecutionResult(set));
				x.setFitness(set.size());
				x.setChanged(false);
				if (set.size() > max)
					max = set.size();

				File outputFile = awaited.get(num);
				// clean up temporary file
				if (outputFile.exists() && !outputFile.delete()) {
					logger.warn("Could not delete temporary file after evaluating {}", outputFile.getAbsolutePath());
					outputFile.deleteOnExit();
				}
			} catch (IOException e) {
				logger.error("Could not read fitness message for chromosome");
			}	
		}

		individual.setFitness(max);
		individual.setChanged(false);
		return max;
	}

}
