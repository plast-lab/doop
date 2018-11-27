package org.clyze.doop.ptatoolkit.util;

import java.util.Objects;

public class Triple<T1, T2, T3> {

	private final T1 first;
	private final T2 second;
	private final T3 third;

	public Triple(T1 first, T2 second, T3 third) {
		this.first = first;
		this.second = second;
		this.third = third;
	}

	public T1 getFirst() {
		return first;
	}

	public T2 getSecond() {
		return second;
	}
	
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
