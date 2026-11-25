// Listed in META-INF/services/Greeter; nothing else names it.
public class EnglishGreeter implements Greeter {
    private final String text;
    public EnglishGreeter() { text = "hello"; }
    public String greet() { return text; }
}
