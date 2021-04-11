class C {
	C parent;
	int age;
	String name;
}

class B {
	C[] employees;
	int total;
	static A wrapper;
	static String args[];
}

public class A {
	public static void main(String args[]){
		B.wrapper = new A();
		B.args = args;
		int i = B.wrapper.foo(15, 30);
		int j = B.wrapper.bar(1.4, 5.66);
		int k = B.wrapper.car(4L, 6L);
		int res = i + (j * k);
		res = res - B.wrapper.testABC(100, 200);
		System.out.println(res);
	}

	int testABC(int a, int b) {
		int ww = 12345;
		int k = 50 + b;
		int z = a * k;
		if ((z & 0x101) > 200) return ww;
		int sum = 0;
		for (int i = 30 ; i >= 10 ; i-=2) sum = sum + i;
		return a/100;
	}

	int foo(int a, int b) {
		assert a > 10;
		boolean fl = true;
		int y = a + 10;
		fl = y > a && a > 15;
		boolean fl2 = !fl;
		System.out.println("fl2 = " + fl2);
		boolean ff = a < b;
		boolean bar = !(a > 32);
		if (ff) return 42 + ~a;
		return ~(~b);
	}

	int bar(double a, double b) {
		boolean fl = a < b;
		boolean ff = b <= a;
		if (fl) return 42;
		return 0;
	}

	int car(long a, long b) {
		boolean fl = a < b;
		long c = ~a;
		System.out.println(c);
		abc(fl);
		if (fl) return 42;
		return 0;
	}

	int abc(boolean a) {
		boolean fl = !a;
		return fl ? 1 : 2;
	}
}
