package org.xmlmate.genetics;

import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.evosuite.utils.Randomness;
import org.msgpack.MessagePack;
import org.msgpack.unpacker.BufferUnpacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;

public class MemoryAccessFitnessFunction extends BasicBlockCoverageFitnessFunction {
	private static final Logger logger = LoggerFactory.getLogger(MemoryAccessFitnessFunction.class);
	private final MessagePack msg = new MessagePack();

	public MemoryAccessFitnessFunction(File workDir, List<String> driverCall) {
		super(workDir, driverCall);
	}
	
	@Override
	public double getFitness(XMLTestSuiteChromosome individual) {
		TLongSet addrs = new TLongHashSet();
		for (XMLTestChromosome x : individual.getTestChromosomes()) {
			if (!x.isChanged())
				addrs.addAll(((MemoryAccessExecutionResult) x.getLastExecutionResult()).getAddresses());
			else {
				File f = new File(XMLProperties.OUTPUT_PATH, String.format( "%d-%d%s", System.currentTimeMillis(), Randomness.nextShort(), XMLProperties.FILE_EXTENSION));
				try {
					File outputFile = x.writeToFile(f);
					String path = outputFile.getAbsolutePath();
					logger.trace("Sending file {}", path);
					dataOut.send(path);
					logger.trace("Waiting for coverage");
					
					ByteBuffer buffer = ByteBuffer.wrap(coverageIn.recv());
					BufferUnpacker unpk = msg.createBufferUnpacker(buffer);
					unpk.setArraySizeLimit(10000000); // XXX calibrate
					long[] la = unpk.read(long[].class);
					logger.trace("received {} items", la.length);
					TLongSet set = new TLongHashSet(la);
					la = null;
					
					addrs.addAll(set);
					x.setLastExecutionResult(new MemoryAccessExecutionResult(set));
					x.setFitness(set.size());
					x.setChanged(false);
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
		
		double fitness = addrs.size();
		individual.setFitness(fitness);
		individual.setChanged(false);
		return fitness;
	}

}
