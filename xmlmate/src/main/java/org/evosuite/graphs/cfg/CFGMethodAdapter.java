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
 */
package org.evosuite.graphs.cfg;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.evosuite.Properties;
import org.evosuite.Properties.Criterion;
import org.evosuite.coverage.branch.BranchPool;
import org.evosuite.instrumentation.AnnotatedMethodNode;
import org.evosuite.instrumentation.coverage.BranchInstrumentation;
import org.evosuite.instrumentation.coverage.DefUseInstrumentation;
import org.evosuite.instrumentation.coverage.LCSAJsInstrumentation;
import org.evosuite.instrumentation.coverage.MethodInstrumentation;
import org.evosuite.instrumentation.coverage.MutationInstrumentation;
import org.evosuite.instrumentation.coverage.PrimePathInstrumentation;
import org.evosuite.reset.ClassResetter;
import org.evosuite.setup.DependencyAnalysis;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create a minimized control flow graph for the method and store it. In
 * addition, this adapter also adds instrumentation for branch distance
 * measurement
 * 
 * defUse, concurrency and LCSAJs instrumentation is also added (if the
 * properties are set).
 * 
 * @author Gordon Fraser
 */
public class CFGMethodAdapter extends MethodVisitor {

	private static final Logger logger = LoggerFactory.getLogger(CFGMethodAdapter.class);

	/**
	 * A list of Strings representing method signatures. Methods matching those
	 * signatures are not instrumented and no CFG is generated for them. Except
	 * if some MethodInstrumentation requests it.
	 */
	public static final List<String> EXCLUDE = Arrays.asList("<clinit>()V",
																ClassResetter.STATIC_RESET+"()V",
																ClassResetter.STATIC_RESET);
	/**
	 * The set of all methods which can be used during test case generation This
	 * excludes e.g. synthetic, initializers, private and deprecated methods
	 */
	public static Map<String, Set<String>> methods = new HashMap<String, Set<String>>();

	/**
	 * This is the name + the description of the method. It is more like the
	 * signature and less like the name. The name of the method can be found in
	 * this.plain_name
	 */
	private final String methodName;

	private final MethodVisitor next;
	private final String plain_name;
	private final int access;
	private final String className;
	private final ClassLoader classLoader;

	private int lineNumber = 0;
	
	/** Can be set by annotation */
	private boolean excludeMethod = false;

	/**
	 * <p>
	 * Constructor for CFGMethodAdapter.
	 * </p>
	 * 
	 * @param className
	 *            a {@link java.lang.String} object.
	 * @param access
	 *            a int.
	 * @param name
	 *            a {@link java.lang.String} object.
	 * @param desc
	 *            a {@link java.lang.String} object.
	 * @param signature
	 *            a {@link java.lang.String} object.
	 * @param exceptions
	 *            an array of {@link java.lang.String} objects.
	 * @param mv
	 *            a {@link org.objectweb.asm.MethodVisitor} object.
	 */
	public CFGMethodAdapter(ClassLoader classLoader, String className, int access,
	        String name, String desc, String signature, String[] exceptions,
	        MethodVisitor mv) {

		// super(new MethodNode(access, name, desc, signature, exceptions),
		// className,
		// name.replace('/', '.'), null, desc);

		super(Opcodes.ASM4, new AnnotatedMethodNode(access, name, desc, signature,
		        exceptions));

		this.next = mv;
		this.className = className; // .replace('/', '.');
		this.access = access;
		this.methodName = name + desc;
		this.plain_name = name;
		this.classLoader = classLoader;
	}

	/* (non-Javadoc)
	 * @see org.objectweb.asm.MethodVisitor#visitLineNumber(int, org.objectweb.asm.Label)
	 */
	@Override
	public void visitLineNumber(int line, Label start) {
		lineNumber = line;
		super.visitLineNumber(line, start);
	}
	
	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		if("Lorg/evosuite/annotation/EvoSuiteExclude;".equals(desc)) {
            logger.info("Method has EvoSuite annotation: {}", desc);
			excludeMethod = true;
		}
		return super.visitAnnotation(desc, visible);
	}

	/** {@inheritDoc} */
	@Override
	public void visitEnd() {
        logger.debug("Creating CFG of {}.{}", className, methodName);
		boolean isExcludedMethod = excludeMethod || EXCLUDE.contains(methodName);
		boolean isMainMethod = plain_name.equals("main") && Modifier.isStatic(access);

		List<MethodInstrumentation> instrumentations = new ArrayList<MethodInstrumentation>();
		if (DependencyAnalysis.shouldInstrument(className, methodName)) {
			if (Properties.CRITERION == Criterion.LCSAJ) {
				instrumentations.add(new LCSAJsInstrumentation());
				instrumentations.add(new BranchInstrumentation());
			} else if (Properties.CRITERION == Criterion.DEFUSE
			        || Properties.CRITERION == Criterion.ALLDEFS) {
				instrumentations.add(new BranchInstrumentation());
				instrumentations.add(new DefUseInstrumentation());
			} else if (Properties.CRITERION == Criterion.PATH) {
				instrumentations.add(new PrimePathInstrumentation());
				instrumentations.add(new BranchInstrumentation());
			} else if (Properties.CRITERION == Criterion.MUTATION
			        || Properties.CRITERION == Criterion.WEAKMUTATION
			        || Properties.CRITERION == Criterion.STRONGMUTATION) {
				instrumentations.add(new BranchInstrumentation());
				instrumentations.add(new MutationInstrumentation());
			} else {
				instrumentations.add(new BranchInstrumentation());
			}
		} else {
			//instrumentations.add(new BranchInstrumentation());
		}

		boolean executeOnMain = false;
		boolean executeOnExcluded = false;

		for (MethodInstrumentation instrumentation : instrumentations) {
			executeOnMain = executeOnMain || instrumentation.executeOnMainMethod();
			executeOnExcluded = executeOnExcluded
			        || instrumentation.executeOnExcludedMethods();
		}

		// super.visitEnd();
		// Generate CFG of method
		MethodNode mn = (AnnotatedMethodNode) mv;

		boolean checkForMain = false;
		if (Properties.CONSIDER_MAIN_METHODS) {
			checkForMain = true;
		} else {
			checkForMain = !isMainMethod || executeOnMain;
		}

		// Only instrument if the method is (not main and not excluded) or (the
		// MethodInstrumentation wants it anyway)
		if (checkForMain && (!isExcludedMethod || executeOnExcluded)
		        && (access & Opcodes.ACC_ABSTRACT) == 0
		        && (access & Opcodes.ACC_NATIVE) == 0) {

            logger.info("Analyzing method {} in class {}", methodName, className);

			// MethodNode mn = new CFGMethodNode((MethodNode)mv);
			// System.out.println("Generating CFG for "+ className+"."+mn.name +
			// " ("+mn.desc +")");

			BytecodeAnalyzer bytecodeAnalyzer = new BytecodeAnalyzer();
            logger.info("Generating CFG for method {}", methodName);

			try {

				bytecodeAnalyzer.analyze(classLoader, className, methodName, mn);
                logger.trace("Method graph for {}.{} contains {} nodes for {} instructions", className, methodName, bytecodeAnalyzer.retrieveCFGGenerator().getRawGraph().vertexSet().size(), bytecodeAnalyzer.getFrames().length);
				// compute Raw and ActualCFG and put both into GraphPool
				bytecodeAnalyzer.retrieveCFGGenerator().registerCFGs();
                logger.info("Created CFG for method {}", methodName);

				if (DependencyAnalysis.shouldInstrument(className, methodName)) {
					if (!methods.containsKey(className))
						methods.put(className, new HashSet<String>());

					// add the actual instrumentation
                    logger.info("Instrumenting method {} in class {}", methodName, className);
					for (MethodInstrumentation instrumentation : instrumentations)
						instrumentation.analyze(classLoader, mn, className, methodName,
						                        access);

					handleBranchlessMethods();
					String id = className + "." + methodName;
					if (isUsable()) {
						methods.get(className).add(id);
                        logger.debug("Counting: {}", id);
					}
				}
			} catch (AnalyzerException e) {
                logger.error("Analyzer exception while analyzing {}.{}: {}", className, methodName, e);
				e.printStackTrace();
			}

		} else {
            logger.debug("NOT Creating CFG of {}.{}: {}, {}, {}, {}", className, methodName, checkForMain, !isExcludedMethod || executeOnExcluded, (access & Opcodes.ACC_ABSTRACT) == 0, (access & Opcodes.ACC_NATIVE) == 0);
		}
		mn.accept(next);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.objectweb.asm.commons.LocalVariablesSorter#visitMaxs(int, int)
	 */
	/** {@inheritDoc} */
	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
		int maxNum = 7;
		super.visitMaxs(Math.max(maxNum, maxStack), maxLocals);
	}

	private void handleBranchlessMethods() {
		String id = className + "." + methodName;
		if (BranchPool.getNonArtificialBranchCountForMethod(className, methodName) == 0) {
			if (isUsable()) {
                logger.debug("Method has no branches: {}", id);
				BranchPool.addBranchlessMethod(className, id, lineNumber);
			}
		}
	}

	/**
	 * See description of CFGMethodAdapter.EXCLUDE
	 * 
	 * @return
	 */
	private boolean isUsable() {
		return !((this.access & Opcodes.ACC_SYNTHETIC) != 0
		        || (this.access & Opcodes.ACC_BRIDGE) != 0 || (this.access & Opcodes.ACC_NATIVE) != 0)
		        && !methodName.contains("<clinit>")
		        && !(methodName.contains("<init>") && (access & Opcodes.ACC_PRIVATE) == Opcodes.ACC_PRIVATE)
		        && (Properties.USE_DEPRECATED || (access & Opcodes.ACC_DEPRECATED) != Opcodes.ACC_DEPRECATED);
	}

	/**
	 * Returns a set with all unique methodNames of methods.
	 * 
	 * @return A set with all unique methodNames of methods.
	 * @param className
	 *            a {@link java.lang.String} object.
	 */
	public static Set<String> getMethods(String className) {
		Set<String> targetMethods = new HashSet<String>();
		for (String currentClass : methods.keySet()) {
			if (currentClass.equals(className)
			        || currentClass.startsWith(className + "$"))
				targetMethods.addAll(methods.get(currentClass));
		}

		return targetMethods;
	}

	/**
	 * Returns a set with all unique methodNames of methods.
	 * 
	 * @return A set with all unique methodNames of methods.
	 */
	public static Set<String> getMethods() {
		Set<String> targetMethods = new HashSet<String>();
		for (String currentClass : methods.keySet()) {
			targetMethods.addAll(methods.get(currentClass));
		}

		return targetMethods;
	}

	/**
	 * Returns a set with all unique methodNames of methods.
	 * 
	 * @return A set with all unique methodNames of methods.
	 * @param className
	 *            a {@link java.lang.String} object.
	 */
	public static Set<String> getMethodsPrefix(String className) {
		Set<String> matchingMethods = new HashSet<String>();

		for (String name : methods.keySet()) {
			if (name.startsWith(className)) {
				matchingMethods.addAll(methods.get(name));
			}
		}

		return matchingMethods;
	}

	/**
	 * Returns a set with all unique methodNames of methods.
	 * 
	 * @return A set with all unique methodNames of methods.
	 * @param className
	 *            a {@link java.lang.String} object.
	 */
	public static int getNumMethodsPrefix(String className) {
		int num = 0;

		for (String name : methods.keySet()) {
			if (name.startsWith(className)) {
				num += methods.get(name).size();
			}
		}

		return num;
	}

	/**
	 * Returns a set with all unique methodNames of methods.
	 * 
	 * @return A set with all unique methodNames of methods.
	 */
	public static int getNumMethods() {
		int num = 0;

		for (String name : methods.keySet()) {
			num += methods.get(name).size();
		}

		return num;
	}

	/**
	 * Returns a set with all unique methodNames of methods.
	 * 
	 * @return A set with all unique methodNames of methods.
	 * @param className
	 *            a {@link java.lang.String} object.
	 */
	public static int getNumMethodsMemberClasses(String className) {
		int num = 0;

		for (String name : methods.keySet()) {
			if (name.equals(className) || name.startsWith(className + "$")) {
				num += methods.get(name).size();
			}
		}

		return num;
	}
}
