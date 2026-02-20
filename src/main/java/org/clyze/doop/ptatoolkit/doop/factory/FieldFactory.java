package org.clyze.doop.ptatoolkit.doop.factory;

import org.clyze.doop.ptatoolkit.doop.basic.DoopField;
import org.clyze.doop.ptatoolkit.pta.basic.Field;

public class FieldFactory extends ElementFactory<Field> {

	/* Creates a new DoopField with the given signature and a unique identifier. */
	@Override
	protected Field createElement(String name) {
		return new DoopField(name, ++count);
	}
	
}
