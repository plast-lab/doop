package org.clyze.doop.dex;

import org.clyze.utils.TypeUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jf.dexlib2.iface.reference.MethodReference;

import java.util.List;

class DexRepresentation {

    private static Map<MethodReference, String> cachedMethodIds = new ConcurrentHashMap<>();

    public static String local(String methId, int i) {
        return methId + "/v" + i;
    }

    public static String param(String methId, int i) {
        return methId + "/p" + i;
    }

    public static String methodId(String declClass, String retType, String name, String paramsSig) {
        return "<" + declClass + ": " + retType + " " + name + "(" + paramsSig + ")>";
    }

    public static String methodId(MethodReference m) {
        String methId = cachedMethodIds.get(m);
        if (methId == null) {
            methId = methodFacts(m).getMethodId();
            cachedMethodIds.put(m, methId);
        }
        return methId;
    }

    public static String thisVarId(String methId) {
        return param(methId, 0);
    }

    /**
     * Generates a table of metadata/facts for a method.
     * @param m      the method
     * @return       the method facts
     */
    public static MethodFacts methodFacts(MethodReference m) {
        String jvmRetType = m.getReturnType();
        String retType = TypeUtils.raiseTypeId(jvmRetType);

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

        String simpleName = m.getName();
        String declaringClass = TypeUtils.raiseTypeId(m.getDefiningClass());
        String methId = methodId(declaringClass, retType, simpleName, paramsSigStr);
        cachedMethodIds.put(m, methId);
        String jvmSig = "(" + jvmParamsSig + ")" + jvmRetType;
        String arity = Integer.valueOf(paramTypes.size()).toString();
        return new MethodFacts(simpleName, paramsSigStr, declaringClass, retType, jvmSig, arity);
    }

    static String strOfLineNo(Integer i) {
        // Return "-1" instead of "", as lineNo is the last column in facts
        // and can lead to parsing problems.
        return (i == null) ? "-1" : String.valueOf(i.intValue());
    }

}
