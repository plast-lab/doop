package org.clyze.doop.common;

import java.util.regex.Pattern;

public class JavaRepresentation {

    private final static Pattern qPat = Pattern.compile("'");

    public static String classConstant(String className) {
        return "<class " + className + ">";
    }

    public static String methodHandleConstant(String handleName) {
        return "<handle " + handleName + ">";
    }

    public static String nativeReturnVarOfMethod(String m) {
        return m + "/@native-return";
    }

    public static String instructionId(String m, String kind, int index) {
        return m + "/" + kind + "/" + index;
    }

    public static String numberedInstructionId(String pre, String mid, SessionCounter c) {
        return instructionId(pre, mid, c.nextNumber(mid));
    }

    protected static String unsupportedId(String m, String kind, String ins, int index) {
        return m + "/unsupported " + kind + "/" + ins + "/" + index;
    }

    protected static String localId(String m, String l) {
        return m + "/" + l;
    }

    protected static String newLocalIntermediateId(String s, SessionCounter c) {
        return numberedInstructionId(s, "intermediate", c);
    }

    public static String heapAllocId(String m, String s, SessionCounter c) {
        return m + "/new " + s + "/" +  c.nextNumber(s);
    }

    public static String handlerMid(String excType) {
        return "catch " + excType;
    }

    protected static String throwLocalId(String name) {
        return "throw " + name;
    }

    public static String fieldId(String declClass, String type, String name) {
        return "<" + declClass + ": " + type + " " + name + ">";
    }

    @SuppressWarnings("WeakerAccess")
    public static String stripQuotes(CharSequence s) {
        return qPat.matcher(s).replaceAll("");
    }
}
