package org.clyze.doop.ptatoolkit.pta.util;


/**
 * Every instance of this class has an unique ID number.
 */
public abstract class Numberable {

	public abstract int getID();

	@Override
	public final int hashCode() {
		return getID();
	}
	
	@Override
	public final boolean equals(Object obj) {
		return this == obj;
	}
	
}
