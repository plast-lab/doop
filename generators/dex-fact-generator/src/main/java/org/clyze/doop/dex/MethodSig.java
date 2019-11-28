package org.clyze.doop.dex;

import org.clyze.utils.TypeUtils;
import org.jf.dexlib2.iface.reference.MethodReference;

import java.util.List;

class MethodSig {
    private final String declClass;
    private final String name;
    public final String[] paramTypes;
    public final String retType;
    public final String sig;

    /**
     * Convert a Dex method reference to a MethodSig.
     * @param methodRef  the Dex method reference
     */
    MethodSig(MethodReference methodRef) {
        this.declClass = TypeUtils.raiseTypeId(methodRef.getDefiningClass());
        this.name = methodRef.getName();
        this.retType = TypeUtils.raiseTypeId(methodRef.getReturnType());

        // Calculate information about parameter types.
        List<? extends CharSequence> paramTypes0 = methodRef.getParameterTypes();
        int paramTypesCount = paramTypes0.size();
        this.paramTypes = new String[paramTypesCount];
        StringBuilder paramTypesSig = new StringBuilder();
        for (int i = 0; i < paramTypesCount; i++) {
            String pType = TypeUtils.raiseTypeId(paramTypes0.get(i).toString());
            this.paramTypes[i] = pType;
            if (i != 0)
                paramTypesSig.append(",");
            paramTypesSig.append(pType);
        }
        this.sig = DexRepresentation.methodId(declClass, retType, name, paramTypesSig.toString());
    }

    public String getMid() {
        return declClass + "." + name;
    }
}
