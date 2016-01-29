package deepdoop.datalog;

import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

public class ExprElement implements IElement {

	public enum Operator {
		PLUS("+"), MINUS("-"), MULT("*"), DIV("/");

		private String _op;

		Operator(String op) {
			_op = op;
		}

		@Override
		public String toString() {
			return _op;
		}
	}

	ExprElement _left;
	Operator _op;
	ExprElement _right;

	ExprElement _expr;

	Variable _var;

	FunctionalHeadElement _funcHead;

	String _constant;


	public ExprElement(ExprElement left, Operator op, ExprElement right) {
		_left = left;
		_op = op;
		_right = right;
	}
	public ExprElement(ExprElement expr) {
		_expr = expr;
	}
	public ExprElement(Variable var) {
	   _var = var;
	}
	public ExprElement(FunctionalHeadElement funcHead) {
		_funcHead = funcHead;
	}
	public ExprElement(String constant) {
		_constant = constant;
	}

	@Override
	public void normalize() {}

	@Override
	public String toString() {
		if (_op != null) return _left + " " + _op + " " + _right;
		else if (_expr != null) return "(" + _expr + ")";
		else if (_var != null) return _var.toString();
		else if (_funcHead != null) return _funcHead.toString();
		else if (_constant != null) return _constant;
		else throw new RuntimeException("Expression problem");
	}
}
