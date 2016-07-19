package org.xmlmate.execution;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.evosuite.coverage.branch.BranchCoverageSuiteFitness;
import org.evosuite.coverage.exception.ExceptionCoverageSuiteFitness;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.testcase.ExecutableChromosome;
import org.evosuite.testcase.ExecutionResult;
import org.evosuite.testsuite.AbstractTestSuiteChromosome;
import org.evosuite.testsuite.TestSuiteFitnessFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoExceptionCoverageSuiteFitness extends TestSuiteFitnessFunction {



	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/**
	 * 
	 */
	
	private static Logger logger = LoggerFactory
			.getLogger(NoExceptionCoverageSuiteFitness.class);
	
	protected TestSuiteFitnessFunction baseFF;
	
	public NoExceptionCoverageSuiteFitness() {
		baseFF = new BranchCoverageSuiteFitness();
	}
	@Override
	public boolean isMaximizationFunction() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public double getFitness(AbstractTestSuiteChromosome<? extends ExecutableChromosome> suite) {
		// TODO Auto-generated method stub
		
		Map<String, Set<Class<?>>> NoExceptions = new HashMap<String, Set<Class<?>>>();
		
		List<ExecutionResult> results = runTestSuite(suite);
		
		return 0;
	}

}
