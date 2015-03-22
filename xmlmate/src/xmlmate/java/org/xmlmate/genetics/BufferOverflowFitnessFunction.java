package org.xmlmate.genetics;

import org.evosuite.Properties;

public class BufferOverflowFitnessFunction extends IntegerOverflowFitnessFunction {

    public BufferOverflowFitnessFunction() {
        assert Properties.POPULATION == 1 : "The BufferOverflowFitnessFunction can only be used with a singleton population!";
        // in this case the secondary objective will maximize the number of RIPs
        XMLTestSuiteChromosome.addSecondaryObjective(new MaximizeDivisionsSecondaryObjective());
        // this will favor greater distances
        XMLTestSuiteChromosome.addSecondaryObjective(new MaximizeCumulativeDistanceSecondaryObjective());
    }

}
