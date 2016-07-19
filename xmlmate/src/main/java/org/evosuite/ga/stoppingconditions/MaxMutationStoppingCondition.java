package org.evosuite.ga.stoppingconditions;

import org.xmlmate.execution.XMLTestRunner;


// Author Faezeh Siavashi

public class MaxMutationStoppingCondition extends StoppingConditionImpl{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	protected static long maxmut = 0; 

	@Override
	public void forceCurrentValue(long value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public long getCurrentValue() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getLimit() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean isFinished() {
		// TODO Auto-generated method stub
		return (XMLTestRunner.totalmut>=maxmut);
	}

	@Override
	public void reset() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setLimit(long limit) {
		maxmut = limit;		
	}

}
