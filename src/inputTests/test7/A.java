import java.util.HashMap;
import java.util.Map;

public class A {
	public static void main(String[] args) {
		Map<Integer, Integer> m = new HashMap<>();
		B b = new B1();
		bar(m, b);
	}

	static void bar(Map<Integer, Integer> m0, B b0) {
		m0.put(0, b0.foo());
	}
}

class B {
	public int foo() { System.out.println("B"); return 0; }
}

class B1 extends B {
	@Override
	public int foo() { System.out.println("B1"); return 1; }
}

class B2 extends B {
	@Override
	public int foo() { System.out.println("B2"); return 2; }
}