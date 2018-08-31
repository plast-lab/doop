package org.clyze.doop.common.android;

import java.util.Map;
import java.util.Objects;

/**
 * A layout control found in an Android resources file.
 */
public abstract class LayoutControl {
    abstract public int getID();
    abstract public boolean isSensitive();
    abstract public String getViewClassName();
    abstract public int getParentID();
    abstract public String getAppRId();
    abstract public String getAndroidRId();
    protected abstract Map<String, Object> getAdditionalAttributes();

    @Override
    public int hashCode() {
        return Objects.hash(getID(), isSensitive(), getViewClassName(), getAdditionalAttributes(), getParentID());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof LayoutControl))
            return false;
        else {
            LayoutControl lc = (LayoutControl)obj;
            return (getID() == lc.getID()) &&
                (isSensitive() == lc.isSensitive()) &&
                (Objects.equals(getViewClassName(), lc.getViewClassName())) &&
                (Objects.equals(getAdditionalAttributes(), lc.getAdditionalAttributes())) &&
                (getParentID() == lc.getParentID());
        }
    }
}
