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
		Scanner in = new Scanner(System.in);
		int aa = in.nextInt();
		int bb = in.nextInt();
		BAR[aa][bla][bb] = '$';

		char[][][] BABBAR = new char[10][ble][40];
		BABBAR[1][3][ble] = '*';
		BABBAR[1][5][ble] = 'A';
		BABBAR[1][5][9] = '?';
		BABBAR[2][in.nextInt()][3] = in.next().charAt(0);

		//Create a 3x3 array that represents our tic tac toe board
		char[][] board = new char[3][3];

		//Initialize our board with dashes (empty positions)
		for (int i = 0; i < board.length; i++) {
			for (int j = 0; j < board[i].length; j++) {
				board[i][j] = '-';
			}
		}

		char result = playerHasWon(board);
		testfoobar(board);

		foobar1(FOO1D);
		foobar2(FOO1D);
		foobar3(FOO1D);
		foobar4(FOO1D);
		foobar5(BAR);

		printfoo(board);

		blaaa();
	}

	static void blaaa() {
		try {
			int a = 10;
			throw new RuntimeException("" + a);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	static char playerHasWon(char[][] board) {

		//Check each row
		for (int i = 0; i < 3; i++) {
			if (board[i][0] == board[i][1] && board[i][1] == board[i][2] && board[i][0] != '-') {
				return board[i][0];
			}
		}

		//Check each column
		for (int j = 0; j < 3; j++) {
			if (board[0][j] == board[1][j] && board[1][j] == board[2][j] && board[0][j] != '-') {
				return board[0][j];
			}
		}

		//Check the diagonals
		if (board[0][0] == board[1][1] && board[1][1] == board[2][2] && board[0][0] != '-') {
			return board[0][0];
		}
		if (board[2][0] == board[1][1] && board[1][1] == board[0][2] && board[2][0] != '-') {
			return board[2][0];
		}

		//Otherwise nobody has won yet
		return ' ';
	}

	static char testfoobar(char[][] board) {
		int i = 0;
		int ok = 0;
		while (i < 3) {
			if (board[i][0] == '-') ok = 1;
			else if (board[i][1] == '?') ok = 2;
			else if (board[i][2] == '-') ok = 3;
			i++;
		}

		for (i = 0; i < 3; i++) {
			if (board[i][0] == board[i][1] || board[i][1] == board[i][2] || board[i][0] != '-' || board[i][1] != '?') {
				return board[i][0];
			}
		}
		return ' ';
	}

	static void foobar1(char[] myarr) {
		for (int i = 0 ; i < 10 ; i++)
			myarr[i] = 'A' + 1;
	}

	static void foobar2(char[] myarr) {
		for (int j = 0 ; j < myarr.length ; j++)
			myarr[j] = 'a' + 1;
	}

	static void foobar3(char[] myarr) {
		int sum = 0;
		for (int k = 9 ; 0 <= k ; k--)
			sum += myarr[k];
	}

	static void foobar4(char[] myarr) {
		int sum = 0;
		for (char c : myarr)
			sum += c;
	}

	static void foobar5(char[][][] myarr) {
		for (int i = 0; i < myarr.length; i++)
			for (int j = 0; j < myarr[i].length; j++)
				for (int k = 0; k < myarr[i][j].length; k++)
					myarr[i][j][k] = '?';
	}

	static void printfoo(char[][] board) {
		for (int i = 0; i < board.length ; i++) {
			for (int j = 0; j < board[i].length; j++) {
				System.out.println(board[i][j]);
			}
		}
	}
}