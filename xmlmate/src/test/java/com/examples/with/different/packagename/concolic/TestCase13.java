package com.examples.with.different.packagename.concolic;

public class TestCase13 {

	public static final double DOUBLE_VALUE = Math.E;

	/**
	 * @param args
	 */
	public static void test(double double0) {
		double double1 = DOUBLE_VALUE;
		double double2 = StrictMath.cos(double0);
		double double3 = StrictMath.cos(double1);

		if (double2 != double3) {
			throw new RuntimeException();
		}

	}
}
