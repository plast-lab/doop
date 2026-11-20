package org.clyze.doop.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Collects the parameter names a class file records, read with ASM since
 * Soot keeps only part of them. Two sources, kept apart because frameworks
 * read them differently (Spring 6.1+ reads only the first):
 * <ul>
 * <li>declared names: the MethodParameters attribute (javac -parameters),
 *     what java.lang.reflect.Parameter.getName() returns;</li>
 * <li>debug names: the LocalVariableTable attribute (javac -g), the entry of
 *     each parameter's slot that starts at the method's first instruction.</li>
 * </ul>
 * Methods are keyed by "class.name(descriptor)return" (for example
 * "t.Config.b(Ljava/lang/String;I)Ljava/lang/Object;"); a name is null
 * where the class file records none.
 */
public class ParameterNames {

    private final Map<String, String[]> declaredNames = new ConcurrentHashMap<>();
    private final Map<String, String[]> debugNames = new ConcurrentHashMap<>();

    public static String key(String className, String methodName, String descriptor) {
        return className + "." + methodName + descriptor;
    }

    public String[] getDeclaredNames(String key) {
        return declaredNames.get(key);
    }

    public String[] getDebugNames(String key) {
        return debugNames.get(key);
    }

    /** Read the parameter names of all methods of a class. */
    public void collect(ClassReader reader, String className) {
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                Type[] args = Type.getArgumentTypes(descriptor);
                int n = args.length;
                if (n == 0)
                    return null;
                int[] slots = new int[n];
                int slot = (access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
                for (int i = 0; i < n; i++) {
                    slots[i] = slot;
                    slot += args[i].getSize();
                }
                String key = key(className, name, descriptor);
                return new MethodVisitor(Opcodes.ASM9) {
                    private final List<String> declared = new ArrayList<>();
                    private final String[] debug = new String[n];
                    private boolean anyDebug = false;
                    // The first label ASM visits: the start of the code, where the
                    // entries of parameters begin (labels read by ASM have no offsets).
                    private Label codeStart = null;

                    @Override
                    public void visitParameter(String paramName, int paramAccess) {
                        declared.add(paramName);
                    }

                    @Override
                    public void visitLabel(Label label) {
                        if (codeStart == null)
                            codeStart = label;
                    }

                    @Override
                    public void visitLocalVariable(String localName, String localDescriptor, String localSignature,
                                                   Label start, Label end, int index) {
                        if (localName == null || start != codeStart)
                            return;
                        for (int i = 0; i < n; i++)
                            if (slots[i] == index && debug[i] == null) {
                                debug[i] = localName;
                                anyDebug = true;
                            }
                    }

                    @Override
                    public void visitEnd() {
                        // Like reflection, use the attribute only if it has one entry per parameter.
                        if (declared.size() == n)
                            declaredNames.put(key, declared.toArray(new String[0]));
                        if (anyDebug)
                            debugNames.put(key, debug);
                    }
                };
            }
        }, ClassReader.SKIP_FRAMES);
    }
}
