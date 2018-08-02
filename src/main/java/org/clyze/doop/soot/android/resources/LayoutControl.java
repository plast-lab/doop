/*******************************************************************************
 * Copyright (c) 2012 Secure Software Engineering Group at EC SPRIDE.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric
 * Bodden, and others.
 ******************************************************************************/
package org.clyze.doop.soot.android.resources;

import java.util.HashMap;
import java.util.Map;

import soot.SootClass;

/**
 * Data class representing a layout control on the android screen
 * 
 * @author Steven Arzt
 *
 */
public class LayoutControl {
	
	private final int id;
	private final SootClass viewClass;
	private boolean isSensitive;
	private Map<String, Object> additionalAttributes = null;
	
	public LayoutControl(int id, SootClass viewClass) {
		this.id = id;
		this.viewClass = viewClass;
	}
	
	public LayoutControl(int id, SootClass viewClass, boolean isSensitive) {
		this(id, viewClass);
		this.isSensitive = isSensitive;
	}
	
	public LayoutControl(int id, SootClass viewClass, boolean isSensitive,
			Map<String, Object> additionalAttributes) {
		this(id, viewClass, isSensitive);
		this.additionalAttributes = additionalAttributes;
	}

	public int getID() {
		return this.id;
	}
	
	public SootClass getViewClass() {
		return this.viewClass;
	}
	
	public void setIsSensitive(boolean isSensitive) {
		this.isSensitive = isSensitive;
	}
	
	public boolean isSensitive() {
		return this.isSensitive;
	}
	
	/**
	 * Adds an additional attribute to this layout control
	 * @param key The key of the attribute
	 * @param value The value of the attribute
	 */
	public void addAdditionalAttribute(String key, String value) {
		if (additionalAttributes != null)
			additionalAttributes = new HashMap<>();
		additionalAttributes.put(key, value);
	}
	
	/**
	 * Gets the additional attributes associated with this layout control
	 * @return The additional attributes associated with this layout control
	 */
	public Map<String, Object> getAdditionalAttributes() {
		return additionalAttributes;
	}
	
	@Override
	public String toString() {
		return id + " - " + viewClass;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id;
		result = prime * result + (isSensitive ? 1231 : 1237);
		result = prime * result + ((viewClass == null) ? 0 : viewClass.hashCode());
		result = prime * result + ((additionalAttributes == null)
				? 0 : additionalAttributes.hashCode());
		return result;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LayoutControl other = (LayoutControl) obj;
		if (id != other.id)
			return false;
		if (isSensitive != other.isSensitive)
			return false;
		if (viewClass == null) {
			if (other.viewClass != null)
				return false;
		} else if (!viewClass.equals(other.viewClass))
			return false;
		if (additionalAttributes == null) {
			if (other.additionalAttributes != null)
				return false;
		} else if (!additionalAttributes.equals(other.additionalAttributes))
			return false;
		return true;
	}
	
}
