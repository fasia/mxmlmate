package org.xmlmate.genetics;

import gnu.trove.map.TLongObjectMap;
import gnu.trove.set.TLongSet;

import org.evosuite.testcase.ExecutionResult;

public class BasicBlockSuccessionMapExecutionResult extends ExecutionResult {
	private final TLongObjectMap<TLongSet> successions;

	public TLongObjectMap<TLongSet> getSuccessions() {
		return successions;
	}

	public BasicBlockSuccessionMapExecutionResult(TLongObjectMap<TLongSet> dist) {
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
