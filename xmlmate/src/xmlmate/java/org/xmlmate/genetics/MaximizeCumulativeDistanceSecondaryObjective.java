package org.xmlmate.genetics;

import gnu.trove.procedure.TLongProcedure;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.SecondaryObjective;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MaximizeCumulativeDistanceSecondaryObjective extends SecondaryObjective {
    private static final long serialVersionUID = -8713255327186258170L;
    private static final Logger logger = LoggerFactory.getLogger(MaximizeCumulativeDistanceSecondaryObjective.class);

    private static class SumValues implements TLongProcedure {
        long sum = 0L;

        @Override
        public boolean execute(long arg0) {
            sum += arg0;
            return true;
        }
    }

    private long getCumulativeFitness(XMLTestSuiteChromosome individual) {
        long sum = 0L;
        for (XMLTestChromosome x : individual.getTestChromosomes()) {
            SumValues sumValues = new SumValues();
            ((DistanceMapExecutionResult) x.getLastExecutionResult()).getDistances().forEachValue(sumValues);
            sum += sumValues.sum;
        }
        return sum;
    }

    @Override
    public int compareChromosomes(Chromosome chromosome1, Chromosome chromosome2) {
        if (chromosome1 instanceof XMLTestSuiteChromosome && chromosome2 instanceof XMLTestSuiteChromosome) {
            XMLTestSuiteChromosome x1 = (XMLTestSuiteChromosome) chromosome1;
            XMLTestSuiteChromosome x2 = (XMLTestSuiteChromosome) chromosome2;
            return (int) (getCumulativeFitness(x1) - getCumulativeFitness(x2));
        }
        logger.warn("Tried to use MaximizeCumulativeDistanceSecondaryObjective for non XML Suite chromosomes");
        return 0;
    }

    @Override
    public int compareGenerations(Chromosome parent1, Chromosome parent2, Chromosome child1, Chromosome child2) {
        // TODO Auto-generated method stub
        return 0;
    }

}
