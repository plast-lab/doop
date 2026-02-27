package org.clyze.doop.ptatoolkit.doop.basic;

import org.clyze.doop.ptatoolkit.pta.basic.Field;

/**
 * Concrete field representation loaded from Doop facts.
 */
public class DoopField extends Field {

	private final String sig;
	private final int id;
	
	/**
	 * Creates a field value.
	 *
	 * @param sig the field signature
	 * @param id the unique field identifier
	 */
	public DoopField(String sig, int id) {
		this.sig = sig;
		this.id = id;
	}

	/**
	 * Returns the unique field identifier.
	 *
	 * @return the identifier
	 */
	@Override
	public int getID() {
		return id;
	}
	
	/**
	 * Returns the field signature.
	 *
	 * @return the field signature
	 */
	@Override
	public String toString() {
		return sig;
	}

}
