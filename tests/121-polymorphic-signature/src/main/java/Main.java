import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

// invokeExact is signature-polymorphic (native, varargs, in MethodHandle);
// type() is an ordinary method of MethodHandle and must not be treated as one.
public class Main {
    static Object id(Object o) { return o; }

    public static void main(String[] args) throws Throwable {
        MethodHandle mh = MethodHandles.lookup().findStatic(Main.class, "id",
                MethodType.methodType(Object.class, Object.class));
        MethodType t = mh.type();
        Object r = (Object) mh.invokeExact((Object) "x");
        System.out.println(t + " " + r);
    }
}
