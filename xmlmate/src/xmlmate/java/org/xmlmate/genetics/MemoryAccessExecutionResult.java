package org.xmlmate.genetics;

import gnu.trove.set.TLongSet;

import org.evosuite.testcase.ExecutionResult;

public class MemoryAccessExecutionResult extends ExecutionResult {
	private final TLongSet addresses;

	public TLongSet getAddresses() {
		return addresses;
	}

	public MemoryAccessExecutionResult(TLongSet set) {
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
