package org.clyze.doop.dex;

import org.clyze.doop.util.TypeUtils;
import org.jf.dexlib2.iface.reference.MethodReference;

import java.util.List;

class DexRepresentation {

    public static String local(String methId, int i) {
        return methId + "/v" + i;
    }

    public static String param(String methId, int i) {
        return methId + "/p" + i;
    }

    public static String fieldId(String declClass, String type, String name) {
        return "<" + declClass + ": " + type + " " + name + ">";
    }

    public static String methodId(String declClass, String retType, String name, String paramsSig) {
        return "<" + declClass + ": " + retType + " " + name + paramsSig + ">";
    }

    public static String thisVarId(String methId) {
        return param(methId, 0);
    }


    /**
     * Generates a method id for a method.
     * @param m      the method for which to generate an id
     * @param mf     if not null, this argument will be populated
     *               with more facts about the method
     * @return       the method id
     */
    public static String methodId(MethodReference m, MethodFacts mf) {
        String jvmRetType = m.getReturnType();
        String retType = TypeUtils.raiseTypeId(jvmRetType);

        StringBuilder jvmParamsSig = new StringBuilder();
        StringBuilder paramsSig = new StringBuilder("(");
        boolean firstParam = true;
        List<? extends CharSequence> paramTypes = m.getParameterTypes();
        for (CharSequence pt : paramTypes) {
            if (mf != null)
                jvmParamsSig.append(pt);
            if (firstParam)
                firstParam = false;
            else
                paramsSig.append(',');
            paramsSig.append(TypeUtils.raiseTypeId(pt.toString()));
        }
        paramsSig.append(')');
        String paramsSigStr = paramsSig.toString();

        String simpleName = m.getName();
        String declaringClass = TypeUtils.raiseTypeId(m.getDefiningClass());
        String methId = methodId(declaringClass, retType, simpleName, paramsSigStr);
        if (mf != null) {
            mf.simpleName = simpleName;
            mf.paramsSig = paramsSigStr;
            mf.declaringClass = declaringClass;
            mf.retType = retType;
            mf.jvmSig = "(" + jvmParamsSig + ")" + jvmRetType;
            mf.arity = Integer.valueOf(paramTypes.size()).toString();
        }
        return methId;
    }

    static String strOfLineNo(Integer i) {
        // Return "-1" instead of "", as lineNo is the last column in facts
        // and can lead to parsing problems.
        return (i == null) ? "-1" : String.valueOf(i.intValue());
    }

}
