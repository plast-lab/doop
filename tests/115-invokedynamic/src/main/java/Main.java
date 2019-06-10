import java.io.*;
import java.lang.invoke.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.Handle;
import org.objectweb.asm.commons.*;

public class Main {
    public static void main(String[] args) {
        try {
            test1();
            test2();
            test3();
            test4();
            test5();
            test6();
            test7();
            testInvokedynamic(new A());
            (new A()).test();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    // Taken from original invokedynamic paper, broken down into steps.
    public static void test1() throws Throwable {
        System.out.println("== test1 ==");
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType mType = MethodType.methodType(void.class, String.class);
        System.out.println("mType = " + mType);
        MethodHandle println = lookup.findVirtual(PrintStream.class, "println", mType);
        info(println, mType);
        println.invokeExact(System.out, "hello, world");

        int pos = 0;  // receiver in leading position
        MethodHandle println2out = MethodHandles.insertArguments(println, pos, System.out);
        println2out.invokeExact("hello, world");
    }

    // Test for instance method handles and <methodType(Class)>.
    public static void test2() throws Throwable {
        System.out.println("== test2 ==");
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType mType = MethodType.methodType(Integer.class);
        MethodHandle staticMeth_mh = lookup.findStatic(A.class, "staticMeth", mType);
        info(staticMeth_mh, mType);
        Integer ret = (Integer)staticMeth_mh.invokeExact();
        System.out.println("Return value: " + ret);
        System.out.println("filler for Android dx conversion");
    }

    // Test for instance method handles and <methodType(Class, Class)>.
    public static void test3() throws Throwable {
        System.out.println("== test3 ==");
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType mType = MethodType.methodType(void.class, Integer.class);
        MethodHandle methI_mh = lookup.findVirtual(A.class, "methI", mType);
        info(methI_mh, mType);
        methI_mh.invokeExact(new A(), new Integer(42));
    }

    // Test for method handles with non-void types and <methodType(Class, Class)>.
    public static void test4() throws Throwable {
        System.out.println("== test4 ==");
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType mType = MethodType.methodType(Double.class, Double.class);
        MethodHandle methDD_mh4 = lookup.findVirtual(A.class, "doubleIdentity", mType);
        info(methDD_mh4, mType);
        Double d4_1 = (Double)methDD_mh4.invokeExact(new A(), new Double(42.0));
        System.out.println("test4() | Result#1: " + d4_1);
        double d4_2 = (Double)methDD_mh4.invoke(new A(), 42.0);
        System.out.println("test4() | Result#2: " + d4_2);
    }

    // Test for <methodType(Class, Class[])>.
    public static void test5() throws Throwable {
        System.out.println("== test5 ==");
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Class[] pTypes = new Class[] { Double.class };
        MethodType mType = MethodType.methodType(Double.class, pTypes);
        MethodHandle methDD_mh5 = lookup.findVirtual(A.class, "doubleIdentity", mType);
        info(methDD_mh5, mType);
        Double d5 = (Double)methDD_mh5.invokeExact(new A(), new Double(12.0));
        System.out.println("test5() | Result: " + d5);
    }

    // Test for <methodType(Class, Class, Class...)> and <methodType(Class, List)>.
    public static void test6() throws Throwable {
        System.out.println("== test6 ==");

        MethodType mType1 = MethodType.methodType(Double.class, Integer.class, Float.class, Short.class);
        test6_aux(mType1);

        List<Class<?> > pTypes = new LinkedList<>();
        pTypes.add(Integer.class);
        pTypes.add(Float.class);
        pTypes.add(Short.class);
        MethodType mType2 = MethodType.methodType(Double.class, pTypes);
        test6_aux(mType2);
    }
    private static void test6_aux(MethodType mType) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        MethodHandle add3_mh6 = lookup.findVirtual(A.class, "add3", mType);
        info(add3_mh6, mType);
        Double d6 = (Double)add3_mh6.invokeExact(new A(), new Integer(11), new Float(112.0), new Short("1"));
        System.out.println("test6() | Result: d6 = " + d6);
    }

    // Test for <methodType(Class, MethodType)>.
    public static void test7() throws Throwable {
        System.out.println("== test7 ==");
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType mType1 = MethodType.methodType(String.class, Double.class);
        MethodType mType2 = MethodType.methodType(Double.class, mType1);
        MethodHandle methDD_mh6 = lookup.findVirtual(A.class, "doubleIdentity", mType2);
        info(methDD_mh6, mType2);
        Double d8 = (Double)methDD_mh6.invokeExact(new A(), new Double(41.0));
        System.out.println("test7() | Result: " + d8);
    }

    public static void info(MethodHandle target, MethodType mType) {
        CallSite cs = new ConstantCallSite(target);
        System.out.println("Call site: [ target = " + target + ", method type = " + target.type() + ", original method type = " + mType);
    }

    public static void testInvokedynamic(A aObj) throws Exception {
        System.out.println("== testInvokedynamic ==");
        TestClassLoader loader = new TestClassLoader();
        byte[] bytes = InvokedynamicGenerator.create();
        System.out.println("Loading: " + InvokedynamicGenerator.invokedynamicClass);
        Class<?> c = loader.defineClass(InvokedynamicGenerator.invokedynamicClass, bytes);
        System.out.println("Loaded.");
        Runnable r = (Runnable) c.getConstructor(A.class).newInstance(aObj);
        System.out.println("Invoking run() on Runnable...");
        r.run();
        System.out.println("Invoked.");
    }
}

class TestClassLoader extends ClassLoader {
    public Class<?> defineClass(final String name, final byte[] b) {
        return defineClass(name, b, 0, b.length);
    }
}

class InvokedynamicGenerator implements Opcodes {
    // Don't use packages, just top-level class names.
    static final String invokedynamicClass = "InvokedynamicClass";

    // Adapted from: https://gist.github.com/spullara/1519842
    public static byte[] create() throws Exception {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        cw.visit(V1_7, ACC_PUBLIC + ACC_SUPER, invokedynamicClass, null, "java/lang/Object",
                 new String[]{"java/lang/Runnable"});

        cw.visitSource(invokedynamicClass + ".java", null);

        {
            FieldVisitor fv = cw.visitField(ACC_PRIVATE, "obj", "LA;", null, null);
            fv.visitEnd();
        }
        {
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(LA;)V", null, null);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitLineNumber(7, l0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitLineNumber(8, l1);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitFieldInsn(PUTFIELD, invokedynamicClass, "obj", "LA;");
            Label l2 = new Label();
            mv.visitLabel(l2);
            mv.visitLineNumber(9, l2);
            mv.visitInsn(RETURN);
            Label l3 = new Label();
            mv.visitLabel(l3);
            mv.visitLocalVariable("this", "L"+invokedynamicClass+";", null, l0, l3, 0);
            mv.visitLocalVariable("obj", "LA;", null, l0, l3, 1);
            mv.visitMaxs(2, 2);
            mv.visitEnd();
        }

        String mt0 = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
                                           MethodType.class).toMethodDescriptorString();
        Handle BOOTSTRAP_METHOD = new Handle(H_INVOKESTATIC, "A", "bootstrap", mt0);
        Handle BOOTSTRAP_METHOD_2 = new Handle(H_INVOKESTATIC, "A", "bootstrap2", mt0);
        Handle BOOTSTRAP_METHOD_3 = new Handle(H_INVOKESTATIC, "A", "bootstrap3", mt0);
        Handle BOOTSTRAP_METHOD_4 = new Handle(H_INVOKESTATIC, "A", "bootstrap4", mt0);
        {
            GeneratorAdapter ga = new GeneratorAdapter(ACC_PUBLIC, Method.getMethod("void run ()"), null, null, cw);
            Type owner = Type.getType("L"+invokedynamicClass+";");
            Type typeA = Type.getType(A.class);

            ga.loadThis();
            ga.getField(owner, "obj", typeA);
            ga.invokeDynamic("print", "(LA;)V", BOOTSTRAP_METHOD);

            ga.loadThis();
            ga.getField(owner, "obj", typeA);
            ga.invokeDynamic("print", "(LA;)V", BOOTSTRAP_METHOD_2);

            ga.loadThis();
            ga.getField(owner, "obj", typeA);
            ga.invokeDynamic("print", "(LA;)V", BOOTSTRAP_METHOD_3);

            ga.loadThis();
            ga.getField(owner, "obj", typeA);
            ga.dup();
            ga.invokeDynamic("print2", "(LA;LA;)V", BOOTSTRAP_METHOD_4);

            ga.returnValue();
            ga.endMethod();
        }
        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
        String file = "build/classes/java/main/" + invokedynamicClass + ".class";
        System.out.println("Invokedynamic bytecode saved: " + file);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(bytes);
        }
        return bytes;
    }
}
