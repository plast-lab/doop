// Gone is compiled with Main but left out of the jar (see build.sh), so JPhantom
// must generate it: with a single input jar, Doop used to lose the phantom classes.
public class Main {
    public static void main(String[] args) {
        Gone g = new Gone();
        System.out.println(g.value());
    }
}
