package org.xmlmate.genetics;

import java.util.concurrent.TimeUnit;

import org.evosuite.Properties;
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.ga.SteadyStateGA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

public class SingletonPopulationGA extends SteadyStateGA<XMLTestSuiteChromosome> {
	private static final Logger logger = LoggerFactory.getLogger(SingletonPopulationGA.class);
    public static long mutationTime = 0l;
    public static long cloningTime = 0l;
    public static long evalTime = 0l;

	public SingletonPopulationGA(
			ChromosomeFactory<XMLTestSuiteChromosome> factory) {
		super(factory);
		assert Properties.POPULATION == 1 : "Population size must be 1! It is currently "
				+ Properties.POPULATION;
	}

	@Override
	protected void evolve() {
		assert population.size() == 1;
		XMLTestSuiteChromosome parent = population.get(0);
		double oldFitness = parent.getFitness();
		Stopwatch w = Stopwatch.createStarted();
		XMLTestSuiteChromosome child = (XMLTestSuiteChromosome) parent.clone();
		cloningTime += w.elapsed(TimeUnit.MICROSECONDS);
		logger.trace("Cloning:  {}",w);
		w.reset();
		w.start();
		do
			child.mutate();
		while (!child.isChanged());
		child.updateAge(currentIteration);
		mutationTime += w.elapsed(TimeUnit.MICROSECONDS);
		logger.trace("Mutating: {}",w);
		w.reset();
		w.start();
		double newFitness = fitnessFunction.getFitness(child); // this causes the heavy lifting
		boolean better = false;
		if (fitnessFunction.isMaximizationFunction()) {
			better = newFitness > oldFitness ||
					newFitness == oldFitness && child.compareSecondaryObjective(parent) < 0;
		} else {
			better = newFitness < oldFitness || 
					newFitness == oldFitness && child.compareSecondaryObjective(parent) < 0;
		}
		if (better)
			population.set(0, child);
		currentIteration += 1;
		w.stop();
		evalTime += w.elapsed(TimeUnit.MICROSECONDS);
		logger.trace("Evaluate: {}",w);
	}

}
