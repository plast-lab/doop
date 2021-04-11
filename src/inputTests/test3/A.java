import java.util.Scanner;

public class A {
	public static void main(String args[]){
		Scanner scanner = new Scanner(System.in);
		// TODO fixed size arrays not working in SOOT
		int[] array = new int[10];
		for (int i=0; i<10; i++) {
			array[i] = scanner.nextInt();
		}
		int x = 1;
		int y = 3;
		array[x] = 12345;
		array[y] = 98765;
		int z = array[1];
		//array[1] = 12345;
		//array[3] = 98765;
		System.out.println(z);
	}
}
