package org.clyze.doop.ptatoolkit.doop.basic;

import org.clyze.doop.ptatoolkit.pta.basic.Obj;
import org.clyze.doop.ptatoolkit.pta.basic.Type;

public class DoopObj extends Obj {

	private final String objName;
	private final int id;
	private final String rep;
	
	public DoopObj(String objName, Type type, int id) {
		super(type);
		this.objName = objName;
		this.id = id;
		//this.rep = String.format("%s(%d)", objName, id);
		this.rep = objName;
	}
	
	@Override
	public int getID() {
		return id;
	}
	
	@Override
	public String toString() {
		return rep;
	}
}
