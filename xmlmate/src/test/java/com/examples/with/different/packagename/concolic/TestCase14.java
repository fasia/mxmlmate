package com.examples.with.different.packagename.concolic;

public class TestCase14 {

	public static final double DOUBLE_VALUE = 0.222D;

	/**
	 * @param args
	 */
	public static void test(double double1) {
		double double2 = StrictMath.acos(double1);
		double double3 = StrictMath.asin(double2);
		double double4 = StrictMath.atan(double2);
		double double5 = StrictMath.cbrt(double2);
		double double6 = Math.ceil(double2);
		double double7 = StrictMath.cos(double2);
		double double8 = StrictMath.cosh(double2);
		double double9 = StrictMath.exp(double2);
		double double10 = StrictMath.expm1(double2);
		double double11 = Math.floor(double2);
		double double12 = StrictMath.log(double2);
		double double13 = StrictMath.log10(double2);
		double double14 = StrictMath.log1p(double2);
		double double15 = Math.rint(double2);
		double double16 = StrictMath.sin(double2);
		double double17 = StrictMath.sinh(double2);
		double double18 = Math.sqrt(double2);
		double double19 = StrictMath.tan(double2);
		double double20 = StrictMath.tanh(double2);
		double double21 = Math.toDegrees(double2);
		double double22 = Math.toRadians(double2);

		if (double1 != 0.222)
			return;
		if (double2 != 1.3469311497286958)
			return;
		if (double3 == Double.NaN) // NaN always !=NaN
			return;
		if (double4 != 0.932158649922364)
			return;
		if (double5 != 1.1043713519090836)
			return;
		if (double6 != 2.0)
			return;
		if (double7 != 0.22199999999999995)
			return;
		if (double8 != 2.052821433618708)
			return;
		if (double9 != 3.845605815055685)
			return;
		if (double10 != 2.845605815055685)
			return;
		if (double11 != 1.0)
			return;
		if (double12 != 0.297828782334713D)
			return;
		if (double13 != 0.12934539671993053) // -INF
			return;
		if (double14 != 0.8531085810013106)
			return;
		if (double15 != 1.0)
			return;
		if (double16 != 0.975046665549911)
			return;
		if (double17 != 1.7927843814369775)
			return;
		if (double18 != 1.160573629602489)
			return;
		if (double19 != 4.392102097071672)
			return;
		if (double20 != 0.8733269986745328)
			return;
		if (double21 != 77.17347017415783)
			return;
		if (double22 != 0.023508383360438468)
			return;

		return;
	}

}
