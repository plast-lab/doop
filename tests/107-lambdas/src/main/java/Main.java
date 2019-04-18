import java.util.function.*;
import java.util.stream.IntStream;
import java.util.Arrays;

// Test for task CLUE-54.
public class Main {
    public static void main(String[] args) {

        int[] ints = new int[] { 10, 20, 30};
        IntStream is = Arrays.stream(ints);

        // Simple lambda.
        Function<Integer, String> intWriter = x -> x.toString();
        String stringInt = intWriter.apply(new Integer(10));
        System.out.println("stringInt = " + stringInt);

        // Nested lambdas.
        Function<Integer, Integer> func1 = i -> new Integer(is.map(j -> j + i.intValue()).sum());
        Integer i1 = func1.apply(new Integer(2));
        System.out.println("i1 = " + i1);

        A a = new A();
        a.test();

        main2();
    }
    public static void main2() {
        // Another simple lambda belonging to a 'main' method. This
        // tests the last two parts of "lambda$main$0" names.
        Function<String,Integer> intParser = s -> new Integer(s);
        Integer parsedInt = intParser.apply("47");
        System.out.println("parsedInt = " + parsedInt);
    }
}
class A implements java.io.Serializable {
    public Function<Integer, Integer> intCompare;
    public A() {
        // Closure lambda.
        Integer y = new Integer(12);
        Integer z = new Integer(22);
        this.intCompare = x -> Integer.valueOf(x.compareTo(y) + y.compareTo(z));
    }
    void test() {
        Integer compareRes = this.intCompare.apply(new Integer(10));
        System.out.println("compareRes = " + compareRes);
    }
}
