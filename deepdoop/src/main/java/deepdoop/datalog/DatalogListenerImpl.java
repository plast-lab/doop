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
	Set<Predicate> _predicates;
	Set<Predicate> _specialPredicates;
	Set<Rule> _rules;
	Program _program;

	ParseTreeProperty<String> _name;
	ParseTreeProperty<List<String>> _names;
	ParseTreeProperty<Predicate> _pred;
	ParseTreeProperty<List<Predicate>> _preds;
	ParseTreeProperty<IExpr> _param;
	ParseTreeProperty<List<IExpr>> _params;
	ParseTreeProperty<IElement> _elem;
	ParseTreeProperty<List<IElement>> _elems;
	boolean _inDeclaration;

	public DatalogListenerImpl() {
		_predicates = new HashSet<>();
		_specialPredicates = new HashSet<>();
		_rules = new HashSet<>();

		_name = new ParseTreeProperty<>();
		_names = new ParseTreeProperty<>();
		_pred = new ParseTreeProperty<>();
		_preds = new ParseTreeProperty<>();
		_param = new ParseTreeProperty<>();
		_params = new ParseTreeProperty<>();
		_elem = new ParseTreeProperty<>();
		_elems = new ParseTreeProperty<>();
		_inDeclaration = false;
	}

	public Program getProgram() {
		return _program;
	}

	public void enterProgram(ProgramContext ctx) {}
	public void exitProgram(ProgramContext ctx) {
		_program = new Program(_predicates, _specialPredicates, _rules);
	}
	public void enterDeclaration(DeclarationContext ctx) {
		_inDeclaration = true;
	}
	public void exitDeclaration(DeclarationContext ctx) {
		_inDeclaration = false;

		PredicateContext predCtx = ctx.predicate();
		if (predCtx != null) {
			Predicate pred = get(_pred, predCtx);
			List<String> types = new ArrayList<>();
			List<Predicate> preds = get(_preds, ctx.predicateList());
			if (preds != null) {
				for (Predicate p : preds)
					types.add(p.getName());
				pred.setTypes(types);
				_predicates.add(pred);
			} else {
				pred = new Entity(pred.getName());
				_specialPredicates.add(new Entity(pred.getName()));
			}
		} else {
			Entity ent = new Entity(get(_name, ctx.predicateName()));
			_specialPredicates.add(ent);

			String refName = get(_name, ctx.refmode());
			String refType = get(_name, ctx.primitiveType());
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
			aggregation._body = get(_elem, ctx.ruleBody());
			_rules.add(new Rule(head, aggregation));
		}
		org.antlr.v4.runtime.Token first = ctx.getStart();
		int line = first.getLine();
		System.out.println(line);
	}
	public void enterDirective(DirectiveContext ctx) {}
	public void exitDirective(DirectiveContext ctx) {}
	public void enterPredicate(PredicateContext ctx) {}
	public void exitPredicate(PredicateContext ctx) {
		PredicateNameContext predCtx = ctx.predicateName();
		FunctionalHeadContext funcCtx = ctx.functionalHead();
		RefmodeContext refCtx = ctx.refmode();
		PrimitiveTypeContext primCtx = ctx.primitiveType();

		if (_inDeclaration) {
			Predicate pred = null;
			if (predCtx != null) {
				pred = new Predicate(get(_name, predCtx), null);
			} else if (funcCtx != null) {
				pred = new Functional(get(_name, funcCtx), null, null);
			} else if (refCtx != null) {
				throw new RuntimeException ("Refmode in declaration has separate handling");
			} else if (primCtx != null) {
				pred = new Entity(get(_name, primCtx));
			}
			_pred.put(ctx, pred);
		} else {
			PredicateElement elem = null;
			if (predCtx != null) {
				List<IExpr> params;
				if (ctx.exprList() == null) params = new ArrayList<>();
				else params = get(_params, ctx.exprList());
				elem = new PredicateElement(get(_name, predCtx), params);
			} else if (funcCtx != null) {
				List<IExpr> params = get(_params, funcCtx);
				elem = new FunctionalElement(get(_name, funcCtx), params, get(_param, ctx.expr()));
			} else if (refCtx != null) {
				String name = get(_name, ctx.refmode());
				List<IExpr> params = get(_params, ctx.refmode());
				elem = new RefModeElement(name, (VariableExpr)params.get(0), params.get(1));
			} else if (primCtx != null) {
				throw new RuntimeException ("Primitive used outside a declaration");
			}
			_elem.put(ctx, elem);
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
		list.add(get(_param, ctx.expr()));
		_params.put(ctx, list);
	}
	public void enterFunctionalHead(FunctionalHeadContext ctx) {}
	public void exitFunctionalHead(FunctionalHeadContext ctx) {
		_name.put(ctx, get(_name, ctx.predicateName()));
		if (ctx.exprList() != null)
			_params.put(ctx, get(_params, ctx.exprList()));
		else
			_params.put(ctx, new ArrayList<IExpr>());
	}
	public void enterPredicateName(PredicateNameContext ctx) {}
	public void exitPredicateName(PredicateNameContext ctx) {
		PredicateNameContext child = ctx.predicateName();
		String name = ctx.IDENTIFIER().getText();
		if (child != null)
			name = get(_name, child) + ":" + name;
		_name.put(ctx, name);
	}
	public void enterPrimitiveType(PrimitiveTypeContext ctx) {}
	public void exitPrimitiveType(PrimitiveTypeContext ctx) {
		String base = ctx.IDENTIFIER(0).getText();
		if (ctx.CAPACITY() != null)
			base += ctx.CAPACITY().getText();
		else
			base = normalize(base);
		_name.put(ctx, base);
	}
	public void enterConstant(ConstantContext ctx) {}
	public void exitConstant(ConstantContext ctx) {
		ConstantExpr p;
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
			p = new ConstantExpr(Integer.parseInt(str, base));
		}
		else if (ctx.REAL() != null) p = new ConstantExpr(Double.parseDouble(ctx.REAL().getText()));
		else if (ctx.BOOLEAN() != null) p = new ConstantExpr(Boolean.parseBoolean(ctx.BOOLEAN().getText()));
		else /*if (ctx.STRING() != null)*/ p = new ConstantExpr(ctx.STRING().getText());

		_param.put(ctx, p);
	}
	public void enterExpr(ExprContext ctx) {}
	public void exitExpr(ExprContext ctx) {
		IExpr p;
		if (ctx.IDENTIFIER() != null)
			p = new VariableExpr(ctx.IDENTIFIER().getText());
		else if (ctx.functionalHead() != null) {
			String name = get(_name, ctx.functionalHead());
			List<IExpr> params = get(_params, ctx.functionalHead());
			p = new FunctionalHeadExpr(name, params);
		} else /*if (ctx.primitiveConstant() != null) */
			p = get(_param, ctx.constant());

		_param.put(ctx, p);
//		String token = getToken(ctx, 0);
//		if (ctx.IDENTIFIER() != null) {
//			_elem.put(ctx, new ExprElement(ctx.IDENTIFIER().getText()));
//		} else if (ctx.functionalHead() != null) {
//			FunctionalHeadContext functional = ctx.functionalHead();
//			String name = get(_name, functional);
//			List<Object> params = get(_params, functional);
//			_elem.put(ctx, new ExprElement(new FunctionalHeadElement(name, params)));
//		} else if (ctx.primitiveConstant() != null) {
//			_elem.put(ctx, new ExprElement(getToken(ctx.primitiveConstant(), 0)));
//		} else if (token != null && !token.equals("(")) {
//			ExprElement left = (ExprElement) get(_elem, ctx.expr(0));
//			ExprElement right = (ExprElement) get(_elem, ctx.expr(1));
//			ExprElement.Operator op = null;
//			switch (token) {
//				case "+": op = ExprElement.Operator.PLUS ; break;
//				case "-": op = ExprElement.Operator.MINUS; break;
//				case "*": op = ExprElement.Operator.MULT ; break;
//				case "/": op = ExprElement.Operator.DIV  ; break;
//			}
//			_elem.put(ctx, new ExprElement(left, op, right));
//		} else {
//			_elem.put(ctx, new ExprElement((ExprElement) get(_elem, ctx.expr(0))));
//		}
	}
	public void enterComparison(ComparisonContext ctx) {}
	public void exitComparison(ComparisonContext ctx) {
		String token = getToken(ctx, 0);
		IExpr left = (IExpr) get(_elem, ctx.expr(0));
		IExpr right = (IExpr) get(_elem, ctx.expr(1));
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
		IExpr p = get(_param, ctx.expr());
		List<IExpr> list = get(_params, ctx.exprList(), p);
		_params.put(ctx, list);
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
	static String normalize(String type) {
		if (type.equals("uint") || type.equals("int") || type.equals("float") || type.equals("decimal"))
			return type + "[64]";
		return type;
	}
}

