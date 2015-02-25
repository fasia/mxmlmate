package org.xmlmate.genetics;

import gnu.trove.map.TLongLongMap;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.procedure.TLongLongProcedure;

import java.io.File;
import java.util.Map;

import org.evosuite.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DivByZeroFitnessFunction extends BinaryBackendFitnessFunction {
	private static final long serialVersionUID = 2226177116546744577L;
	private static final Logger logger = LoggerFactory.getLogger(DivByZeroFitnessFunction.class);

	public DivByZeroFitnessFunction() {
		assert Properties.POPULATION == 1: "The DivByZeroFitnessFunction can only be used with a singleton population!";
	}
	
	@Override
	public double getFitness(XMLTestSuiteChromosome individual) {
		evaluationClock.start();
		
		final TLongLongMap mins = new TLongLongHashMap(20,0.68f,0,Long.MAX_VALUE);
		
		for (XMLTestChromosome x : individual.getTestChromosomes()) {
		    if (!x.isChanged()) {
		    	TLongLongMap cached = ((DistanceMapExecutionResult) x.getLastExecutionResult()).getDistances();
		    	cached.forEachEntry(new TLongLongProcedure() {
					@Override
					public boolean execute(long a, long b) {
						long prev = mins.get(a);
						if (b < prev)
							mins.put(a, b);
						return true;
					}
				});
		    }
		}
		Map<Integer, File> awaited = sendChangedChromosomes(individual.getTestChromosomes());
		
		logger.trace("Waiting for coverage of {} files", awaited.size());
		
		// TODO implement 
		
		evaluationClock.stop();
		return 0; // TODO return smallest value from mins
	}

	@Override
	public boolean isMaximizationFunction() {
		return false;
	}

}
