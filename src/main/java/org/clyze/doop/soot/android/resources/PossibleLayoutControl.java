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

/**
 * Representation of a possible layout control on the android screen.
 * No Soot analysis, just info from XML
 *
 * @author Steven Arzt
 * @author Yannis Smaragdakis
 *
 */
public class PossibleLayoutControl {

    private final int id;
    private final String viewClassName;
    private boolean isSensitive;
    private Map<String, Object> additionalAttributes = null;

    private int parentID = -1;

    public PossibleLayoutControl(int id, String viewClassName) {
        this.id = id;
        this.viewClassName = viewClassName;
    }

    public PossibleLayoutControl(int id, String viewClassName, boolean isSensitive) {
        this(id, viewClassName);
        this.isSensitive = isSensitive;
    }

    public PossibleLayoutControl(int id, String viewClassName, boolean isSensitive,
                                 Map<String, Object> additionalAttributes) {
        this(id, viewClassName, isSensitive);
        this.additionalAttributes = additionalAttributes;
    }

    public PossibleLayoutControl(int id, String viewClassName, boolean isSensitive,
                                 Map<String, Object> additionalAttributes, int parentID) {

        this(id, viewClassName, isSensitive, additionalAttributes);
        this.parentID = parentID;
    }

    public int getParentID() {
        return parentID;
    }

    public int getID() {
        return this.id;
    }

    public String getViewClassName() {
        return this.viewClassName;
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
        return id + " - " + viewClassName;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        result = prime * result + (isSensitive ? 1231 : 1237);
        result = prime * result + ((viewClassName == null) ? 0 : viewClassName.hashCode());
        result = prime * result + ((additionalAttributes == null)
                ? 0 : additionalAttributes.hashCode());
        result = prime * result + ((parentID == -1) ? 0 : parentID);
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
        PossibleLayoutControl other = (PossibleLayoutControl) obj;
        if (id != other.id)
            return false;
        if (isSensitive != other.isSensitive)
            return false;
        if (viewClassName == null) {
            if (other.viewClassName != null)
                return false;
        } else if (!viewClassName.equals(other.viewClassName))
            return false;
        if (additionalAttributes == null) {
            if (other.additionalAttributes != null)
                return false;
        } else if (!additionalAttributes.equals(other.additionalAttributes))
            return false;
        if (parentID != other.parentID)
            return false;
        return true;
    }

}
