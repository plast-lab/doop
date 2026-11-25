// Base entry of a multi-release jar; a JVM of version 11 or later loads
// META-INF/versions/11/Versioned.class instead (src/main/java11).
public class Versioned {
    public String where() { return "base"; }
}
