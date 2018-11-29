package org.clyze.doop.ptatoolkit.doop.basic;

import org.clyze.doop.ptatoolkit.pta.basic.Type;

public class DoopType extends Type {

	private final String typeName;
	private final int id;
	
	public DoopType(String typeName, int id) {
		this.typeName = typeName;
		this.id = id;
	}
	
	@Override
	public int getID() {
		return id;
	}

	@Override
	public String toString() {
		return typeName;
	}
	
}
