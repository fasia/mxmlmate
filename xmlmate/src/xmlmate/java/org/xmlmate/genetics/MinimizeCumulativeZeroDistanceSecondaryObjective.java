package org.xmlmate.genetics;

import gnu.trove.procedure.TLongProcedure;

import org.evosuite.ga.Chromosome;
import org.evosuite.ga.SecondaryObjective;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinimizeCumulativeZeroDistanceSecondaryObjective extends SecondaryObjective {
    private static final long serialVersionUID = 7263763416972293234L;
    private static final Logger logger = LoggerFactory.getLogger(MinimizeCumulativeZeroDistanceSecondaryObjective.class);

    private static class GetMinValue implements TLongProcedure {
	long min = Long.MAX_VALUE;
	@Override
	public boolean execute(long arg0) {
	    if (arg0 < min)
		min = arg0;
	    return true;
	}
    }
    
    private long getCumulativeFitness(XMLTestSuiteChromosome individual) {
	long sum = 0l;
	for (XMLTestChromosome x : individual.getTestChromosomes()) {
	    GetMinValue minEntry = new GetMinValue();
	    ((DistanceMapExecutionResult) x.getLastExecutionResult()).getDistances().forEachValue(minEntry);
	    sum += minEntry.min;
	}
	return sum;
    }

    @Override
    public int compareChromosomes(Chromosome chromosome1, Chromosome chromosome2) {
	if (chromosome1 instanceof XMLTestSuiteChromosome && chromosome2 instanceof XMLTestSuiteChromosome) {
	    XMLTestSuiteChromosome x1 = (XMLTestSuiteChromosome) chromosome1;
	    XMLTestSuiteChromosome x2 = (XMLTestSuiteChromosome) chromosome2;
	    return (int)(getCumulativeFitness(x1) - getCumulativeFitness(x2));
	}
	logger.warn("Tried to use MinimizeCumulativeZeroDistanceSecondaryObjective for non XML Suite chromosomes");
	return 0;
    }

    @Override
    public int compareGenerations(Chromosome parent1, Chromosome parent2, Chromosome child1, Chromosome child2) {
	// TODO Auto-generated method stub
	return 0;
    }

}
