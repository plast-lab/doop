import java.util.Scanner;

class B {
	int x ;
}

class Main {

	public static void main(String[] args) {

		char[] FOO1D = new char[10];
		int bla = 4;
		char[] FOO1Dv = new char[bla];

		short FOO4D[][][][] = new short[2][3][4][5];
		short FOO3Dn[][][] = new short[3][3][];

		char[][] FOO = new char[2][];
		FOO[0] = new char[3];
		FOO[1] = new char[4];
		int ble = bla + 4;

		char[][][] BAR = new char[2][4][5];

		char[][][] BABBAR = new char[10][ble][4];
		BABBAR[1][3][ble] = '*';

		//Create a 3x3 array that represents our tic tac toe board
		char[][] board = new char[3][3];

		//Initialize our board with dashes (empty positions)
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				board[i][j] = '-';
			}
		}
	}
}