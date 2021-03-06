/**
 * Copyright (C) 2011,2012 Gordon Fraser, Andrea Arcuri and EvoSuite
 * contributors
 * 
 * This file is part of EvoSuite.
 * 
 * EvoSuite is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 * 
 * EvoSuite is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Public License for more details.
 * 
 * You should have received a copy of the GNU Public License along with
 * EvoSuite. If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Gordon Fraser
 */
package org.evosuite.symbolic.expr.fp;

import java.util.HashSet;
import java.util.Set;

import org.evosuite.Properties;
import org.evosuite.symbolic.ConstraintTooLongException;
import org.evosuite.symbolic.DSEStats;
import org.evosuite.symbolic.expr.AbstractExpression;
import org.evosuite.symbolic.expr.Expression;
import org.evosuite.symbolic.expr.Operator;
import org.evosuite.symbolic.expr.UnaryExpression;
import org.evosuite.symbolic.expr.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RealUnaryExpression extends AbstractExpression<Double> implements
        RealValue, UnaryExpression<Double> {

	private static final long serialVersionUID = 9086637495150131445L;

	protected static final Logger log = LoggerFactory.getLogger(RealUnaryExpression.class);

	private final Operator op;

	private final Expression<Double> expr;

	/**
	 * <p>
	 * Constructor for RealUnaryExpression.
	 * </p>
	 * 
	 * @param e
	 *            a {@link org.evosuite.symbolic.expr.Expression} object.
	 * @param op2
	 *            a {@link org.evosuite.symbolic.expr.Operator} object.
	 * @param con
	 *            a {@link java.lang.Double} object.
	 */
	public RealUnaryExpression(Expression<Double> e, Operator op2, Double con) {
		super(con, 1 + e.getSize(), e.containsSymbolicVariable());
		this.expr = e;
		this.op = op2;

		if (getSize() > Properties.DSE_CONSTRAINT_LENGTH) {
			DSEStats.reportConstraintTooLong(getSize());
			throw new ConstraintTooLongException(getSize());
		}
	}

	/** {@inheritDoc} */
	@Override
	public Expression<Double> getOperand() {
		return expr;
	}

	/** {@inheritDoc} */
	@Override
	public Operator getOperator() {
		return op;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return op.toString() + "(" + expr + ")";
	}

	/** {@inheritDoc} */
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof RealUnaryExpression) {
			RealUnaryExpression v = (RealUnaryExpression) obj;
			return this.op.equals(v.op) && this.getSize() == v.getSize()
			        && this.expr.equals(v.expr);
		}
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public Double execute() {
		double leftVal = expr.execute();

		switch (op) {

		case ABS:
			return Math.abs(leftVal);
		case ACOS:
			return StrictMath.acos(leftVal);
		case ASIN:
			return StrictMath.asin(leftVal);
		case ATAN:
			return StrictMath.atan(leftVal);
		case CBRT:
			return StrictMath.cbrt(leftVal);
		case CEIL:
			return Math.ceil(leftVal);
		case COS:
			return StrictMath.cos(leftVal);
		case COSH:
			return StrictMath.cosh(leftVal);
		case EXP:
			return StrictMath.exp(leftVal);
		case EXPM1:
			return StrictMath.expm1(leftVal);
		case FLOOR:
			return Math.floor(leftVal);
		case LOG:
			return StrictMath.log(leftVal);
		case LOG10:
			return StrictMath.log10(leftVal);
		case LOG1P:
			return StrictMath.log1p(leftVal);
		case NEG:
			return -leftVal;
		case NEXTUP:
			return Math.nextUp(leftVal);
		case RINT:
			return Math.rint(leftVal);
		case SIGNUM:
			return Math.signum(leftVal);
		case SIN:
			return StrictMath.sin(leftVal);
		case SINH:
			return StrictMath.sinh(leftVal);
		case SQRT:
			return Math.sqrt(leftVal);
		case TAN:
			return StrictMath.tan(leftVal);
		case TANH:
			return StrictMath.tanh(leftVal);
		case TODEGREES:
			return Math.toDegrees(leftVal);
		case TORADIANS:
			return Math.toRadians(leftVal);
		case ULP:
			return Math.ulp(leftVal);

		default:
            log.warn("RealUnaryExpression: unimplemented operator: {}", op);
			return null;
		}
	}

	@Override
	public Set<Variable<?>> getVariables() {
		Set<Variable<?>> variables = new HashSet<Variable<?>>();
		variables.addAll(this.expr.getVariables());
		return variables;
	}

	@Override
	public int hashCode() {
		return this.op.hashCode() + this.getSize() + this.expr.hashCode();
	}

	@Override
	public Set<Object> getConstants() {
		return expr.getConstants();
	}
}
