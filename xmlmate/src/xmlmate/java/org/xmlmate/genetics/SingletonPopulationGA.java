package org.xmlmate.genetics;

import org.evosuite.Properties;
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.ga.SteadyStateGA;

public class SingletonPopulationGA extends SteadyStateGA<XMLTestSuiteChromosome> {
    private static final long serialVersionUID = -1553753824526692679L;

    public SingletonPopulationGA(ChromosomeFactory<XMLTestSuiteChromosome> factory) {
	super(factory);
	assert Properties.POPULATION == 1 : "Population size must be 1! It is currently " + Properties.POPULATION;
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
	    better = newFitness > oldFitness || newFitness == oldFitness && child.compareSecondaryObjective(parent) < 0;
	} else {
	    better = newFitness < oldFitness || newFitness == oldFitness && child.compareSecondaryObjective(parent) < 0;
	}
	if (better)
	    population.set(0, child);
	currentIteration += 1;
    }

}
