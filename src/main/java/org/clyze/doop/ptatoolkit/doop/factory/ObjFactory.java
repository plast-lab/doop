package org.clyze.doop.ptatoolkit.doop.factory;

import org.clyze.doop.ptatoolkit.doop.DataBase;
import org.clyze.doop.ptatoolkit.doop.Query;
import org.clyze.doop.ptatoolkit.doop.basic.DoopObj;
import org.clyze.doop.ptatoolkit.pta.basic.Obj;
import org.clyze.doop.ptatoolkit.pta.basic.Type;

import java.util.HashMap;
import java.util.Map;

public class ObjFactory extends ElementFactory<Obj> {

	private static final String CLASS_TYPE = "java.lang.Class";

	private final Map<String, Type> typeMap = new HashMap<>();;
	private final TypeFactory typeFactory;
	
	public ObjFactory(DataBase db, TypeFactory typeFactory) {
		this.typeFactory = typeFactory;
		db.query(Query.OBJ_TYPE).forEachRemaining(list -> {
			String objName = list.get(0);
			Type type = typeFactory.get(list.get(1));
			typeMap.put(objName, type);
		});
	}
	
	@Override
	protected Obj createElement(String name) {
		Type type;
		if (name.startsWith("<class ")) {
			// Handle special Class objects
			type = typeFactory.get(CLASS_TYPE);
		} else {
			type = typeMap.get(name);
		}
		return new DoopObj(name, type, ++count);
	}

}
