package org.clyze.doop.ptatoolkit.util;

import java.util.Objects;

/**
 * Immutable tuple of three values.
 *
 * @param <T1> the first value type
 * @param <T2> the second value type
 * @param <T3> the third value type
 */
public class Triple<T1, T2, T3> {

	private final T1 first;
	private final T2 second;
	private final T3 third;

	/**
	 * Creates a triple.
	 *
	 * @param first the first value
	 * @param second the second value
	 * @param third the third value
	 */
	public Triple(T1 first, T2 second, T3 third) {
		this.first = first;
		this.second = second;
		this.third = third;
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
	
	/**
	 * Returns the third value.
	 *
	 * @return the third value
	 */
	public T3 getThird() {
		return third;
	}

	@Override
	public int hashCode() {
		return Objects.hash(first, second, third);
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof Triple) {
			Triple<?, ?, ?> anoTriple = (Triple<?, ?, ?>) o;
			return Objects.equals(first, anoTriple.first)
					&& Objects.equals(second, anoTriple.second)
					&& Objects.equals(third, anoTriple.third);
		}
		return false;
	}
	
	@Override
	public String toString() {
		return "<"
				+ Objects.toString(first) + ", "
				+ Objects.toString(second) + ", "
				+ Objects.toString(third)
				+ ">";
	}
	
}
