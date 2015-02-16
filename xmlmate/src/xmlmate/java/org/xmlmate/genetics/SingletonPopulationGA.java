package org.xmlmate.genetics;

import org.evosuite.Properties;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.ga.SteadyStateGA;
import org.evosuite.localsearch.LocalSearchBudget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SingletonPopulationGA extends SteadyStateGA<XMLTestSuiteChromosome> {
	private static final Logger logger = LoggerFactory.getLogger(SingletonPopulationGA.class);
    
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
		XMLTestSuiteChromosome child = (XMLTestSuiteChromosome) parent.clone();
		
		do
			child.mutate();
		while (!child.isChanged());
		child.updateAge(currentIteration);
		
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
	}

	@Override
	protected void applyLocalSearch() {
		if(!shouldApplyLocalSearch())
			return;
		
		logger.trace("Applying local search");
		LocalSearchBudget.getInstance().localSearchStarted();

		boolean improvement = false;
		
		for (int i = 0; i < population.size(); i++) {
			Chromosome individual = population.get(i);
			if (isFinished())
				break;

			if (LocalSearchBudget.getInstance().isFinished()) {
				logger.trace("Local search budget used up, exiting local search");
				break;
			}

			XMLTestSuiteChromosome clone = ((XMLTestSuiteChromosome) individual).deepClone();
			if(clone.localSearch(localObjective)) {
				improvement = true;
				population.set(i, clone);
			}
		}
		
		if (improvement) {
			localSearchProbability *= Properties.LOCAL_SEARCH_ADAPTATION_RATE;
			localSearchProbability = Math.min(localSearchProbability, 1.0);
		} else {
			localSearchProbability /= Properties.LOCAL_SEARCH_ADAPTATION_RATE;
			localSearchProbability = Math.max(localSearchProbability, Double.MIN_VALUE);
		}
	}
}
