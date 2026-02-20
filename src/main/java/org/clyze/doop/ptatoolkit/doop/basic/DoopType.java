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
	 * Constructor for DoopType that initializes the type name and its unique identifier.	
	 * @param typeName
	 * @param id
	 */
	public DoopType(String typeName, int id) {
		this.typeName = typeName;
		this.id = id;
	}
	
	@Override
	/**
	 * Returns the name of the type.
	 * @return the name of the type
	 */
	public int getID() {
		return id;
	}

	@Override
	/**
	 * Returns the name of the type.
	 * @return the name of the type
	 */
	public String toString() {
		return typeName;
	}
	
}
