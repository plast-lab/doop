package org.clyze.doop.ptatoolkit.util;

import java.util.Objects;

/**
 * Immutable pair of two values.
 *
 * @param <T1> the first value type
 * @param <T2> the second value type
 */
public class Pair<T1, T2> {

	private final T1 first;
	private final T2 second;

	/**
	 * Creates a pair.
	 *
	 * @param first the first value
	 * @param second the second value
	 */
	public Pair(T1 first, T2 second) {
		this.first = first;
		this.second = second;
	}

	/**
	 * Returns the first value.
	 *
	 * @return the first value
	 */
	public T1 getFirst() {
		return first;
	}

	/**
	 * Returns the second value.
	 *
	 * @return the second value
	 */
	public T2 getSecond() {
		return second;
	}

	@Override
	public int hashCode() {
		return Objects.hash(first, second);
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof Pair) {
			Pair<?, ?> anoPair = (Pair<?, ?>) o;
			return Objects.equals(first, anoPair.first)
					&& Objects.equals(second, anoPair.second);
		}
		return false;
	}

	@Override
	public String toString() {
		return "<"
				+ Objects.toString(first) + ", "
				+ Objects.toString(second)
				+ ">";
	}
	
}
