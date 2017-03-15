package Instrumentation.Agent;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.NewArray;
import javassist.expr.NewExpr;

import java.io.IOException;
import java.lang.instrument.*;
import java.security.ProtectionDomain;
import java.util.Arrays;

public class Transformer implements ClassFileTransformer {


    public static synchronized void agentmain(String args, Instrumentation inst) {

    }
    // java -javaagent:build/libs/agent-instrument-test-all-1.0-SNAPSHOT.jar -jar ../../doop-benchmarks/dacapo-9.12-bach.jar jython | more
    public static synchronized void premain(String args, Instrumentation inst) throws ClassNotFoundException, IOException {
        inst.addTransformer(new Transformer());
    }

    private static boolean isLibraryClass(String name) {
        return name.startsWith("java") || name.startsWith("Instrumentation/");
    }

    private static boolean isInterestingClass(String name) {
        if (name.startsWith("javaassist"))
            return false;
        if (name.startsWith("Instrumentation"))
            return false;
        if (name.equals("java/lang/String"))
            return false;
        return true;
    }

    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain pd, byte[] classFile) throws IllegalClassFormatException {
        if (isLibraryClass(className)) return null;
        CtClass cls = null;
        try {
            cls = getCtClass(className);
        } catch (NotFoundException e) {
//            ClassPool cp = ClassPool.getDefault();
//            cp.insertClassPath(new ByteArrayClassPath(className.replace("/", "."), classFile));
//            try {
//                cls = getCtClass(className);
//            } catch (NotFoundException e2) {
//                System.err.println("Warning: Cannot find " + className);
//                return null;
//            }
            return null;
        }

        if (cls.isInterface()) return null;

        Arrays.stream(cls.getDeclaredMethods()).forEach((CtMethod m) -> {
            try {
                m.instrument(new ExprEditor() {
                    public void edit(NewExpr newExpr) throws CannotCompileException {
                        if (!isInterestingClass(newExpr.getClassName()))
                            return;
                        if (Modifier.isStatic(m.getModifiers())) {
                            // TODO
                        } else if (true) {
                            newExpr.replace("{ $_ = $proceed($$);   Instrumentation.Recorder.Recorder.record(this, $_); }");
                        }
                    }

                    public void edit(NewArray newArray) throws CannotCompileException {
                        try {
                            if (!isInterestingClass(newArray.getComponentType().getName()))
                                return;
                        } catch (NotFoundException e) {
                            return;
                        }
                        if (Modifier.isStatic(m.getModifiers())) {
                            // TODO
                        } else {
                            newArray.replace("{ $_ = $proceed($$);   Instrumentation.Recorder.Recorder.record(this, $_); }");
                        }
                    }
                });

            } catch (CannotCompileException e) {
                e.printStackTrace();
            }
        });
        try {
            byte[] byteCode =  cls.toBytecode();
            return byteCode;
        } catch (IOException e) {
            //e.printStackTrace();
        } catch (CannotCompileException e) {
            //e.printStackTrace();
        } finally {
            cls.detach();
        }
        return null;

    }

    private static CtClass getCtClass(String className) throws NotFoundException {
        ClassPool cp = ClassPool.getDefault();
        return cp.get(className.replace("/", "."));
    }
}