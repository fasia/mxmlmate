package org.xmlmate.genetics;

import org.evosuite.testcase.ExecutionResult;

public class MemoryAccessExecutionResult extends ExecutionResult {
	private final long[] addresses;

	public long[] getAddresses() {
		return addresses;
	}

	public MemoryAccessExecutionResult(long[] set) {
		super(null);
		addresses = set;
	}
	
	private MemoryAccessExecutionResult(MemoryAccessExecutionResult other) {
		this(other.addresses);
	}
	
	@Override
	public ExecutionResult clone() {
		return new MemoryAccessExecutionResult(this);
	}

}
