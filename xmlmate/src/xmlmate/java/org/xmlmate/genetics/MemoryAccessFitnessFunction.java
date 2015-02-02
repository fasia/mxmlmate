package org.xmlmate.genetics;

import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;

public class MemoryAccessFitnessFunction extends BasicBlockCoverageFitnessFunction {
	private static final Logger logger = LoggerFactory.getLogger(MemoryAccessFitnessFunction.class);

	public MemoryAccessFitnessFunction(File workDir, List<String> driverCall) {
		super(workDir, driverCall);
	}
	
	@Override
	public double getFitness(XMLTestSuiteChromosome individual) {
		TIntSet addrs = new TIntHashSet();
		for (XMLTestChromosome x : individual.getTestChromosomes()) {
			if (!x.isChanged())
				addrs.addAll(((MemoryAccessExecutionResult) x.getLastExecutionResult()).getAddresses());
			else {
				File f = new File(XMLProperties.OUTPUT_PATH, String.format( "%d-%d%s", System.currentTimeMillis(), Randomness.nextShort(), XMLProperties.FILE_EXTENSION));
				try {
					File outputFile = x.writeToFile(f);
					String path = outputFile.getAbsolutePath();
					logger.debug("Sending file {}", path);
					dataOut.send(path);
					logger.trace("Waiting for coverage");
					String coverage = coverageIn.recvStr();
					logger.debug("Received coverage message of length {}", coverage.length());
					
					TIntSet set = new TIntHashSet();
					// TODO use google protobuf here to deserialize the message
					
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
