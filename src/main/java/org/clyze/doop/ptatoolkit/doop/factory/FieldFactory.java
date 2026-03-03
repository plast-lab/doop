package org.clyze.doop.ptatoolkit.doop.factory;

import org.clyze.doop.ptatoolkit.doop.basic.DoopField;
import org.clyze.doop.ptatoolkit.pta.basic.Field;

/**
 * Factory for creating and caching field elements from Doop identifiers.
 */
public class FieldFactory extends ElementFactory<Field> {

	/**
	 * Creates a new field element.
	 *
	 * @param name the field signature
	 * @return a new {@link DoopField}
	 */
	@Override
	protected Field createElement(String name) {
		return new DoopField(name, ++count);
	}
	
}
