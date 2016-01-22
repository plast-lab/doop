package deepdoop.datalog;

import static deepdoop.datalog.DatalogParser.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.antlr.v4.runtime.tree.TerminalNode;

class DatalogListenerImpl implements DatalogListener {
	Set<Predicate> _predicates;
	Set<Predicate> _specialPredicates;

	ParseTreeProperty<String> _name;
	ParseTreeProperty<List<String>> _names;

	public DatalogListenerImpl() {
		_predicates = new HashSet<>();
		_specialPredicates = new HashSet<>();

		_name = new ParseTreeProperty<>();
		_names = new ParseTreeProperty<>();
	}

	public void enterProgram(ProgramContext ctx) {}
	public void exitProgram(ProgramContext ctx) {}
	public void enterDeclaration(DeclarationContext ctx) {}
	public void exitDeclaration(DeclarationContext ctx) {
		if (ctx.predicate() != null) {
			String name = get(_name, ctx.predicate());
			List<String> types = get(_names, ctx.predicateList());
			if (types != null)
				_predicates.add(new Predicate(name, types));
			else
				_specialPredicates.add(new Entity(name));
		} else {
			Entity ent = new Entity(get(_name, ctx.predicateName()));

			String refName = get(_name, ctx.refmode());
			String refType = get(_name, ctx.primitiveType());
			RefMode ref = new RefMode(refName, refType, ent);

			_specialPredicates.add(ent);
			_specialPredicates.add(ref);
		}
	}
	public void enterConstraint(ConstraintContext ctx) {}
	public void exitConstraint(ConstraintContext ctx) {}
	public void enterRule_(Rule_Context ctx) {}
	public void exitRule_(Rule_Context ctx) {}
	public void enterDirective(DirectiveContext ctx) {}
	public void exitDirective(DirectiveContext ctx) {}
	public void enterPredicate(PredicateContext ctx) {}
	public void exitPredicate(PredicateContext ctx) {
		ParseTree child = ctx.predicateName();
		if (child == null) child = ctx.primitiveType();

		if (child != null)
			_name.put(ctx, get(_name, child));
	}
	public void enterRuleBody(RuleBodyContext ctx) {}
	public void exitRuleBody(RuleBodyContext ctx) {}
	public void enterAggregation(AggregationContext ctx) {}
	public void exitAggregation(AggregationContext ctx) {}
	public void enterRefmode(RefmodeContext ctx) {}
	public void exitRefmode(RefmodeContext ctx) {
		_name.put(ctx, get(_name, ctx.predicateName()));
	}
	public void enterFunctionalHead(FunctionalHeadContext ctx) {}
	public void exitFunctionalHead(FunctionalHeadContext ctx) {}
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
	public void enterPrimitiveConstant(PrimitiveConstantContext ctx) {}
	public void exitPrimitiveConstant(PrimitiveConstantContext ctx) {}
	public void enterParameter(ParameterContext ctx) {}
	public void exitParameter(ParameterContext ctx) {}
	public void enterComparison(ComparisonContext ctx) {}
	public void exitComparison(ComparisonContext ctx) {}
	public void enterExpr(ExprContext ctx) {}
	public void exitExpr(ExprContext ctx) {}
	public void enterPredicateList(PredicateListContext ctx) {}
	public void exitPredicateList(PredicateListContext ctx) {
		PredicateListContext child = ctx.predicateList();
		String name = get(_name, ctx.predicate());
		List<String> list;
		if (child != null) {
			list = get(_names, child);
		} else {
			list = new ArrayList<>();
		}
		list.add(name);
		_names.put(ctx, list);
	}
	public void enterParameterList(ParameterListContext ctx) {}
	public void exitParameterList(ParameterListContext ctx) {}

	public void enterEveryRule(ParserRuleContext ctx) {}
	public void exitEveryRule(ParserRuleContext ctx) {}
	public void visitErrorNode(ErrorNode node) {
		throw new RuntimeException("Parsing error");
	}
	public void visitTerminal(TerminalNode node) {}


	static String join(List<String> list, String delim) {
		StringJoiner joiner = new StringJoiner(delim);
		for (String s : list) joiner.add(s);
		return joiner.toString();
	}
	static <T> T get(ParseTreeProperty<T> values, ParseTree node) {
		T t = values.get(node);
		values.removeFrom(node);
		return t;
	}
	static String normalize(String type) {
		if (type.equals("uint") || type.equals("int") || type.equals("float") || type.equals("decimal"))
			return type + "[64]";
		return type;
	}
}

