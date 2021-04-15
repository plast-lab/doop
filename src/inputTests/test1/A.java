import java.util.Scanner;

public class A {
	public static void main(String args[]){
		Scanner scanner = new Scanner(System.in);
		int size = Integer.parseInt(args[1]);
		int[] array = new int[size];
		for (int i=0; i<size; i++) {
			array[i] = scanner.nextInt();
		}
		array[1] = 12345;
		array[3] = 98765;
		int sum = 0;
		for (int num : array) {
			sum = sum+num;
		}
		System.out.println("Sum of array elements is:"+sum);
	}
}
