package org.clyze.doop.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.Opcodes;

public enum TypeUtils implements Opcodes  {
    ;

    private static final String BOOLEAN = "boolean";
    private static final String BOOLEAN_JVM = "Z";
    private static final String INT = "int";
    private static final String INT_JVM = "I";
    private static final String LONG = "long";
    private static final String LONG_JVM = "J";
    private static final String DOUBLE = "double";
    private static final String DOUBLE_JVM = "D";
    private static final String VOID = "void";
    private static final String VOID_JVM = "V";
    private static final String FLOAT = "float";
    private static final String FLOAT_JVM = "F";
    private static final String CHAR = "char";
    private static final String CHAR_JVM = "C";
    private static final String SHORT = "short";
    private static final String SHORT_JVM = "S";
    private static final String BYTE = "byte";
    private static final String BYTE_JVM = "B";

    private static final Map<String, String> cachedRaisedTypes = new ConcurrentHashMap<>();

    public static String getPackageName(String className)
    {
        int index = className.lastIndexOf('.');

        return (index < 0) ? "" : className.substring(0, index);
    }

    /**
     * Converts a type id of the form 'La/b/C;' to 'a.b.C'.
     * @param id     the type id
     * @return       the fixed type id
     */
    public static String raiseTypeId(String id) {
        String cached = cachedRaisedTypes.get(id);
        if (cached != null)
            return cached;

        int typePrefixEndIdx = 0;
        // Peel off array brackets.
        while (id.charAt(typePrefixEndIdx) == '[')
            typePrefixEndIdx++;

        StringBuilder sb;
        if ((id.charAt(typePrefixEndIdx) == 'L') && (id.charAt(id.length() -1) == ';'))
            sb = new StringBuilder(id.substring(typePrefixEndIdx + 1, id.length() - 1).replace('/', '.'));
        else
            sb = new StringBuilder(decodePrimType(id.substring(typePrefixEndIdx)));

        if (typePrefixEndIdx != 0) {
            for (int i = 0; i < typePrefixEndIdx; i++)
                sb.append("[]");
        }

        String ret = sb.toString();

        // Find multidimensional arrays in bytecode (e.g. '[[C' / 'char[][]') .
//        if (typePrefixEndIdx > 1) {
//            System.err.println("Warning: found multidimensional array type: " + id + " -> " + ret);
//        }

        cachedRaisedTypes.put(id, ret);
        return ret;
    }

    private static String decodePrimType(String id) {
        switch (id) {
            case BOOLEAN_JVM : return BOOLEAN;
            case INT_JVM     : return INT;
            case LONG_JVM    : return LONG;
            case DOUBLE_JVM  : return DOUBLE;
            case VOID_JVM    : return VOID;
            case FLOAT_JVM   : return FLOAT;
            case CHAR_JVM    : return CHAR;
            case SHORT_JVM   : return SHORT;
            case BYTE_JVM    : return BYTE;
            default          : throw new RuntimeException("Invalid type id format: " + id);
        }
    }

    public static boolean isPrimitiveType(String s) {
        return (s.equals(BOOLEAN) || s.equals(INT) || s.equals(LONG) ||
                s.equals(DOUBLE) || s.equals(VOID) || s.equals(FLOAT) ||
                s.equals(CHAR) || s.equals(SHORT) || s.equals(BYTE));
    }

    public static boolean isLowLevelType(char first, String s) {
        return first == '[' || (first == 'L' && s.endsWith(";"));
    }
}
