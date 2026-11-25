package app;

// Packaged under WEB-INF/classes (123-deployables.war) and BOOT-INF/classes
// (123-deployables-boot.jar): application code, as the class loader sees it.
public class Hello {
    public static void main(String[] args) {
        System.out.println(lib.Util.greeting());
    }
}
