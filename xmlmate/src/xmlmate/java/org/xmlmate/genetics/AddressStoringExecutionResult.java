package org.xmlmate.genetics;

import org.evosuite.testcase.ExecutionResult;

public class AddressStoringExecutionResult extends ExecutionResult {
    private final long[] addresses;

    public long[] getAddresses() {
        return addresses;
    }

    public AddressStoringExecutionResult(long[] set) {
        super(null);
        addresses = set;
    }

    private AddressStoringExecutionResult(AddressStoringExecutionResult other) {
        this(other.addresses);
    }

    @Override
    public ExecutionResult clone() {
        return new AddressStoringExecutionResult(this);
    }

}
