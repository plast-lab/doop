import java.util.Scanner;

public class A {
	public static void main(String args[]){
		Scanner scanner = new Scanner(System.in);
		int size = Integer.parseInt(args[1]);
		int[] array = new int[size];
		System.out.println("Enter the elements:");
		for (int i=0; i<size; i++) {
			array[i] = scanner.nextInt();
		}
		int sum = 0;
		for (int num : array) {
			sum = sum+num;
		}
		System.out.println("Sum of array elements is:"+sum);
	}
}
