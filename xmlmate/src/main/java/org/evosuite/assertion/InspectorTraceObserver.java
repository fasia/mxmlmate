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
package org.evosuite.assertion;

import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.evosuite.testcase.PrimitiveStatement;
import org.evosuite.testcase.Scope;
import org.evosuite.testcase.StatementInterface;
import org.evosuite.testcase.VariableReference;

public class InspectorTraceObserver extends AssertionTraceObserver<InspectorTraceEntry> {

	private static Pattern addressPattern = Pattern.compile(".*[\\w+\\.]+@[abcdef\\d]+.*", Pattern.MULTILINE);
	
	private final InspectorManager manager = InspectorManager.getInstance();

	/* (non-Javadoc)
	 * @see org.evosuite.assertion.AssertionTraceObserver#visit(org.evosuite.testcase.StatementInterface, org.evosuite.testcase.Scope, org.evosuite.testcase.VariableReference)
	 */
	/** {@inheritDoc} */
	@Override
	protected void visit(StatementInterface statement, Scope scope, VariableReference var) {
		// TODO: Check the variable class is complex?

		// We don't want inspector checks on string constants
		StatementInterface declaringStatement = currentTest.getStatement(var.getStPosition());
		if (declaringStatement instanceof PrimitiveStatement<?>)
			return;

		if (var.isPrimitive() || var.isString() || var.isWrapperType())
			return;

        logger.debug("Checking for inspectors of {} at statement {}", var, statement.getPosition());
		List<Inspector> inspectors = manager.getInspectors(var.getVariableClass());

		InspectorTraceEntry entry = new InspectorTraceEntry(var);

		for (Inspector i : inspectors) {

			// No inspectors from java.lang.Object
			if (i.getMethod().getDeclaringClass().equals(Object.class))
				continue;

			try {
				Object target = var.getObject(scope);
				if (target != null) {
					Object value = i.getValue(target);
                    logger.debug("Inspector {} is: {}", i.getMethodCall(), value);

					// We need no assertions that include the memory location
					if (value instanceof String) {
						// String literals may not be longer than 32767
						if(((String)value).length() >= 32767)
							continue;
						if(addressPattern.matcher((String)value).find())
							continue;
					}

					entry.addValue(i, value);
				}
			} catch (Exception e) {
				if (e instanceof TimeoutException) {
                    logger.debug("Timeout during inspector call - deactivating inspector {}", i.getMethodCall());
					manager.removeInspector(var.getVariableClass(), i);
				}
                logger.debug("Exception {} / {}", e, e.getCause());
				if (e.getCause() != null
				        && !e.getCause().getClass().equals(NullPointerException.class)) {
                    logger.debug("Exception during call to inspector: {} - {}", e, e.getCause());
				}
			}
		}
        logger.debug("Found {} inspectors for {} at statement {}", entry.size(), var, statement.getPosition());

		trace.addEntry(statement.getPosition(), var, entry);

	}
}
