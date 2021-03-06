package org.evosuite.symbolic.vm.string;

import java.util.ArrayList;

import org.evosuite.symbolic.expr.Expression;
import org.evosuite.symbolic.expr.Operator;
import org.evosuite.symbolic.expr.bv.IntegerConstant;
import org.evosuite.symbolic.expr.bv.IntegerValue;
import org.evosuite.symbolic.expr.bv.StringMultipleComparison;
import org.evosuite.symbolic.expr.str.StringValue;
import org.evosuite.symbolic.vm.NonNullReference;
import org.evosuite.symbolic.vm.SymbolicEnvironment;
import org.evosuite.symbolic.vm.SymbolicFunction;
import org.evosuite.symbolic.vm.SymbolicHeap;

public final class RegionMatches5 extends SymbolicFunction {

	private static final String REGION_MATCHES = "regionMatches";

	public RegionMatches5(SymbolicEnvironment env) {
		super(env, Types.JAVA_LANG_STRING, REGION_MATCHES,
				Types.INT_STR_INT_INT_TO_BOOL_DESCRIPTOR);
	}

	@Override
	public Object executeFunction() {

		NonNullReference symb_receiver = (NonNullReference) this
				.getSymbReceiver();
		String conc_receiver = (String) this.getConcReceiver();
		StringValue stringReceiverExpr = env.heap.getField(
				Types.JAVA_LANG_STRING, SymbolicHeap.$STRING_VALUE,
				conc_receiver, symb_receiver, conc_receiver);

		IntegerValue ignoreCaseExpr = new IntegerConstant(0);
		IntegerValue toffsetExpr = this.getSymbIntegerArgument(0);

		NonNullReference symb_other = (NonNullReference) this
				.getSymbArgument(1);
		String conc_other = (String) this.getConcArgument(1);
		StringValue otherExpr = env.heap.getField(Types.JAVA_LANG_STRING,
				SymbolicHeap.$STRING_VALUE, conc_other, symb_other, conc_other);
		IntegerValue ooffsetExpr = this.getSymbIntegerArgument(2);
		IntegerValue lenExpr = this.getSymbIntegerArgument(3);

		boolean res = this.getConcBooleanRetVal();
		if (stringReceiverExpr.containsSymbolicVariable()
				|| ignoreCaseExpr.containsSymbolicVariable()
				|| toffsetExpr.containsSymbolicVariable()
				|| otherExpr.containsSymbolicVariable()
				|| ooffsetExpr.containsSymbolicVariable()
				|| lenExpr.containsSymbolicVariable()) {

			ArrayList<Expression<?>> other = new ArrayList<Expression<?>>();
			other.add(toffsetExpr);
			other.add(ooffsetExpr);
			other.add(lenExpr);
			other.add(ignoreCaseExpr);
			int conV = res ? 1 : 0;

			StringMultipleComparison strComp = new StringMultipleComparison(
					stringReceiverExpr, Operator.REGIONMATCHES, otherExpr,
					other, (long) conV);

			return strComp;
		}

		return this.getSymbIntegerRetVal();
	}

}
