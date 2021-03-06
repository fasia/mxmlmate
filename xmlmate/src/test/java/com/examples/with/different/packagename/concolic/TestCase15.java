package com.examples.with.different.packagename.concolic;

import static org.evosuite.symbolic.Assertions.checkEquals;

public class TestCase15 {

	public static final double DOUBLE_CONSTANT = 1.0;

	/**
	 * @param args
	 */
	public static void test(double double0) {
		double double1 = 2.0;
		double double2 = StrictMath.atan2(double0, double1);
		double double3 = StrictMath.atan2(DOUBLE_CONSTANT, double1);

		double double4 = StrictMath.hypot(double0, double1);
		double double5 = StrictMath.hypot(DOUBLE_CONSTANT, double1);

		double double6 = Math.IEEEremainder(double0, double1);
		double double7 = Math.IEEEremainder(DOUBLE_CONSTANT, double1);

		double double8 = StrictMath.pow(double0, double1);
		double double9 = StrictMath.pow(DOUBLE_CONSTANT, double1);

		checkEquals(double2, double3);
		checkEquals(double4, double5);
		checkEquals(double6, double7);
		checkEquals(double8, double9);
	}

}
