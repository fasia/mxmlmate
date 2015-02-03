package org.xmlmate.genetics;

import gnu.trove.set.TIntSet;

import org.evosuite.testcase.ExecutionResult;

public class MemoryAccessExecutionResult extends ExecutionResult {
	private final TIntSet addresses;

	public TIntSet getAddresses() {
		return addresses;
	}

	public MemoryAccessExecutionResult(TIntSet set) {
		super(null);
		addresses = set;
	}
	
}
