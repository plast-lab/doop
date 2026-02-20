package org.clyze.doop.ptatoolkit.doop.basic;

import org.clyze.doop.ptatoolkit.pta.basic.Obj;
import org.clyze.doop.ptatoolkit.pta.basic.Type;

/**
 * A class representing an object in Doop.
 * It extends the abstract class Obj and provides a concrete implementation.
 */
public class DoopObj extends Obj {

	private final String objName;
	private final int id;
	private final String rep;
	
	/**
	 * Constructor for DoopObj that initializes the object name, its type, and its unique identifier.
	 * @param objName the name of the object
	 * @param type the type of the object
	 * @param id the unique identifier of the object
	 */
	public DoopObj(String objName, Type type, int id) {
		super(type);
		this.objName = objName;
		this.id = id;
		//this.rep = String.format("%s(%d)", objName, id);
		this.rep = objName;
	}
	
	/**
	 * Returns the unique identifier of the object.
	 * @return the unique identifier of the object
	 */
	@Override
	public int getID() {
		return id;
	}
	
	/**
	 * Returns the string representation of the object.
	 * @return the string representation of the object
	 */
	@Override
	public String toString() {
		return rep;
	}
}
