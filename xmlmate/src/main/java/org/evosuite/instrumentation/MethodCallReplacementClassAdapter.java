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
/**
 * 
 */
package org.evosuite.instrumentation;

import org.evosuite.Properties;
import org.evosuite.runtime.MockList;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>MethodCallReplacementClassAdapter class.</p>
 *
 * @author fraser
 */
public class MethodCallReplacementClassAdapter extends ClassVisitor {

	private final String className;
	
	private String superClassName;
	
	private boolean definesHashCode = false;
	
	private boolean isInterface = false;

	private boolean definesUid = false;

	/**
	 * <p>Constructor for MethodCallReplacementClassAdapter.</p>
	 *
	 * @param cv a {@link org.objectweb.asm.ClassVisitor} object.
	 * @param className a {@link java.lang.String} object.
	 */
	public MethodCallReplacementClassAdapter(ClassVisitor cv, String className) {
		super(Opcodes.ASM4, cv);
		this.className = className;
		this.superClassName = null;
	}

	/* (non-Javadoc)
	 * @see org.objectweb.asm.ClassVisitor#visitMethod(int, java.lang.String, java.lang.String, java.lang.String, java.lang.String[])
	 */
	/** {@inheritDoc} */
	@Override
	public MethodVisitor visitMethod(int access, String name, String desc,
	        String signature, String[] exceptions) {
		if(name.equals("hashCode"))
			definesHashCode = true;
		
		MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
		if(name.equals("<init>")) {			
			mv = new RegisterObjectForDeterministicHashCodeVisitor(mv, access, name, desc);
		}

		return new MethodCallReplacementMethodAdapter(mv, className, superClassName, name, access, desc);
	}
	
	@Override
	public FieldVisitor visitField(int access, String name, String desc,
			String signature, Object value) {
		if(name.equals("serialVersionUID")) {
			definesUid = true;
		}
		return super.visitField(access, name, desc, signature, value);
	}
	
	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		String superNameWithDots = superName.replace('/', '.');
		superClassName = superNameWithDots;
		if((access & Opcodes.ACC_INTERFACE) == Opcodes.ACC_INTERFACE)
			isInterface = true;
		
		if(MockList.shouldBeMocked(superNameWithDots)) {
			Class<?> mockSuperClass = MockList.getMockClass(superNameWithDots);
			String mockSuperClassName = mockSuperClass.getCanonicalName().replace('.', '/');
			
			super.visit(version, access, name, signature, mockSuperClassName, interfaces);
		} else {
			super.visit(version, access, name, signature, superName, interfaces);
		}
	}
	
	private static final Logger logger = LoggerFactory.getLogger(MethodCallReplacementClassAdapter.class);
	
	@Override
	public void visitEnd() {
		if(!definesHashCode && !isInterface && Properties.REPLACE_CALLS) {
            logger.info("No hashCode defined for: {}, superclass = {}", className, superClassName);
			if(superClassName.equals("java.lang.Object")) {
				Method hashCodeMethod = Method.getMethod("int hashCode()");
				GeneratorAdapter mg = new GeneratorAdapter(Opcodes.ACC_PUBLIC, hashCodeMethod, null, null, this);
				mg.loadThis();
				mg.visitAnnotation("Lorg/evosuite/annotation/EvoSuiteExclude;", true);
				mg.invokeStatic(Type.getType(org.evosuite.runtime.System.class), Method.getMethod("int identityHashCode(Object)"));
				mg.returnValue();
				mg.endMethod();
				
				/*
				 * If the class is serializable, then adding a hashCode will change the serialVersionUID
				 * if it is not defined in the class. Hence, if it is not defined, we have to define it to
				 * avoid problems in serialising the class.
				 */
				/*
				if(!definesUid) {
					try {
						Class<?> clazz = Class.forName(className.replace('/', '.'), false, MethodCallReplacementClassAdapter.class.getClassLoader());
						if(Serializable.class.isAssignableFrom(clazz)) {
						ObjectStreamClass c = ObjectStreamClass.lookup(clazz);
						long serialID = c.getSerialVersionUID();
						logger.info("Adding serialId to class "+className);
						visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "serialVersionUID", "J", null, serialID);
						}
					} catch(ClassNotFoundException e) {
						logger.info("Failed to add serialId to class "+className+": "+e.getMessage());
					}
				*/
			}
		}
		super.visitEnd();
	}
}
