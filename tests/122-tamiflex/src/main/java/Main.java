import java.lang.reflect.Method;

// Reflective calls resolved by the TamiFlex log in 122-tamiflex.log (line numbers
// refer to this file).
public class Main {
    public static void main(String[] args) throws Exception {
        Object receiver = args.length > 0 ? new Other() : new Sub();
        // Logged target <Base: void hello()>: dispatches on the receiver's
        // run-time type (Sub.hello), and not on Other, which is not a Base.
        Method hello = Base.class.getMethod("hello");
        hello.invoke(receiver);
        // Logged private target <Base: void secret()>: called as logged, not
        // looked up on the receiver (Sub has a private secret() of its own).
        Method secret = Base.class.getDeclaredMethod("secret");
        secret.setAccessible(true);
        secret.invoke(new Sub());
        // Plugin is in the library jar and named only in the log.
        String name = new StringBuilder("Plu").append("gin").toString();
        Object plugin = Class.forName(name).newInstance();
        System.out.println(plugin);
    }
}

class Base {
    public void hello() { }
    private void secret() { }
}

class Sub extends Base {
    public void hello() { }
    private void secret() { }
}

class Other {
    public void hello() { }
}
