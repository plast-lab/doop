package org.clyze.doop.common;

import java.io.*;
import org.clyze.doop.util.TypeUtils;
import org.objectweb.asm.ClassReader;

enum BytecodeUtil {
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
