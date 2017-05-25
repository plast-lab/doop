package org.clyze.deepdoop.datalog

enum BinOperator {
	// Comparisons
	EQ("="), LT("<"), LEQ("<="),
	GT(">"), GEQ(">="), NEQ("!="),
	// Arithmetic
			PLUS("+"), MINUS("-"), MULT("*"), DIV("/")

	private String op

	BinOperator(String op) { this.op = op }

	String toString() { op }
}
