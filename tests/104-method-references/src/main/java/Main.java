import java.util.function.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.Arrays;

public class Main {

    public static void main(String[] args) {
        // Direct uses of method references.
        Consumer<Integer> c1 = A::meth1234;
        c1.accept(new Integer(10));
        Function<Integer, Integer> c2 = A::meth5678;
        Integer i = c2.apply(new Integer(20));
        System.out.println("i = " + i);

        // Common case: pass method references to streams.
        Integer[] ints = new Integer[3];
        ints[0] = new Integer(10);
        ints[1] = new Integer(20);
        ints[2] = new Integer(30);
        Stream<Integer> is = Arrays.stream(ints);
        System.out.println("== original stream ==");
        is.forEach(A::meth1234);
        Stream<Integer> is1 = Arrays.stream(ints);
        Stream<Integer> is2 = is1.map(A::meth5678);
        System.out.println("== stream.map ==");
        is2.forEach(A::meth1234);
        long count = Arrays.stream(ints).count();
        System.out.println("== stream.count() ==");
        System.out.println(count);

        // Special case: pass method references to streams of
        // primitives (where boxing happens).
        int[] pInts = new int[] { 10, 20, 30};
        IntStream pIs = Arrays.stream(pInts);
        System.out.println("== original stream ==");
        Arrays.stream(pInts).forEach(A::meth1234);
        IntStream pIs1 = Arrays.stream(pInts).map(A::meth5678);
        System.out.println("== stream.map ==");
        pIs1.forEach(A::meth1234);
        int pSum = Arrays.stream(pInts).sum();
        System.out.println("== stream.sum() ==");
        System.out.println(pSum);

        // Instance method references.
        A a = new A();
        Function<Integer, Integer> c3 = a::meth99;
        Integer i3 = c3.apply(new Integer(20));
        System.out.println("i3 = " + i3);

        // Method references that return allocations.
        Supplier<Integer> c4 = A::createSomeInt;
        Integer i4 = c4.get();

        // Constructor references.
        Supplier<A> c5 = A::new;
        A a5 = c5.get();

        // Taken from https://docs.oracle.com/javase/tutorial/java/javaOO/methodreferences.html
        MethodReferencesTest.main();
    }
}

class A {

    private Integer x;

    public A() {
        System.out.println("new A: this.x := 7");
        this.x = new Integer(7);
    }

    public static void meth1234(Integer x) {
        // (new RuntimeException("test")).printStackTrace();
        System.out.println("x = " + x);
    }
    public static Integer meth5678(Integer x) {
        // (new RuntimeException("test")).printStackTrace();
        System.out.println("x := " + x);
        return x;
    }

    public static Integer createSomeInt() {
        System.out.println("Creating some integer...");
        return new Integer(12);
    }

    public Integer meth99(Integer x) {
        // (new RuntimeException("test")).printStackTrace();
        this.x = x;
        System.out.println("meth99: this.x := " + x);
        return x;
    }
}
