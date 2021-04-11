import java.util.Scanner;

public class A {
	public static void main(String args[]){
		B b1 = new B(42, -123);
		//B b2 = new B("hello world");
		B b3 = new B(b1);
	}
}

class B {
	int f1;
	int f2;
	String s;

	public B(int x, int y) {
		f1 = y;
		f2 = x;
		s = "dummy";
	}

//	public B(String s) {
//		f1 = s.length();
//		f2 = 100;
//		this.s = s;
//	}

	public B(B b) {
		f1 = b.f1 + b.f2;
		f2 = -1 * f1;
	}
}