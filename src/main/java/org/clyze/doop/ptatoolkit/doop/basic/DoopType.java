package org.clyze.doop.ptatoolkit.doop.basic;

import org.clyze.doop.ptatoolkit.pta.basic.Type;
/**
 * A class representing a type in Doop.
 * It extends the abstract class Type and provides a concrete implementation.
 */
public class DoopType extends Type {
	// The name of the type and its unique identifier.
	private final String typeName;
	private final int id;
	
	/**
	 * Creates a Doop type descriptor.
	 *
	 * @param typeName the type name
	 * @param id the unique type identifier
	 */
	public DoopType(String typeName, int id) {
		this.typeName = typeName;
		this.id = id;
	}
	
	/**
	 * Returns the unique type identifier.
	 *
	 * @return the unique identifier
	 */
	@Override
	public int getID() {
		return id;
	}

	/**
	 * Returns the name of the type.
	 *
	 * @return the name of the type
	 */
	@Override
	public String toString() {
		return typeName;
	}
	
}
