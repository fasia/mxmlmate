package org.xmlmate.execution;

import java.io.File;
import java.util.List;

import org.evosuite.ga.FitnessFunction;
import org.evosuite.ga.GeneticAlgorithm;
import org.xmlmate.formats.PNGConverter;
import org.xmlmate.genetics.BasicBlockCoverageFitnessFunction;
import org.xmlmate.genetics.XMLTestChromosome;
import org.xmlmate.genetics.XMLTestSuiteChromosome;
import org.xmlmate.genetics.XMLTestSuiteChromosomeFactory;
import org.xmlmate.monitoring.EventRecounter;

public class BasicBlockCoverageUseCase extends EvolveBranchCoverageUseCase {

	private List<String> commands;
	private File workDir;
	private BasicBlockCoverageFitnessFunction fitnessFunction;

	public BasicBlockCoverageUseCase(XMLTestSuiteChromosomeFactory factory, File workDir, List<String> commands) {
		super(factory);
		fitnessFunction = new BasicBlockCoverageFitnessFunction(workDir, commands);
		XMLTestChromosome.setConverter(new PNGConverter());
	}

	@Override
	public void initialize() {
		fitnessFunction.startAndReadyWorkers();
	}

	@Override
	protected void addMonitoring(GeneticAlgorithm<XMLTestSuiteChromosome> ga) {
		ga.addListener(new EventRecounter());
	}

	@Override
	protected FitnessFunction chooseFitnessFunction() {
		return fitnessFunction;
	}

	@Override
	protected void freeResources() {
		fitnessFunction.freeSockets();
		fitnessFunction.destroyWorkers();
	}

}
