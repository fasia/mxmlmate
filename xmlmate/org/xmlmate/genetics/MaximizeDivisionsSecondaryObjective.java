package org.xmlmate.genetics;

import org.evosuite.ga.Chromosome;
import org.evosuite.ga.SecondaryObjective;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MaximizeDivisionsSecondaryObjective extends SecondaryObjective {
    private static final long serialVersionUID = -5561799183151420412L;
    private static final Logger logger = LoggerFactory.getLogger(MaximizeDivisionsSecondaryObjective.class);


    private long getCumulativeFitness(XMLTestSuiteChromosome individual) {
        long sum = 0L;
        for (XMLTestChromosome x : individual.getTestChromosomes())
            sum += ((DistanceMapExecutionResult) x.getLastExecutionResult()).getDistances().size();
        return sum;
    }

    @Override
    public int compareChromosomes(Chromosome chromosome1, Chromosome chromosome2) {
        if (chromosome1 instanceof XMLTestSuiteChromosome && chromosome2 instanceof XMLTestSuiteChromosome) {
            XMLTestSuiteChromosome x1 = (XMLTestSuiteChromosome) chromosome1;
            XMLTestSuiteChromosome x2 = (XMLTestSuiteChromosome) chromosome2;
            return (int) (getCumulativeFitness(x2) - getCumulativeFitness(x1));
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
