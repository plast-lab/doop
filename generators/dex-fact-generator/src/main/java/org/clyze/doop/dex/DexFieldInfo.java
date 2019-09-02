package org.clyze.doop.dex;

import org.clyze.doop.common.FieldInfo;
import org.clyze.utils.TypeUtils;
import org.jf.dexlib2.iface.reference.FieldReference;

class DexFieldInfo extends FieldInfo {    
    /**
     * Generate the field information for a field reference.
     * @param fieldRef     the field reference
     */
    DexFieldInfo(FieldReference fieldRef) {
        super(TypeUtils.raiseTypeId(fieldRef.getType()), fieldRef.getName());
        this.definingClass = TypeUtils.raiseTypeId(fieldRef.getDefiningClass());
    }
}
