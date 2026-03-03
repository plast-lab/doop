package org.clyze.doop.ptatoolkit.pta.util;


/**
 * Base type for entities identified by a unique numeric ID.
 */
public abstract class Numberable {

	/**
	 * Returns the unique identifier of this object.
	 *
	 * @return the unique numeric identifier
	 */
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
