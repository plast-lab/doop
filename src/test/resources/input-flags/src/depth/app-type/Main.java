// TYPE-ONLY reference to com.foo.Baz: Baz appears only in a field descriptor
// and a method parameter descriptor. No allocation (`new Baz`), no method call.
// This probes the resolution level Soot gives a -l class that is merely named
// as a type by application code.
public class Main {
    static com.foo.Baz bazField;              // field descriptor Lcom/foo/Baz;

    static void takesBaz(com.foo.Baz b) {     // method descriptor references Baz
    }

    public static void main(String[] args) {
        // Intentionally empty: Baz is never allocated or invoked here.
    }
}
