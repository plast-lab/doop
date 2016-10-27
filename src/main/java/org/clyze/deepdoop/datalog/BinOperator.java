package org.clyze.deepdoop.datalog;

public enum BinOperator {
	// Comparisons
	EQ("="), LT("<"), LEQ("<="),
	GT(">"), GEQ(">="), NEQ("!="),
	// Arithmetic
	PLUS("+"), MINUS("-"), MULT("*"), DIV("/");

	private String _op;

	BinOperator(String op) {
		_op = op;
	}

	@Override
	public String toString() {
		return _op;
	}
}
