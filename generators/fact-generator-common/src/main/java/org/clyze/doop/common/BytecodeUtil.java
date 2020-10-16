package org.clyze.doop.common;

import java.io.*;
import org.clyze.utils.TypeUtils;
import org.objectweb.asm.ClassReader;

public enum BytecodeUtil {
    ;
    public static String getClassName(File f) throws IOException {
        try (FileInputStream fis = new FileInputStream(f)) {
            return getClassName(new ClassReader(fis));
        }
    }
    public static String getClassName(ClassReader reader) {
        return TypeUtils.replaceSlashesWithDots(reader.getClassName());
    }
}
