// REFLECTION-ONLY use of com.foo.Refl: the class is looked up by name string,
// instantiated reflectively, and a method is invoked reflectively. The class
// name still appears ONLY as a String constant (no CONSTANT_Class_info /
// symbolic type reference), and newInstance()/invoke() are typed as Object, so
// Soot's resolver has no static type edge to com.foo.Refl. Probes whether
// reflective instantiation+invocation (without any --reflection* flag) pulls the
// class into the facts.
import java.lang.reflect.Method;

public class Main {
    public static void main(String[] args) throws Exception {
        Class<?> c = Class.forName("com.foo.Refl");
        Object o = c.getDeclaredConstructor().newInstance();
        Method m = c.getMethod("reflMarker");
        m.invoke(o);
        System.out.println(o);
    }
}
