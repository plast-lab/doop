import java.util.ServiceLoader;

// ServiceLoader creates the providers listed in META-INF/services with their
// no-arg constructor; with --deepspatch the analysis does the same.
public class Main {
    public static void main(String[] args) {
        for (Greeter g : ServiceLoader.load(Greeter.class))
            System.out.println(g.greet());
    }
}
