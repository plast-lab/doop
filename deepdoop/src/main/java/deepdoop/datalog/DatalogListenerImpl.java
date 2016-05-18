package deepdoop.datalog;

import static deepdoop.datalog.DatalogParser.*;
import deepdoop.datalog.LogicalElement.LogicType;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.antlr.v4.runtime.tree.TerminalNode;

class DatalogListenerImpl implements DatalogListener {

	ParseTreeProperty<String>          _name;
	ParseTreeProperty<List<String>>    _names;
	ParseTreeProperty<IAtom>           _atom;
	ParseTreeProperty<List<IAtom>>     _atoms;
	ParseTreeProperty<IExpr>           _expr;
	ParseTreeProperty<List<IExpr>>     _exprs;
	ParseTreeProperty<IElement>        _elem;
	ParseTreeProperty<List<IElement>>  _elems;

	boolean                            _inDecl;
	Component                          _globalComp;
	Component                          _currComp;
	Program                            _program;

	public DatalogListenerImpl() {
		_name       = new ParseTreeProperty<>();
		_names      = new ParseTreeProperty<>();
		_atom       = new ParseTreeProperty<>();
		_atoms      = new ParseTreeProperty<>();
		_expr       = new ParseTreeProperty<>();
		_exprs      = new ParseTreeProperty<>();
		_elem       = new ParseTreeProperty<>();
		_elems      = new ParseTreeProperty<>();
		_globalComp = new Component();
		_currComp   = _globalComp;
		_program    = new Program();
	}

	public Program getProgram() {
		return _program;
	}

	public void exitProgram(ProgramContext ctx) {
		_program.global(_globalComp);
	}
	public void enterComp(CompContext ctx) {
		_currComp = new Component(ctx.IDENTIFIER(0).getText(), (ctx.IDENTIFIER(1) != null ? ctx.IDENTIFIER(1).getText() : null));
	}
	public void exitComp(CompContext ctx) {
		_program.comp(_currComp);
		_currComp = _globalComp;
	}
	public void exitInit_(Init_Context ctx) {
		_program.init(ctx.IDENTIFIER(0).getText(), ctx.IDENTIFIER(1).getText());
	}
	public void exitPropagate(PropagateContext ctx) {
		_program.propagate(
				ctx.IDENTIFIER(0).getText(),
				ctx.ALL() != null ? new HashSet<>() : new HashSet<>(get(_names, ctx.predicateNameList())),
				ctx.GLOBAL() != null ? null : ctx.IDENTIFIER(1).getText());
	}
	public void exitPredicateNameList(PredicateNameListContext ctx) {
		String predName = get(_name, ctx.predicateName());
		List<String> list = get(_names, ctx.predicateNameList(), predName);
		_names.put(ctx, list);
	}
	public void enterDeclaration(DeclarationContext ctx) {
		_inDecl = true;
	}
	public void exitDeclaration(DeclarationContext ctx) {
		_inDecl = false;

		if (ctx.refmode() == null) {
			IAtom pred = get(_atom, ctx.predicate());
			List<IAtom> types = get(_atoms, ctx.predicateList());
			if (types != null) {
				if (pred instanceof Predicate) {
					((Predicate)pred).setTypes(types);
				}
				else if (pred instanceof Functional) {
					IAtom value = types.remove(types.size()-1);
					((Functional)pred).setTypes(types, value);
				}
				else
					throw new RuntimeException("Weird");
				_currComp.atoms.add(pred);
			}
			else {
				_currComp.atoms.add(new Entity(pred.name()));
			}
		}
		else {
			Entity ent = new Entity(get(_name, ctx.predicateName()));
			Primitive primitive = (Primitive) get(_atom, ctx.predicate());
			_currComp.atoms.add(ent);
			_currComp.atoms.add(new RefMode(get(_name, ctx.refmode()), ent, primitive));
		}
	}
	public void exitRule_(Rule_Context ctx) {
		if (ctx.predicateList() != null) {
			LogicalElement head = new LogicalElement(LogicType.AND, get(_elems, ctx.predicateList()));
			IElement body = get(_elem, ctx.ruleBody());
			if (body != null) body.flatten();
			_currComp.rules.add(new Rule(head, body));
		} else {
			IElement head = get(_elem, ctx.predicate());
			AggregationElement aggregation = (AggregationElement) get(_elem, ctx.aggregation());
			_currComp.rules.add(new Rule(head, aggregation));
		}
	}
	public void exitPredicate(PredicateContext ctx) {
		if (_inDecl) {
			assert ctx.AT_STAGE() == null;

			if (ctx.predicateName(0) != null) {
				String name = get(_name, ctx.predicateName(0));
				if (hasToken(ctx, "(")) {
					String capacity = ctx.CAPACITY() == null ? null : ctx.CAPACITY().getText();
					if (isPrimitive(name))
						_atom.put(ctx, new Primitive(name, capacity));
					else
						_atom.put(ctx, new Predicate(name));
				}
				else if (hasToken(ctx, "[")) {
					_atom.put(ctx, new Functional(name));
				}
			}
			// NOTE: Refmode declarations have separate handling in grammar
			//else if (ctx.refmode() != null) {}

		}
		else {
			assert ctx.CAPACITY() == null;

			if (ctx.predicateName(0) != null) {
				String name = get(_name, ctx.predicateName(0));
				String stage = ctx.AT_STAGE() == null ? null : ctx.AT_STAGE().getText();
				List<IExpr> exprs = (ctx.exprList() == null ? exprs = new ArrayList<>() : get(_exprs, ctx.exprList()));
				if (hasToken(ctx, "(")) {
					_elem.put(ctx, new PredicateElement(name, stage, exprs));
				}
				else if (hasToken(ctx, "[")) {
					_elem.put(ctx, new FunctionalElement(name, stage, exprs, get(_expr, ctx.expr())));
				}
			}
			else if (ctx.refmode() != null) {
				String name = get(_name, ctx.refmode());
				String stage = ctx.refmode().AT_STAGE() == null ? null : ctx.refmode().AT_STAGE().getText();
				List<IExpr> exprs = get(_exprs, ctx.refmode());
				_elem.put(ctx, new RefModeElement(name, stage, (VariableExpr)exprs.get(0), exprs.get(1)));
			}

		}
	}
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
			_elem.put(ctx, new LogicalElement(token.equals(",") ? LogicType.AND : LogicType.OR, list));
		} else if (token.equals("!")) {
			_elem.put(ctx, new NegationElement(get(_elem, ctx.ruleBody(0))));
		}
	}
	public void exitAggregation(AggregationContext ctx) {
		VariableExpr variable = new VariableExpr(ctx.IDENTIFIER().getText());
		PredicateElement predicate = (PredicateElement) get(_elem, ctx.predicate());
		IElement body = get(_elem, ctx.ruleBody());
		_elem.put(ctx, new AggregationElement(variable, predicate, body));
	}
	public void exitRefmode(RefmodeContext ctx) {
		_name.put(ctx, get(_name, ctx.predicateName()));
		List<IExpr> list = new ArrayList<>();
		list.add(new VariableExpr(ctx.IDENTIFIER().getText()));
		list.add(get(_expr, ctx.expr()));
		_exprs.put(ctx, list);
	}
	public void exitPredicateName(PredicateNameContext ctx) {
		PredicateNameContext child = ctx.predicateName();
		String name = ctx.IDENTIFIER().getText();
		if (child != null)
			name = get(_name, child) + ":" + name;
		_name.put(ctx, name);
	}
	public void exitConstant(ConstantContext ctx) {
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
			_expr.put(ctx, new ConstantExpr(Integer.parseInt(str, base)));
		}
		else if (ctx.REAL() != null)    _expr.put(ctx, new ConstantExpr(Double.parseDouble(ctx.REAL().getText())));
		else if (ctx.BOOLEAN() != null) _expr.put(ctx, new ConstantExpr(Boolean.parseBoolean(ctx.BOOLEAN().getText())));
		else if (ctx.STRING() != null)  _expr.put(ctx, new ConstantExpr(ctx.STRING().getText()));
	}
	public void exitExpr(ExprContext ctx) {
		IExpr e;
		if (ctx.IDENTIFIER() != null)
			e = new VariableExpr(ctx.IDENTIFIER().getText());
		else if (ctx.predicateName() != null) {
			String name = get(_name, ctx.predicateName());
			String stage = ctx.AT_STAGE() == null ? null : ctx.AT_STAGE().getText();
			List<IExpr> exprs = (ctx.exprList() == null ? exprs = new ArrayList<>() : get(_exprs, ctx.exprList()));
			e = new FunctionalHeadExpr(name, stage, exprs);
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
	public void exitPredicateList(PredicateListContext ctx) {
		if (_inDecl) {
			IAtom atom = get(_atom, ctx.predicate());
			List<IAtom> list = get(_atoms, ctx.predicateList(), atom);
			_atoms.put(ctx, list);
		} else {
			IElement elem = get(_elem, ctx.predicate());
			List<IElement> list = get(_elems, ctx.predicateList(), elem);
			_elems.put(ctx, list);
		}
	}
	public void exitExprList(ExprListContext ctx) {
		IExpr p = get(_expr, ctx.expr());
		List<IExpr> list = get(_exprs, ctx.exprList(), p);
		_exprs.put(ctx, list);
	}

	public void visitErrorNode(ErrorNode node) {
		throw new RuntimeException("Parsing error");
	}
	// Not used (for now) inherited methods - START
	public void enterProgram(ProgramContext ctx) {}
	public void enterInit_(Init_Context ctx) {}
	public void enterPropagate(PropagateContext ctx) {}
	public void enterPredicateNameList(PredicateNameListContext ctx) {}
	public void enterDatalog(DatalogContext ctx) {}
	public void exitDatalog(DatalogContext ctx) {}
	public void enterConstraint(ConstraintContext ctx) {}
	public void exitConstraint(ConstraintContext ctx) {}
	public void enterRule_(Rule_Context ctx) {}
	public void enterPredicate(PredicateContext ctx) {}
	public void enterRuleBody(RuleBodyContext ctx) {}
	public void enterAggregation(AggregationContext ctx) {}
	public void enterRefmode(RefmodeContext ctx) {}
	public void enterPredicateName(PredicateNameContext ctx) {}
	public void enterConstant(ConstantContext ctx) {}
	public void enterExpr(ExprContext ctx) {}
	public void enterComparison(ComparisonContext ctx) {}
	public void enterPredicateList(PredicateListContext ctx) {}
	public void enterExprList(ExprListContext ctx) {}

	public void enterEveryRule(ParserRuleContext ctx) {}
	public void exitEveryRule(ParserRuleContext ctx) {}
	public void visitTerminal(TerminalNode node) {}
	// Not used (for now) inherited methods - END

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
	static boolean hasToken(ParserRuleContext ctx, String token) {
		for (int i = 0; i < ctx.getChildCount(); i++)
			if (ctx.getChild(i) instanceof TerminalNode &&
				((TerminalNode)ctx.getChild(i)).getText().equals(token))
				return true;
		return false;
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
}
