package org.clyze.doop.util;

public enum PackageUtils {
    ;

    public static String getPackageName(String className)
    {
        int index = className.lastIndexOf('.');
        return (index < 0) ? "" : className.substring(0, index);
    }

}
