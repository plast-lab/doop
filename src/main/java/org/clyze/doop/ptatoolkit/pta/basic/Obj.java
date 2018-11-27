package org.clyze.doop.ptatoolkit.pta.basic;

/**
 * A class used to represent abstract objects in points-to analysis.
 * To avoid the name collision between this class and java.lang.Object,
 * it is named "Obj".
 *
 */
public abstract class Obj extends AttributeElement {

	private final Type type;

	protected Obj(Type type) {
		this.type = type;
	}

	public Type getType() {
		return type;
	}

}
