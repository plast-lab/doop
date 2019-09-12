package org.clyze.doop.dex;

class DexRepresentation {

    public static String local(String methId, int i) {
        return methId + "/v" + i;
    }

    public static String param(String methId, int i) {
        return methId + "/p" + i;
    }

    public static String methodId(String declClass, String retType, String name, String paramsSig) {
        return "<" + declClass + ": " + retType + " " + name + "(" + paramsSig + ")>";
    }

    public static String thisVarId(String methId) {
        return param(methId, 0);
    }

    static String strOfLineNo(Integer i) {
        // Return "-1" instead of "", as lineNo is the last column in facts
        // and can lead to parsing problems.
        return (i == null) ? "-1" : String.valueOf(i.intValue());
    }

}
