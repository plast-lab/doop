package org.clyze.doop.ptatoolkit.pta.basic;

/**
 * A class used to represent abstract objects in points-to analysis.
 * To avoid the name collision between this class and java.lang.Object,
 * it is named "Obj".
 *
 */
public abstract class Obj extends AttributeElement {

	private final Type type;

	/**
	 * Constructor for Obj that initializes the type of the object. The type of the object is important for points-to analysis, as it helps to determine the possible values that can be assigned to variables and how they may affect the program's behavior.
	 * @param type the type of the object
	 */
	protected Obj(Type type) {
		this.type = type;
	}

	/**
	 * Returns the type of the object. The type of the object is important for points-to analysis, as it helps to determine the possible values that can be assigned to variables and how they may affect the program's behavior.
	 * @return the type of the object
	 */
	public Type getType() {
		return type;
	}
}
