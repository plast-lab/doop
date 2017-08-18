package Instrumentation.Agent;

import javassist.bytecode.ClassFile;
import javassist.expr.*;
import javassist.*;

import java.io.*;

import java.lang.instrument.*;
import java.security.ProtectionDomain;
import java.util.Arrays;

public class Transformer implements ClassFileTransformer {

    private boolean optInstrumentCGE = true;
    public Transformer(boolean optInstrumentCGE) {
        this.optInstrumentCGE = optInstrumentCGE;
    }

    public static synchronized void premain(String args, Instrumentation inst) throws ClassNotFoundException, IOException, NotFoundException {
        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath("/home/neville/doop-benchmarks/dacapo-bach/avrora.jar");
        cp.insertClassPath("/home/neville/doop-benchmarks/dacapo-bach/avrora-deps.jar");
        inst.addTransformer(new Transformer(
                args.contains("cg")
        ));

    }

    private static boolean isLibraryClass(String name) {
        return name == null || name.startsWith("java") || name.startsWith("Instrumentation") || name.startsWith("sun");
    }

    private static boolean isInterestingClass(String name) {
        if (name.startsWith("javassist"))
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
        if (isLibraryClass(className)) return null;
        try {
            ClassPool cp = ClassPool.getDefault();
            cp.appendClassPath("/home/neville/doop-benchmarks/dacapo-bach/avrora.jar");
            cp.appendClassPath("/home/neville/doop-benchmarks/dacapo-bach/avrora-deps.jar");
            //cp.insertClassPath(new ByteArrayClassPath(className.replace("/","."), classFile));
            cls = cp.get(className.replace("/","."));

            //cls = getCtClass(className);
        } catch (Throwable e) {
            //e.printStackTrace();
            return null;
        }

        final CtClass finalCls = cls;

        Arrays.stream(cls.getDeclaredMethods()).forEach((CtMethod m) -> {
            try {
                if (Modifier.isNative(m.getModifiers()))
                    return;
                if (!Modifier.isStatic(m.getModifiers()) && !Modifier.isAbstract(m.getModifiers()) && optInstrumentCGE) {
                    m.insertBefore("Instrumentation.Recorder.Recorder.recordCall($0);");
                }

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
                        if (Modifier.isStatic(m.getModifiers())) {
                            call.replace(" { Instrumentation.Recorder.Recorder.mergeStatic(); $_ = $proceed($$); }");
                        } else {
                            call.replace(" { Instrumentation.Recorder.Recorder.merge(this); $_ = $proceed($$); }");
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


            } catch (Exception e) {
                // fail silently
                //e.printStackTrace();
            }
        });
        try {
            cls.debugWriteFile("tmp");
            //System.out.println(cls.getName());
            return cls.toBytecode();
        } catch (Exception e) {
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