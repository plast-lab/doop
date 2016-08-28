package org.clyze.doop.util;

// import org.objectweb.asm.Opcodes;
// import org.objectweb.asm.Type;

public class TypeUtils /* implements Opcodes */
{
    private TypeUtils()
    {
        throw new AssertionError();
    }

    /* MB TODO this code appears to not be used and is the only
       dependency on asm. Can it be removed?
    public static String getClassName(Type classType)
    {
        return classType.getClassName();
    }

    public static String getPackageName(Type classType)
    {
        return getPackageName(getClassName(classType));
    }
    */

    public static String getPackageName(String className)
    {
        int index = className.lastIndexOf('.');

        return (index < 0) ? "" : className.substring(0, index);
    }

    /* MB TODO this code appears to not be used and is the only
       dependency on asm. Can it be removed?

    public static boolean isPublic(int access) {
        return (access & ACC_PUBLIC) != 0;
    }

    public static boolean isSynthetic(int access) {
        return (access & ACC_SYNTHETIC) != 0;
    }
   */
}
