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
package org.evosuite.utils;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.xerces.xs.XSElementDeclaration;
import org.evosuite.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unique random number accessor
 * 
 * @author Gordon Fraser
 */
public class Randomness implements Serializable {

	private static final long serialVersionUID = -5934455398558935937L;

	private static final Logger logger = LoggerFactory.getLogger(Randomness.class);

	private static long seed = 0;

	private static Randomness instance = new Randomness();

	private Randomness() {
	}

	/**
	 * <p>
	 * Getter for the field <code>instance</code>.
	 * </p>
	 * 
	 * @return a {@link org.evosuite.utils.Randomness} object.
	 */
	public static Randomness getInstance() {
		if (instance == null) {
			instance = new Randomness();
		}
		return instance;
	}

	/**
	 * <p>
	 * nextBoolean
	 * </p>
	 * 
	 * @return a boolean.
	 */
	public static boolean nextBoolean() {
		return  ThreadLocalRandom.current().nextBoolean();
	}

	/**
	 * <p>
	 * nextInt
	 * </p>
	 * 
	 * @param max
	 *            a int.
	 * @return a int.
	 */
	public static int nextInt(int max) {
		return  ThreadLocalRandom.current().nextInt(max);
	}

	public static double nextGaussian() {
		return  ThreadLocalRandom.current().nextGaussian();
	}
	
	/**
	 * <p>
	 * nextInt
	 * </p>
	 * 
	 * @param min
	 *            a int.
	 * @param max
	 *            a int.
	 * @return a int.
	 */
	public static int nextInt(int min, int max) {
		return  ThreadLocalRandom.current().nextInt(max - min) + min;
	}

	/**
	 * <p>
	 * nextInt
	 * </p>
	 * 
	 * @return a int.
	 */
	public static int nextInt() {
		return  ThreadLocalRandom.current().nextInt();
	}

	/**
	 * <p>
	 * nextChar
	 * </p>
	 * 
	 * @return a char.
	 */
	public static char nextChar() {
		return (char) (nextInt(32, 128));
		//return random.nextChar();
	}

	/**
	 * <p>
	 * nextShort
	 * </p>
	 * 
	 * @return a short.
	 */
	public static short nextShort() {
		return (short) ( ThreadLocalRandom.current().nextInt(2 * 32767) - 32767);
	}

	/**
	 * <p>
	 * nextLong
	 * </p>
	 * 
	 * @return a long.
	 */
	public static long nextLong() {
		return  ThreadLocalRandom.current().nextLong();
	}

	/**
	 * <p>
	 * nextByte
	 * </p>
	 * 
	 * @return a byte.
	 */
	public static byte nextByte() {
		return (byte) ( ThreadLocalRandom.current().nextInt(256) - 128);
	}

	/**
	 * <p>
	 * nextDouble
	 * </p>
	 * 
	 * @return a double.
	 */
	public static double nextDouble() {
		return  ThreadLocalRandom.current().nextDouble();
	}

	/**
	 * <p>
	 * nextFloat
	 * </p>
	 * 
	 * @return a float.
	 */
	public static float nextFloat() {
		return  ThreadLocalRandom.current().nextFloat();
	}

	/**
	 * <p>
	 * Setter for the field <code>seed</code>.
	 * </p>
	 * 
	 * @param seed
	 *            a long.
	 */
	public static void setSeed(long seed) {
		Randomness.seed = seed;
		 ThreadLocalRandom.current().setSeed(seed);
	}

	/**
	 * <p>
	 * Getter for the field <code>seed</code>.
	 * </p>
	 * 
	 * @return a long.
	 */
	public static long getSeed() {
		return seed;
	}

	/**
	 * <p>
	 * choice
	 * </p>
	 * 
	 * @param list
	 *            a {@link java.util.List} object.
	 * @param <T>
	 *            a T object.
	 * @return a T object or <code>null</code> if <code>list</code> is empty.
	 */
	public static <T> T choice(List<T> list) {
		if (list.isEmpty())
			return null;

		int position =  ThreadLocalRandom.current().nextInt(list.size());
		return list.get(position);
	}

	/**
	 * <p>
	 * choice
	 * </p>
	 * 
	 * @param set
	 *            a {@link java.util.Collection} object.
	 * @param <T>
	 *            a T object.
	 * @return a T object or <code>null</code> if <code>set</code> is empty.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T choice(Collection<T> set) {
		if (set.isEmpty())
			return null;

		int position =  ThreadLocalRandom.current().nextInt(set.size());
		return (T) set.toArray()[position];
	}

	public static <T> T choice(Set<T> set) {
		if (set.isEmpty())
			return null;
		int index = nextInt(set.size());
        Iterator<T> it = set.iterator();
        for (int i = 0; i < index; i++)
			it.next();
        return it.next();
	}
	
	/**
	 * <p>
	 * choice
	 * </p>
	 * 
	 * @param elements
	 *            a T object.
	 * @param <T>
	 *            a T object.
	 * @return a T object or <code>null</code> if <code>elements.length</code> is zero.
	 */
	public static <T> T choice(T... elements) {
		if (elements.length == 0)
			return null;

		int position =  ThreadLocalRandom.current().nextInt(elements.length);
		return elements[position];
	}

	/**
	 * <p>
	 * shuffle
	 * </p>
	 * 
	 * @param list
	 *            a {@link java.util.List} object.
	 */
	public static void shuffle(List<?> list) {
		Collections.shuffle(list,  ThreadLocalRandom.current());
	}

	/**
	 * <p>
	 * nextString
	 * </p>
	 * 
	 * @param length
	 *            a int.
	 * @return a {@link java.lang.String} object.
	 */
	public static String nextString(int length) {
		char[] characters = new char[length];
		for (int i = 0; i < length; i++)
			characters[i] = nextChar();
		return new String(characters);
	}
}
