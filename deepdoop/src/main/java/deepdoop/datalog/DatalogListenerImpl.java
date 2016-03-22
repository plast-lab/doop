package deepdoop.datalog;

import static deepdoop.datalog.DatalogParser.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.antlr.v4.runtime.tree.TerminalNode;

class DatalogListenerImpl implements DatalogListener {

	enum ClauseType { DECLARATION, CONSTRAINT, RULE, UNDEF }

	Set<Predicate> _predicates;
	Set<Predicate> _specialPredicates;
	Set<Rule> _rules;
	Program _program;

	ParseTreeProperty<String> _name;
	ParseTreeProperty<List<String>> _names;
	ParseTreeProperty<Predicate> _pred;
	ParseTreeProperty<List<Predicate>> _preds;
	ParseTreeProperty<IExpr> _expr;
	ParseTreeProperty<List<IExpr>> _exprs;
	ParseTreeProperty<IElement> _elem;
	ParseTreeProperty<List<IElement>> _elems;
	ClauseType _currentClause;

	public DatalogListenerImpl() {
		_predicates = new HashSet<>();
		_specialPredicates = new HashSet<>();
		_rules = new HashSet<>();

		_name = new ParseTreeProperty<>();
		_names = new ParseTreeProperty<>();
		_pred = new ParseTreeProperty<>();
		_preds = new ParseTreeProperty<>();
		_expr = new ParseTreeProperty<>();
		_exprs = new ParseTreeProperty<>();
		_elem = new ParseTreeProperty<>();
		_elems = new ParseTreeProperty<>();
		_currentClause = ClauseType.UNDEF;
	}

	public Program getProgram() {
		return _program;
	}

	public void enterProgram(ProgramContext ctx) {}
	public void exitProgram(ProgramContext ctx) {
		_program = new Program(_predicates, _specialPredicates, _rules);
	}
	public void enterDeclaration(DeclarationContext ctx) {
		_currentClause = ClauseType.DECLARATION;
	}
	public void exitDeclaration(DeclarationContext ctx) {
		_currentClause = ClauseType.UNDEF;

		Predicate pred = get(_pred, ctx.predicate());
		if (ctx.refmode() == null) {
			List<Predicate> preds = get(_preds, ctx.predicateList());
			if (preds != null) {
				List<String> types = new ArrayList<>();
				for (Predicate p : preds)
					types.add(p.getName());
				pred.setTypes(types);
				_predicates.add(pred);
			}
			else {
				pred = new Entity(pred.getName());
				_specialPredicates.add(new Entity(pred.getName()));
			}
		}
		else {
			Entity ent = new Entity(get(_name, ctx.predicateName()));
			_specialPredicates.add(ent);
			String refName = get(_name, ctx.refmode());
			String refType = pred.getName();
			RefMode ref = new RefMode(refName, refType, ent);
			_specialPredicates.add(ref);
		}
	}
	public void enterConstraint(ConstraintContext ctx) {}
	public void exitConstraint(ConstraintContext ctx) {}
	public void enterRule_(Rule_Context ctx) {}
	public void exitRule_(Rule_Context ctx) {
		if (ctx.predicateList() != null) {
			LogicalElement head = new LogicalElement(true, get(_elems, ctx.predicateList()));
			IElement body = get(_elem, ctx.ruleBody());
			if (body != null) body.normalize();
			_rules.add(new Rule(head, body));
		} else {
			IElement head = get(_elem, ctx.predicate());
			AggregationElement aggregation = (AggregationElement) get(_elem, ctx.aggregation());
			_rules.add(new Rule(head, aggregation));
		}
	}
	public void enterPredicate(PredicateContext ctx) {}
	public void exitPredicate(PredicateContext ctx) {
		/* "normal" predicates | primitive w/o capacity | directive w/o parameters */
		if (ctx.predicateName() != null && ctx.CAPACITY() == null && ctx.BACKTICK() == null) {
		}

		/* primitive types */
		else if (ctx.predicateName() != null && ctx.CAPACITY() != null) {
		}

		/* directives */
		else if (ctx.predicateName() != null && ctx.BACKTICK() != null) {
		}

		/* functional predicates | directive w/o parameters */
		else if (ctx.functionalHead() != null) {
		}

		/* refmode predicates */
		else if (ctx.refmode() != null) {
		}






		if (_currentClause == ClauseType.DECLARATION) {
			Predicate p;
			if (ctx.predicateName() != null) {
				String name = get(_name, ctx.predicateName());
				if (isPrimitive(name))
					p = new Entity(normalizePrimitive(name, ctx.CAPACITY()));
				else
					p = new Predicate(name, null);
			}
			else if (ctx.functionalHead() != null)
				p = new Functional(get(_name, ctx.functionalHead()), null, null);
			else /*if (ctx.refmode() != null)*/
				throw new RuntimeException ("Refmode in declaration has separate handling in grammar");
			_pred.put(ctx, p);
		}
		else {
			PredicateElement p;
			if (ctx.predicateName() != null) {
				String name = normalizePrimitive(get(_name, ctx.predicateName()), ctx.CAPACITY());
				List<IExpr> exprs = (ctx.exprList() == null ? exprs = new ArrayList<>() : get(_exprs, ctx.exprList()));
				p = new PredicateElement(name, exprs);
			}
			else if (ctx.functionalHead() != null) {
				String name = get(_name, ctx.functionalHead());
				List<IExpr> exprs = get(_exprs, ctx.functionalHead());
				p = new FunctionalElement(name, exprs, get(_expr, ctx.expr()));
			}
			else /*if (ctx.refmode() != null)*/ {
				String name = get(_name, ctx.refmode());
				List<IExpr> exprs = get(_exprs, ctx.refmode());
				p = new RefModeElement(name, (VariableExpr)exprs.get(0), exprs.get(1));
			}
			_elem.put(ctx, p);
		}
	}
	public void enterRuleBody(RuleBodyContext ctx) {}
	public void exitRuleBody(RuleBodyContext ctx) {
		String token = getToken(ctx, 0);
		if (ctx.predicate() != null) {
			_elem.put(ctx, get(_elem, ctx.predicate()));
		} else if (ctx.comparison() != null) {
			_elem.put(ctx, get(_elem, ctx.comparison()));
		} else if (token.equals("(")) {
			_elem.put(ctx, new GroupElement(get(_elem, ctx.ruleBody(0))));
		} else if (token.equals(",") || token.equals(";")) {
			List<IElement> list = Arrays.asList(
				get(_elem, ctx.ruleBody(0)),
				get(_elem, ctx.ruleBody(1)));

			_elem.put(ctx, new LogicalElement(token.equals(","), list));
		} else if (token.equals("!")) {
			_elem.put(ctx, new NegationElement(get(_elem, ctx.ruleBody(0))));
		}
	}
	public void enterAggregation(AggregationContext ctx) {}
	public void exitAggregation(AggregationContext ctx) {
		VariableExpr variable = new VariableExpr(ctx.IDENTIFIER().getText());
		PredicateElement predicate = (PredicateElement) get(_elem, ctx.predicate());
		IElement body = get(_elem, ctx.ruleBody());
		_elem.put(ctx, new AggregationElement(variable, predicate, body));
	}
	public void enterRefmode(RefmodeContext ctx) {}
	public void exitRefmode(RefmodeContext ctx) {
		_name.put(ctx, get(_name, ctx.predicateName()));
		List<IExpr> list = new ArrayList<>();
		list.add(new VariableExpr(ctx.IDENTIFIER().getText()));
		list.add(get(_expr, ctx.expr()));
		_exprs.put(ctx, list);
	}
	public void enterFunctionalHead(FunctionalHeadContext ctx) {}
	public void exitFunctionalHead(FunctionalHeadContext ctx) {
		_name.put(ctx, get(_name, ctx.predicateName()));
		if (ctx.exprList() != null)
			_exprs.put(ctx, get(_exprs, ctx.exprList()));
		else
			_exprs.put(ctx, new ArrayList<IExpr>());
	}
	public void enterPredicateName(PredicateNameContext ctx) {}
	public void exitPredicateName(PredicateNameContext ctx) {
		PredicateNameContext child = ctx.predicateName();
		String name = ctx.IDENTIFIER().getText();
		if (child != null)
			name = get(_name, child) + ":" + name;
		_name.put(ctx, name);
	}
	public void enterConstant(ConstantContext ctx) {}
	public void exitConstant(ConstantContext ctx) {
		ConstantExpr e;
		if (ctx.INTEGER() != null) {
			String str = ctx.INTEGER().getText();
			int base = 10;
			if (str.startsWith("0x") || str.startsWith("0X")) {
				str = str.substring(2);
				base = 16;
			} else if (str.startsWith("0") && str.length() > 1) {
				str = str.substring(1);
				base = 8;
			}
			e = new ConstantExpr(Integer.parseInt(str, base));
		}
		else if (ctx.REAL() != null) e = new ConstantExpr(Double.parseDouble(ctx.REAL().getText()));
		else if (ctx.BOOLEAN() != null) e = new ConstantExpr(Boolean.parseBoolean(ctx.BOOLEAN().getText()));
		else /*if (ctx.STRING() != null)*/ e = new ConstantExpr(ctx.STRING().getText());

		_expr.put(ctx, e);
	}
	public void enterExpr(ExprContext ctx) {}
	public void exitExpr(ExprContext ctx) {
		IExpr e;
		if (ctx.IDENTIFIER() != null)
			e = new VariableExpr(ctx.IDENTIFIER().getText());
		else if (ctx.functionalHead() != null) {
			String name = get(_name, ctx.functionalHead());
			List<IExpr> exprs = get(_exprs, ctx.functionalHead());
			e = new FunctionalHeadExpr(name, exprs);
		}
		else if (ctx.constant() != null)
			e = get(_expr, ctx.constant());
		else {
			List<ExprContext> exprs = ctx.expr();
			if (exprs.size() == 2) {
				IExpr left = get(_expr, exprs.get(0));
				ComplexExpr.Operator op = null;
				switch (getToken(ctx, 0)) {
					case "+": op = ComplexExpr.Operator.PLUS ; break;
					case "-": op = ComplexExpr.Operator.MINUS; break;
					case "*": op = ComplexExpr.Operator.MULT ; break;
					case "/": op = ComplexExpr.Operator.DIV  ; break;
				}
				IExpr right = get(_expr, exprs.get(1));
				e = new ComplexExpr(left, op, right);
			}
			else
				e = new ComplexExpr(get(_expr, exprs.get(0)));
		}

		_expr.put(ctx, e);
	}
	public void enterComparison(ComparisonContext ctx) {}
	public void exitComparison(ComparisonContext ctx) {
		String token = getToken(ctx, 0);
		IExpr left = get(_expr, ctx.expr(0));
		IExpr right = get(_expr, ctx.expr(1));
		ComparisonElement.Operator op = null;
		switch (token) {
			case "=" : op = ComparisonElement.Operator.EQ ; break;
			case "<" : op = ComparisonElement.Operator.LT ; break;
			case "<=": op = ComparisonElement.Operator.LEQ; break;
			case ">" : op = ComparisonElement.Operator.GT ; break;
			case ">=": op = ComparisonElement.Operator.GEQ; break;
			case "!=": op = ComparisonElement.Operator.NEQ; break;
		}

		_elem.put(ctx, new ComparisonElement(left, op, right));
	}
	public void enterPredicateList(PredicateListContext ctx) {}
	public void exitPredicateList(PredicateListContext ctx) {
		if (_inDeclaration) {
			Predicate pred = get(_pred, ctx.predicate());
			List<Predicate> list = get(_preds, ctx.predicateList(), pred);
			_preds.put(ctx, list);
		} else {
			IElement elem = get(_elem, ctx.predicate());
			List<IElement> list = get(_elems, ctx.predicateList(), elem);
			_elems.put(ctx, list);
		}
	}
	public void enterExprList(ExprListContext ctx) {}
	public void exitExprList(ExprListContext ctx) {
		IExpr p = get(_expr, ctx.expr());
		List<IExpr> list = get(_exprs, ctx.exprList(), p);
		_exprs.put(ctx, list);
	}

	public void enterEveryRule(ParserRuleContext ctx) {}
	public void exitEveryRule(ParserRuleContext ctx) {}
	public void visitErrorNode(ErrorNode node) {
		throw new RuntimeException("Parsing error");
	}
	public void visitTerminal(TerminalNode node) {}


	static <T> T get(ParseTreeProperty<T> values, ParseTree node) {
		T t = values.get(node);
		values.removeFrom(node);
		return t;
	}
	static <T> List<T> get(ParseTreeProperty<List<T>> values, ParseTree node, T newValue) {
		List<T> lt;
		if (node != null)
			lt = get(values, node);
		else
			lt = new ArrayList<T>();
		lt.add(newValue);
		return lt;
	}
	static String getToken(ParserRuleContext ctx, int index) {
		for (int i = 0; i < ctx.getChildCount(); i++)
			if (ctx.getChild(i) instanceof TerminalNode && index-- == 0)
				return ((TerminalNode)ctx.getChild(i)).getText();
		return null;
	}
	static boolean isPrimitive(String name) {
		switch (name) {
			case "uint":
			case "int":
			case "float":
			case "decimal":
			case "boolean":
			case "string":
				return true;
			default:
				return false;
		}
	}
	static String normalizePrimitive(String name, TerminalNode capacityNode) {
		switch (name) {
			case "uint":
			case "int":
			case "float":
			case "decimal":
				return name + (capacityNode == null ? "[64]" : capacityNode.getText());
			case "boolean":
			case "string":
			default:
				return name;
		}
	}
}

