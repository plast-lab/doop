package org.clyze.doop.dex;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
// import org.checkerframework.checker.nullness.qual.*;
import org.clyze.utils.TypeUtils;
import org.jf.dexlib2.iface.reference.MethodReference;

class MethodFacts {

    private static final Map<MethodReference, String> cachedMethodIds = new ConcurrentHashMap<>();

    public final String simpleName;
    public final String paramsSig;
    public final String declaringClass;
    public final String retType;
    public final String jvmSig;
    public final String arity;
    private final String methId;

    /**
     * Generates a method facts table for a method reference.
     */
    public MethodFacts(MethodReference m) {
        this.simpleName = m.getName();
        this.declaringClass = TypeUtils.raiseTypeId(m.getDefiningClass());

        String jvmRetType = m.getReturnType();
        this.retType = TypeUtils.raiseTypeId(jvmRetType);

        StringBuilder jvmParamsSig = new StringBuilder();
        StringBuilder paramsSig = new StringBuilder();
        boolean firstParam = true;
        List<? extends CharSequence> paramTypes = m.getParameterTypes();
        for (CharSequence pt : paramTypes) {
            jvmParamsSig.append(pt);
            if (firstParam)
                firstParam = false;
            else
                paramsSig.append(',');
            paramsSig.append(TypeUtils.raiseTypeId(pt.toString()));
        }
        String paramsSigStr = paramsSig.toString();
        this.paramsSig = paramsSigStr;

        this.methId = DexRepresentation.methodId(declaringClass, retType, simpleName, paramsSigStr);
        cachedMethodIds.put(m, methId);

        this.jvmSig = "(" + jvmParamsSig + ")" + jvmRetType;
        this.arity = Integer.valueOf(paramTypes.size()).toString();
    }

    public static String methodId(MethodReference m) {
        String methId = cachedMethodIds.get(m);
        if (methId == null) {
            methId = (new MethodFacts(m)).methId;
            cachedMethodIds.put(m, methId);
        }
        return methId;
    }

    public String getMethodId() {
        return methId;
    }
}
