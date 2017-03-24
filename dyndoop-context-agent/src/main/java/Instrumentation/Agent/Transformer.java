package Instrumentation.Agent;

import javassist.expr.*;
import org.objectweb.asm.ClassReader;
import javassist.*;

import java.io.*;

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
        CtClass cls = null;
        try {
            cls = getCtClass(className);
        } catch (Exception e) {
            return null;
        }
        if (isLibraryClass(className)) return null;

        Arrays.stream(cls.getDeclaredMethods()).forEach((CtMethod m) -> {
            try {
                m.instrument(new ExprEditor() {
                    public void edit(NewExpr newExpr) throws CannotCompileException {
                        if (!isInterestingClass(newExpr.getClassName()))
                            return;
                        if (Modifier.isStatic(m.getModifiers())) {
                            newExpr.replace("{ $_ = $proceed($$);   Instrumentation.Recorder.Recorder.recordStatic($_); }");
                        } else {
                            newExpr.replace("{ $_ = $proceed($$);   Instrumentation.Recorder.Recorder.record(this, $_); }");
                        }
                    }

                    public void edit(MethodCall call) throws CannotCompileException {
                        try {
                            if (!Modifier.isStatic(call.getMethod().getModifiers()))
                                return;
                            if (Modifier.isStatic(m.getModifiers())) {
                                call.replace(" { Instrumentation.Recorder.Recorder.mergeStatic(); $_ = $proceed($$); }");
                            } else {
                                call.replace(" { Instrumentation.Recorder.Recorder.merge(this); $_ = $proceed($$); }");
                            }
                        } catch (NotFoundException e) {
                            return;
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
                            newArray.replace("{ $_ = $proceed($$);   Instrumentation.Recorder.Recorder.recordStatic($_); }");

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
        } catch (IOException | CannotCompileException e) {
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