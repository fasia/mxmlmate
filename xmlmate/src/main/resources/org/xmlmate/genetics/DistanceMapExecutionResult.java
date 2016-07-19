package org.xmlmate.genetics;

import gnu.trove.map.TLongLongMap;
import org.evosuite.testcase.ExecutionResult;

public class DistanceMapExecutionResult extends ExecutionResult {
    private final TLongLongMap distances;

    public TLongLongMap getDistances() {
        return distances;
    }

    public DistanceMapExecutionResult(TLongLongMap dist) {
        super(null);
        distances = dist;
    }

    private DistanceMapExecutionResult(DistanceMapExecutionResult other) {
        this(other.distances);
    }

    @Override
    public ExecutionResult clone() {
        return new DistanceMapExecutionResult(this);
    }

}
