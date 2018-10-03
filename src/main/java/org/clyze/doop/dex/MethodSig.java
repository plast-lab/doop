package org.clyze.doop.dex;

import org.clyze.doop.util.TypeUtils;
import org.jf.dexlib2.dexbacked.reference.DexBackedMethodReference;

import java.util.List;

class MethodSig {
    private final String declClass;
    private final String name;
    public final String[] paramTypes;
    public final String retType;
    public final String sig;

    /**
     * Creates an anonymous method signature. Used in array creation opcodes.
     * @param paramTypes  the parameter types
     * @param retType     the return type
     */
    MethodSig(String retType, String[] paramTypes) {
        this.declClass = null;
        this.name = null;
        this.paramTypes = paramTypes;
        this.sig = null;
        this.retType = retType;
    }

    /**
     * Convert a Dex method reference to a MethodSig.
     * @param methodRef  the Dex method reference
     */
    MethodSig(DexBackedMethodReference methodRef) {
        this.declClass = TypeUtils.raiseTypeId(methodRef.getDefiningClass());
        this.name = methodRef.getName();
        this.retType = TypeUtils.raiseTypeId(methodRef.getReturnType());

        // Calculate information about parameter types.
        List<String> paramTypes0 = methodRef.getParameterTypes();
        int paramTypesCount = paramTypes0.size();
        this.paramTypes = new String[paramTypesCount];
        StringBuilder paramTypesSig = new StringBuilder("(");
        for (int i = 0; i < paramTypesCount; i++) {
            String pType = TypeUtils.raiseTypeId(paramTypes0.get(i));
            this.paramTypes[i] = pType;
            if (i != 0)
                paramTypesSig.append(",");
            paramTypesSig.append(pType);
        }
        String pTypesSig = paramTypesSig.append(")").toString();
        this.sig = DexRepresentation.methodId(declClass, retType, name, pTypesSig);
    }

    public String getMid() {
        return declClass + "." + name;
    }
}
