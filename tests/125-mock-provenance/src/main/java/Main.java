// The environment supplies main's String[] argument and its elements: the
// analysis mocks them, and every mock object must say why it exists.
public class Main {
    public static void main(String[] args) {
        for (String a : args)
            System.out.println(a.length());
    }
}
