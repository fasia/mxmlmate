package org.xmlmate.genetics;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.lang3.StringUtils;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;
import org.xmlmate.execution.EvolveBranchCoverageUseCase;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

public class BasicBlockCoverageFitnessFunction extends FitnessFunction<XMLTestSuiteChromosome> {
	private static final Logger logger = LoggerFactory.getLogger(BasicBlockCoverageFitnessFunction.class);
	private Context context;
	protected Socket dataOut;
	protected Socket coverageIn;
	private Scanner in = new Scanner(System.in);

	public BasicBlockCoverageFitnessFunction() {
		// bind socket for receiving coverage results
		context = ZMQ.context(1);
		dataOut = context.socket(ZMQ.PUSH);
		coverageIn = context.socket(ZMQ.PULL);
		// XXX make the addresses parameters to the program and pass them on to the PIN tool?
		dataOut.bind("tcp://127.0.0.1:5556");
		coverageIn.bind("tcp://127.0.0.1:5557");
	}

	public void startAndReadyWorkers() {
		System.out.println("Press Enter when all workers and converters are ready");
		in.nextLine();
	}

	@Override
	public boolean isMaximizationFunction() {
		return true;
	}

	// TODO execute only changed xmls individually and compose results - need to identify bbls across runs and keep a 'covered' map
	@Override
	public double getFitness(XMLTestSuiteChromosome individual) {
		// write out suite to files // XXX pipe directly by creating named pipes?
		List<File> files = new ArrayList<>(individual.size());
		List<String> paths = new ArrayList<>(individual.size());
		for (XMLTestChromosome c : individual.getTestChromosomes()) {
			File f = new File(XMLProperties.OUTPUT_PATH, String.format( "%d-%d%s", System.currentTimeMillis(), Randomness.nextShort(), XMLProperties.FILE_EXTENSION));
			try {
				File outputFile = c.writeToFile(f);
				files.add(outputFile);
				paths.add(outputFile.getAbsolutePath());
			} catch (IOException e) {
				logger.error("Could not write chromosome out to file " + f.getAbsolutePath());
				if (f.exists() && !f.delete()) {
					logger.warn("Could not delete temporary file " + f.getAbsolutePath());
					f.deleteOnExit();
				}
			}
		}

		double fitness = 0d;
		
//		String message = StringUtils.join(paths, File.pathSeparator); // commons
		String message = Joiner.on(File.pathSeparatorChar).join(paths); // guava
		
		logger.trace("Sending files to workers");
		dataOut.send(message);
		logger.trace("Waiting for coverage");
		String coverage = coverageIn.recvStr();
		logger.trace(MessageFormat.format("Received {0} as coverage.", coverage));
		fitness = Integer.parseInt(coverage);
		individual.setFitness(fitness);

		// clean up temporary files
		for (File file : files) {
			if (file.exists() && !file.delete()) {
				logger.warn("Could not delete temporary file after evaluating " + file.getAbsolutePath());
				file.deleteOnExit();
			}
		}

		return fitness;
	}
	
	public void freeSockets() {
		in.close();
		dataOut.close();
		coverageIn.close();
		context.term();
	}

}
