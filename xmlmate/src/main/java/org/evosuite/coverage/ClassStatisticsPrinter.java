/**
 * 
 */
package org.evosuite.coverage;

import java.util.Arrays;

import org.evosuite.Properties;
import org.evosuite.TestGenerationContext;
import org.evosuite.TestSuiteGenerator;
import org.evosuite.classpath.ClassPathHandler;
import org.evosuite.sandbox.Sandbox;
import org.evosuite.setup.DependencyAnalysis;
import org.evosuite.utils.LoggingUtils;

/**
 * @author Gordon Fraser
 * 
 */
public class ClassStatisticsPrinter {

	private static void reinstrument(Properties.Criterion criterion) {
		Properties.Criterion oldCriterion = Properties.CRITERION;
		if (oldCriterion == criterion)
			return;

		Properties.CRITERION = criterion;
		TestGenerationContext.getInstance().resetContext();
		// Need to load class explicitly in case there are no test cases.
		// If there are tests, then this is redundant
		Properties.getTargetClass(false);
	}

	private final static Properties.Criterion[] criteria = { Properties.Criterion.BRANCH,
	         Properties.Criterion.WEAKMUTATION,
	        Properties.Criterion.STATEMENT };
	// Properties.Criterion.DEFUSE is currently experimental

	/**
	 * Identify all JUnit tests starting with the given name prefix, instrument
	 * and run tests
	 */
	public static void printClassStatistics() {
		Sandbox.goingToExecuteSUTCode();
		Sandbox.goingToExecuteUnsafeCodeOnSameThread();
		try {
			// Load SUT without initialising it
			Class<?> targetClass = Properties.getTargetClass(false);
			if(targetClass != null) {
				//DependencyAnalysis.analyze(Properties.TARGET_CLASS,
				//		Arrays.asList(ClassPathHandler.getInstance().getClassPathElementsForTargetProject()));
				LoggingUtils.getEvoLogger().info("* Finished analyzing classpath");
			} else {
				LoggingUtils.getEvoLogger().info("* Error while initializing target class, not continuing");
			}
		} catch (Throwable e) {
            LoggingUtils.getEvoLogger().error("* Error while initializing target class: {}", e.getMessage() != null ? e.getMessage()
                    : e.toString());
			return;
		} finally {
			Sandbox.doneWithExecutingUnsafeCodeOnSameThread();
			Sandbox.doneWithExecutingSUTCode();
		}
		for (Properties.Criterion criterion : criteria) {
			reinstrument(criterion);
			TestFitnessFactory<?> factory = TestSuiteGenerator.getFitnessFactory();
			int numGoals = factory.getCoverageGoals().size();
            LoggingUtils.getEvoLogger().info("* Criterion {}: {}", criterion, numGoals);
		}

	}

}
