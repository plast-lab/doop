// Entry point that references com.foo.Bar through its common method whoAmI(),
// so that Bar gets resolved regardless of which version is on the classpath.
// This reference is what triggers resolution of a library-only (-l) Bar in the
// "duplicate only in -l" case.
public class Main {
    public static void main(String[] args) {
        com.foo.Bar b = new com.foo.Bar();
        String s = b.whoAmI();
        System.out.println(s);
    }
}
