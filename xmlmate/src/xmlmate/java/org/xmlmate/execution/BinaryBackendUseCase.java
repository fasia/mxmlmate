package org.xmlmate.execution;

import java.io.File;
import java.util.List;

import org.evosuite.ga.FitnessFunction;
import org.evosuite.ga.FitnessReplacementFunction;
import org.evosuite.ga.SelectionFunction;
import org.evosuite.ga.SteadyStateGA;
import org.evosuite.ga.TournamentSelection;
import org.xmlmate.formats.PNGConverter;
import org.xmlmate.genetics.BasicBlockCoverageFitnessFunction;
import org.xmlmate.genetics.XMLTestChromosome;
import org.xmlmate.genetics.XMLTestSuiteChromosome;
import org.xmlmate.genetics.XMLTestSuiteChromosomeFactory;
import org.xmlmate.monitoring.EventRecounter;

public class BinaryBackendUseCase extends EvolveBranchCoverageUseCase {

	private BasicBlockCoverageFitnessFunction fitnessFunction;

	public BinaryBackendUseCase(XMLTestSuiteChromosomeFactory factory, BasicBlockCoverageFitnessFunction fitnessFunction) {
		super(factory);
		this.fitnessFunction = fitnessFunction;
		XMLTestChromosome.setConverter(new PNGConverter()); // FIXME make this a parameter
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
