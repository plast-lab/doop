package org.clyze.doop;

public class JavaRepresentation {

    public static String classConstant(String className) {
        return "<class " + className + ">";
    }

    public static String methodTypeConstant(String s) {
        return s;
    }

    public static String methodHandleConstant(String handleName) {
        return "<handle " + handleName + ">";
    }

    protected static String nativeReturnVarOfMethod(String m) {
        return m + "/@native-return";
    }

    public static String instructionId(String m, String kind, int index) {
        return m + "/" + kind + "/instruction" + index;
    }

    public static String invokeId(String pre, String mid, SessionCounter c) {
        return pre + "/" + mid + "/" + c.nextNumber(mid);
    }

    protected static String unsupportedId(String m, String kind, String ins, int index) {
        return m + "/unsupported " + kind + "/" + ins + "/instruction" + index;
    }

    protected static String localId(String m, String l) {
        return m + "/" + l;
    }

    protected static String newLocalIntermediateId(String s, SessionCounter c) {
        return s + "/intermediate/" + c.nextNumber(s);
    }

    public static String heapAllocId(String m, String s, SessionCounter c) {
        return m + "/new " + s + "/" +  c.nextNumber(s);
    }

}
