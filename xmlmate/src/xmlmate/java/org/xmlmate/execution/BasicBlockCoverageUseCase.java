package org.xmlmate.execution;

import java.io.File;
import java.util.List;

import org.evosuite.ga.FitnessFunction;
import org.evosuite.ga.FitnessReplacementFunction;
import org.evosuite.ga.GeneticAlgorithm;
import org.evosuite.ga.SelectionFunction;
import org.evosuite.ga.SteadyStateGA;
import org.evosuite.ga.TournamentSelection;
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
	protected void setupGAAdditions(SteadyStateGA<XMLTestSuiteChromosome> ga) {
        // selection function
        SelectionFunction<XMLTestSuiteChromosome> selectionFunction = new TournamentSelection<>();
        selectionFunction.setMaximize(true);
        ga.setSelectionFunction(selectionFunction);
        // replacement function
		ga.setReplacementFunction(new FitnessReplacementFunction(true));
		// monitoring
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
