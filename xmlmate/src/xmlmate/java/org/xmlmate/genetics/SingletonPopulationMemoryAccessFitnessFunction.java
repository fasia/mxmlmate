package org.xmlmate.genetics;

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

public class SingletonPopulationMemoryAccessFitnessFunction extends MemoryAccessFitnessFunction {
	private static final Logger logger = LoggerFactory.getLogger(SingletonPopulationMemoryAccessFitnessFunction.class);

	@Override
	public double getFitness(XMLTestSuiteChromosome individual) {
		int max = 0;
		Map<Integer, File> awaited = new HashMap<Integer, File>();
		
		for (int i = 0; i < individual.size(); ++i) {
			XMLTestChromosome x = individual.getTestChromosome(i);
			if (!x.isChanged()) {
				int cachedSize = ((MemoryAccessExecutionResult) x.getLastExecutionResult()).getAddresses().length;
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
					awaited.put(i, outputFile);

				} catch (IOException e) {
					logger.error("Could not evaluate chromosome {}", i);
				}
			}
		}
		
		logger.trace("Waiting for coverage");
		for (int i = 0; i < awaited.size(); i++) {
			try {
				ByteBuffer buffer = ByteBuffer.wrap(coverageIn.recv());
				BufferUnpacker unpk = msg.createBufferUnpacker(buffer);
				int num = unpk.readInt();
				long[] la = unpk.read(long[].class);
				logger.trace("received {} items", la.length);

				XMLTestChromosome x = individual.getTestChromosome(num);
				x.setLastExecutionResult(new MemoryAccessExecutionResult(la));
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
				logger.error("Could not read fitness message for chromosome");
			}	
		}
		
		individual.setFitness(max);
		individual.setChanged(false);
		return max;
	}

}
