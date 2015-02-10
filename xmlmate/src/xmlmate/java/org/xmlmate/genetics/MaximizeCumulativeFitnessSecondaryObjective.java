package org.xmlmate.genetics;

import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;

import org.evosuite.ga.Chromosome;
import org.evosuite.ga.SecondaryObjective;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MaximizeCumulativeFitnessSecondaryObjective extends SecondaryObjective {
	private static final Logger logger = LoggerFactory.getLogger(MaximizeCumulativeFitnessSecondaryObjective.class);

	private int getCumulativeFitness(XMLTestSuiteChromosome individual) {
		TLongSet addrs = new TLongHashSet();
		for (XMLTestChromosome x : individual.getTestChromosomes())
				addrs.addAll(((MemoryAccessExecutionResult) x.getLastExecutionResult()).getAddresses());
		return addrs.size();
	}
	
	@Override
	public int compareChromosomes(Chromosome chromosome1, Chromosome chromosome2) {
		if (chromosome1 instanceof XMLTestSuiteChromosome && chromosome2 instanceof XMLTestSuiteChromosome) {
			XMLTestSuiteChromosome x1 = (XMLTestSuiteChromosome) chromosome1;
			XMLTestSuiteChromosome x2 = (XMLTestSuiteChromosome) chromosome2;
			return getCumulativeFitness(x2) - getCumulativeFitness(x1); // keep 1st if negative
		}
		logger.warn("Tried to use MaximizeCumulativeFitnessSecondaryObjective for non XML Suite chromosomes");
		return 0;
	}

	@Override
	public int compareGenerations(Chromosome parent1, Chromosome parent2,
			Chromosome child1, Chromosome child2) {
		// TODO Auto-generated method stub
		return 0;
	}

}
