package org.evosuite.result;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.evosuite.Properties;
import org.evosuite.assertion.Assertion;
import org.evosuite.contracts.ContractViolation;
import org.evosuite.coverage.branch.Branch;
import org.evosuite.coverage.branch.BranchPool;
import org.evosuite.coverage.mutation.Mutation;
import org.evosuite.coverage.mutation.MutationPool;
import org.evosuite.coverage.mutation.MutationTimeoutStoppingCondition;
import org.evosuite.ga.GeneticAlgorithm;
import org.evosuite.instrumentation.LinePool;
import org.evosuite.result.TestGenerationResult.Status;
import org.evosuite.testcase.ExecutionResult;
import org.evosuite.testcase.TestCase;
import org.evosuite.utils.LoggingUtils;

public class TestGenerationResultBuilder {

	public static TestGenerationResult buildErrorResult(String errorMessage) {
		TestGenerationResultImpl result = new TestGenerationResultImpl();
		result.setStatus(Status.ERROR);
		result.setErrorMessage(errorMessage);
		getInstance().fillInformationFromConfiguration(result);
		getInstance().fillInformationFromTestData(result);
		getInstance().resetTestData();
		return result;
	}

	public static TestGenerationResult buildTimeoutResult() {
		TestGenerationResultImpl result = new TestGenerationResultImpl();
		result.setStatus(Status.TIMEOUT);
		getInstance().fillInformationFromConfiguration(result);
		getInstance().fillInformationFromTestData(result);
		getInstance().resetTestData();
		return result;
	}

	public static TestGenerationResult buildSuccessResult() {
		TestGenerationResultImpl result = new TestGenerationResultImpl();
		result.setStatus(Status.SUCCESS);
		getInstance().fillInformationFromConfiguration(result);
		getInstance().fillInformationFromTestData(result);
		getInstance().resetTestData();
		return result;
	}
	
	private static TestGenerationResultBuilder instance = null;
	
	private TestGenerationResultBuilder() {
		resetTestData();
	}
	
	public static TestGenerationResultBuilder getInstance() {
		if(instance == null)
			instance = new TestGenerationResultBuilder();
		
		return instance;
	}
	
	private void resetTestData() {
		code = "";
		ga = null;
		testCode.clear();
		testCases.clear();
		contractViolations.clear();
		uncoveredLines = LinePool.getAllLines();
		for(Branch b : BranchPool.getAllBranches()) {
			uncoveredBranches.add(new BranchInfo(b, true));
			uncoveredBranches.add(new BranchInfo(b, false));
		}
		for(Mutation m : MutationPool.getMutants()) {
			uncoveredMutants.add(new MutationInfo(m));
		}
	}
	
	private void fillInformationFromConfiguration(TestGenerationResultImpl result) {
		result.setClassUnderTest(Properties.TARGET_CLASS);
		result.setTargetCriterion(Properties.CRITERION.name());
	}
	
	private void fillInformationFromTestData(TestGenerationResultImpl result) {
		
		Set<MutationInfo> exceptionMutants = new LinkedHashSet<MutationInfo>();
		for(Mutation m : MutationPool.getMutants()) {
			if(MutationTimeoutStoppingCondition.isDisabled(m)) {
				MutationInfo info = new MutationInfo(m);
				exceptionMutants.add(info);
				uncoveredMutants.remove(info);
			}
		}
		
		for(String test : testCode.keySet()) {
			result.setTestCode(test, testCode.get(test));
			result.setTestCase(test, testCases.get(test));
			result.setContractViolations(test, contractViolations.get(test));
			result.setCoveredLines(test, testLineCoverage.get(test));
			result.setCoveredBranches(test, testBranchCoverage.get(test));
			result.setCoveredMutants(test, testMutantCoverage.get(test));
			result.setComment(test, testComments.get(test));
		}
		
		result.setUncoveredLines(uncoveredLines);
		result.setUncoveredBranches(uncoveredBranches);
		result.setUncoveredMutants(uncoveredMutants);
		result.setExceptionMutants(exceptionMutants);
		result.setTestSuiteCode(code);
		result.setGeneticAlgorithm(ga);
		result.setTargetCoverage(targetCoverage);
	}
	
	private String code = "";
	
	private GeneticAlgorithm<?> ga = null;
	
	private Map<String, String> testCode = new LinkedHashMap<String, String>();

	private Map<String, TestCase> testCases = new LinkedHashMap<String, TestCase>();
	
	private Map<String, String> testComments = new LinkedHashMap<String, String>();

	private Map<String, Set<Integer>> testLineCoverage = new LinkedHashMap<String, Set<Integer>>();

	private Map<String, Set<BranchInfo>> testBranchCoverage = new LinkedHashMap<String, Set<BranchInfo>>();

	private Map<String, Set<MutationInfo>> testMutantCoverage = new LinkedHashMap<String, Set<MutationInfo>>();

	private Map<String, Set<Failure>> contractViolations = new LinkedHashMap<String, Set<Failure>>();
	
	private Set<Integer> uncoveredLines = LinePool.getAllLines();
	
	private Set<BranchInfo> uncoveredBranches = new LinkedHashSet<BranchInfo>();

	private Set<MutationInfo> uncoveredMutants = new LinkedHashSet<MutationInfo>();

	private double targetCoverage = 0.0;
	
	public void setTestCase(String name, String code, TestCase testCase, String comment, ExecutionResult result) {
		testCode.put(name, code);
		testCases.put(name, testCase);
		Set<Failure> failures = new LinkedHashSet<Failure>();
		for(ContractViolation violation : testCase.getContractViolations()) {
			failures.add(new Failure(violation));
		}
		if(!Properties.CHECK_CONTRACTS && result.hasUndeclaredException()) {
			int position = result.getFirstPositionOfThrownException();
			Throwable exception = result.getExceptionThrownAtPosition(position);			
			failures.add(new Failure(exception, position, testCase));
		}
		contractViolations.put(name, failures);
		testComments.put(name, comment);
		testLineCoverage.put(name, result.getTrace().getCoveredLines());
		
		uncoveredLines.removeAll(result.getTrace().getCoveredLines());
		
		Set<BranchInfo> branchCoverage = new LinkedHashSet<BranchInfo>();
		for(int branchId : result.getTrace().getCoveredFalseBranches()) {
			Branch branch = BranchPool.getBranch(branchId);
			if(branch == null) {
                LoggingUtils.getEvoLogger().warn("Branch is null: {}", branchId);
				continue;
			}
			BranchInfo info = new BranchInfo(branch.getClassName(), branch.getMethodName(), branch.getInstruction().getLineNumber(), false);
			branchCoverage.add(info);
		}
		for(int branchId : result.getTrace().getCoveredTrueBranches()) {
			Branch branch = BranchPool.getBranch(branchId);
			if(branch == null) {
                LoggingUtils.getEvoLogger().warn("Branch is null: {}", branchId);
				continue;
			}
			BranchInfo info = new BranchInfo(branch.getClassName(), branch.getMethodName(), branch.getInstruction().getLineNumber(), true);
			branchCoverage.add(info);
		}
		testBranchCoverage.put(name, branchCoverage);
		uncoveredBranches.removeAll(branchCoverage);
		
		Set<MutationInfo> mutationCoverage = new LinkedHashSet<MutationInfo>();
		for(Assertion assertion : testCase.getAssertions()) {
			for(Mutation m : assertion.getKilledMutations()) {
				mutationCoverage.add(new MutationInfo(m));
			}
		}
		testMutantCoverage.put(name, mutationCoverage);
		uncoveredMutants.removeAll(mutationCoverage);
	}
	
	public void setTestSuiteCode(String code) {
		this.code = code;
	}
	
	public void setGeneticAlgorithm(GeneticAlgorithm<?> ga) {
		this.ga = ga;
		targetCoverage = ga.getBestIndividual().getCoverage();
	}
}
