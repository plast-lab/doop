// META-INF/versions/11 entry: what a JVM of version 11 or later loads.
public class Versioned {
    public String where() { return onlyIn11(); }
    String onlyIn11() { return "versions/11"; }
}
