package org.xmlmate.genetics;

import gnu.trove.map.TLongObjectMap;

import org.evosuite.testcase.ExecutionResult;

public class BasicBlockSuccessionMapExecutionResult extends ExecutionResult {
	private final TLongObjectMap<long[]> successions;

	public TLongObjectMap<long[]> getSuccessions() {
		return successions;
	}

	public BasicBlockSuccessionMapExecutionResult(TLongObjectMap<long[]> dist) {
		super(null);
		successions = dist;
	}

	private BasicBlockSuccessionMapExecutionResult(
			BasicBlockSuccessionMapExecutionResult other) {
		this(other.successions);
	}

	@Override
	public ExecutionResult clone() {
		return new BasicBlockSuccessionMapExecutionResult(this);
	}

}
