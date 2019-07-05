/* Test reflection (with heapdl help):

   - In an analysis without reflection support/heapdl: if we only
     call constructors via reflection, they should be shown as unreachable.

   - In an analysis without reflection support but with heapdl: heapdl
     finds allocation by NativeConstructorAccessorImpl.newInstance0(),
     what information is shown? Check var-points-to for 'a5' in
     ReflectionTest.main() and 'this' in A.print().

  Also test for bug CLUE-320.
*/

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.ClassLoader;
import java.security.SecureClassLoader;

public class ReflectionTest {
    public static void main(String[] args) {

        // A a1 = new A();
        // a1.print();
        // A a2 = new A(new Integer(3), new B());
        // a2.print();

        try {
            Class<A> cA = A.class;
            Constructor<A> constr = cA.getConstructor();
            A a3 = (A)cA.newInstance();
            a3.print();
            A a3_2 = constr.newInstance();
            a3_2.print();
            // A a4 = (A)cA.newInstance();
            // a4.print();
            Class<A> cA_2 = (Class<A>)Class.forName("A");
            String cA_2_name = cA_2.getName();
            System.out.println("Class name: " + cA_2_name);
            Constructor<A> constr2 = cA_2.getConstructor(Integer.class, B.class);
            A a5 = constr2.newInstance(new Integer(4), new B());
            a5.print();

            Field iField = cA.getDeclaredField("i");
            Object iFieldVal = iField.get(a3);
            Integer iFieldValInt = (Integer)iFieldVal;
            System.out.println("i = " + iFieldValInt);

            Field bField = cA.getDeclaredField("b_very_long_identifier_does_it_work");
            Object bFieldVal = bField.get(a3);
            B bFieldValB = (B)bFieldVal;
            System.out.println("b.hashCode() = " + bFieldValB.hashCode());

            Method print = cA.getDeclaredMethod("print");
            print.invoke(a3);

            String simple1 = cA.getSimpleName();
            System.out.println("simple1 = " + simple1);
            String simple2 = String.class.getSimpleName();
            System.out.println("simple2 = " + simple2);

            String canonical1 = cA.getCanonicalName();
            System.out.println("canonical1 = " + canonical1);
            String canonical2 = String.class.getCanonicalName();
            System.out.println("canonical2 = " + canonical2);

            // The following code will crash at runtime -- it is only
            // useful for static analysis tests.
            MyClassLoader loader1 = new MyClassLoader();
            MySecureClassLoader loader2 = new MySecureClassLoader();
            try {
                Class definedClass1 = loader1.testDefineClass1();
                System.out.println("defined: " + definedClass1);
                Class definedClass2 = loader1.testDefineClass2();
                System.out.println("defined: " + definedClass2);
                Class definedClass3 = loader1.testDefineClass3();
                System.out.println("defined: " + definedClass3);
                Class loadedClass1 = loader1.testFindLoadedClass();
                System.out.println("loaded: " + loadedClass1);
                Class loadedClass2 = loader1.testLoadClass1();
                System.out.println("loaded: " + loadedClass2);
                Class loadedClass3 = loader1.testLoadClass2();
                System.out.println("loaded: " + loadedClass3);
                Class systemClass = loader1.testFindSystemClass();
                System.out.println("system class: " + systemClass);
                Class definedClass4 = loader2.testDefineClass1();
                System.out.println("defined: " + definedClass4);
                Class definedClass5 = loader2.testDefineClass2();
                System.out.println("defined: " + definedClass5);

            } catch(Exception ex) {
                System.out.println("Exception during class loading!");
                ex.printStackTrace();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        testProxies();
    }

    static void testProxies() {
        D d = new D();
        Class<?> dClass = d.getClass();
        ClassLoader dLoader = dClass.getClassLoader();
        Class<?>[] dIntfs = dClass.getInterfaces();
        G g = (G)Proxy.newProxyInstance(dLoader, dIntfs, new DHandler(d));
        g.report("Hello from report().");
        // Test calls to interface methods: they should be proxied.
        System.out.println("Called count: " + g.count());
        Integer gCount = g.countInteger();
        System.out.println("Called countInteger: " + gCount);
        System.out.println("Called mult: " + g.mult(1.2f, 2.1f));
        // Test call to final Object method: it should not be proxied.
        Class<?> gClass = g.getClass();
        System.out.println("gClass = " + gClass.getName());
        // Test call to Object method: it should be proxied.
        boolean eq = g.equals(new Object());
        System.out.println("equals test : " + eq);
    }
}

class A {
    Integer i;
    B b_very_long_identifier_does_it_work;
    public A() {
        this.i = new Integer(0);
        this.b_very_long_identifier_does_it_work = new B();
    }
    public A(Integer i, B b) {
        this.i = i;
        this.b_very_long_identifier_does_it_work = b;
    }
    public void print() {
        System.out.println("i = " + i + ", b = " + b_very_long_identifier_does_it_work);
    }
}

class B { }

class SomeRandomClass { }

class MyClassLoader extends ClassLoader {

    // <java.lang.ClassLoader: java.lang.Class defineClass(java.lang.String,byte[],int,int)>
    public Class testDefineClass1() {
        Class r = defineClass("SomeRandomClass", (byte[])null, 0, 0);
        return r;
    }

    // <java.lang.ClassLoader: java.lang.Class defineClass(java.lang.String,byte[],int,int,java.security.ProtectionDomain)>
    public Class testDefineClass2() {
        Class r = defineClass("SomeRandomClass", null, 0, 0, (java.security.ProtectionDomain)null);
        return r;
    }

    // <java.lang.ClassLoader: java.lang.Class defineClass(java.lang.String,java.nio.ByteBuffer,java.security.ProtectionDomain)>
    public Class testDefineClass3() {
        Class r = defineClass("SomeRandomClass", null, (java.security.ProtectionDomain)null);
        return r;
    }

    // <java.lang.ClassLoader: java.lang.Class findLoadedClass(java.lang.String)>
    public Class testFindLoadedClass() {
        Class r = findLoadedClass("SomeRandomClass");
        return r;
    }

    // <java.lang.ClassLoader: java.lang.Class findSystemClass(java.lang.String)>
    public Class testFindSystemClass() throws ClassNotFoundException {
        Class r = findSystemClass("SomeRandomClass");
        return r;
    }

    // <java.lang.ClassLoader: java.lang.Class loadClass(java.lang.String)>"
    public Class testLoadClass1() throws ClassNotFoundException {
        Class r = loadClass("SomeRandomClass");
        return r;
    }

    // <java.lang.ClassLoader: java.lang.Class loadClass(java.lang.String,boolean)>
    public Class testLoadClass2() throws ClassNotFoundException {
        Class r = loadClass("SomeRandomClass", true);
        return r;
    }
}

class MySecureClassLoader extends SecureClassLoader {

    // <java.security.SecureClassLoader: java.lang.Class defineClass(java.lang.String,byte[],int,int,java.security.CodeSource)>
    public Class testDefineClass1() {
        Class r = defineClass("SomeRandomClass", null, 0, 0, (java.security.CodeSource)null);
        return r;
    }

    // <java.security.SecureClassLoader: java.lang.Class defineClass(java.lang.String,java.nio.ByteBuffer,java.security.CodeSource)>
    public Class testDefineClass2() {
        Class r = defineClass("SomeRandomClass", (java.nio.ByteBuffer)null, (java.security.CodeSource)null);
        return r;
    }
}

interface G {
    // Test for static/final interface field.
    // static final Integer i = new Integer(10);
    // Test for Java 8 static method.
    // static int staticMeth() { return 42; }
    void report(String c);
    int count();
    Integer countInteger();
    float mult(float x, float y);
}

class D implements G {
    public void report(String c) {
        System.out.println(c);
    }
    public int count() {
	return 42;
    }
    public Integer countInteger() {
	return new Integer(43);
    }
    public float mult(float x, float y) {
	return x * y;
    }
}

class DHandler implements InvocationHandler {
    private D d;
    public DHandler(D d) { this.d = d; }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
	String mn = method.getName();
        System.out.println("The invocation handler was called for '" + mn + "'...");
        if (mn.equals("report") && (args.length==1) && args[0] instanceof String) {
            // Two ways to call the wrapped method, choice doesn't
            // affect analysis:
            // (1) Call method via Method object.
            // method.invoke(a, args);
            // (2) Call method with its name and a typed receiver.
            d.report((String)args[0]);
	    return null;
        }
	else if (mn.equals("count") && ((args==null) || (args.length == 0)))
	    return d.count();
	else if (mn.equals("countInteger") && ((args==null) || (args.length == 0)))
	    return d.countInteger();
	else if (mn.equals("mult") && ((args!=null) && (args.length == 2))) {
	    float x = ((Float)args[0]).floatValue();
	    float y = ((Float)args[1]).floatValue();
	    return d.mult(x, y);
	}
	else if (mn.equals("hashCode") && (args==null || args.length == 0))
	    return System.identityHashCode(proxy);
    else if (mn.equals("equals") && args!=null && args.length == 1)
        return System.identityHashCode(proxy) == System.identityHashCode(args[0]);
	if (method == null)
	    System.out.println("WARNING: Null method paramer, " + proxy.hashCode());
	else if (args == null)
	    System.out.println("WARNING: Null args parameter for " +
			       method.getName() + ".");
	else
	    System.out.println("WARNING: Cannot handle method: " +
			       method.getName() + ", arity: " + args.length);
        return null;
    }
}
