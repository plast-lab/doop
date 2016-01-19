package doop.dsl.datalog;

import static doop.dsl.datalog.DatalogParser.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;

class DatalogListenerImpl implements DatalogListener {
	Set<Predicate> _predicates;
	Set<Predicate> _specialPredicates;

	public DatalogListenerImpl() {
		_predicates = new HashSet<>();
		_specialPredicates = new HashSet<>();
	}

	public void enterProgram(DatalogParser.ProgramContext ctx) {}
	public void exitProgram(DatalogParser.ProgramContext ctx) {}
	public void enterDeclaration(DatalogParser.DeclarationContext ctx) {
		if (ctx.predicate() != null) {
			String name = joinName(ctx.predicate().predicateName());

			List<PredicateContext> preds = collect(ctx.predicateList());
			List<String> types = new ArrayList<>(preds.size());
			for (PredicateContext pCtx : preds) {
				if (pCtx.predicateName() != null)
					types.add(joinName(pCtx.predicateName()));

				else if (pCtx.primitiveType() != null) {
					String base = pCtx.primitiveType().IDENTIFIER(0).getText();
					TerminalNode cap = pCtx.primitiveType().CAPACITY();
					types.add(base + (cap != null ? cap : "[64]"));
				}
			}

			if (types.isEmpty())
				_specialPredicates.add(new Entity(name));
			else
				_predicates.add(new Predicate(name, types));
		} else {
			Entity ent = new Entity(joinName(ctx.predicateName()));

			String refName = joinName(ctx.refmode().predicateName());
			String refType = ctx.primitiveType().IDENTIFIER(0).getText();
			RefMode ref = new RefMode(refName, refType, ent);

			_specialPredicates.add(ent);
			_specialPredicates.add(ref);
		}
	}
	public void exitDeclaration(DatalogParser.DeclarationContext ctx) {}
	public void enterConstraint(DatalogParser.ConstraintContext ctx) {}
	public void exitConstraint(DatalogParser.ConstraintContext ctx) {}
	public void enterRule_(DatalogParser.Rule_Context ctx) {}
	public void exitRule_(DatalogParser.Rule_Context ctx) {}
	public void enterDirective(DatalogParser.DirectiveContext ctx) {}
	public void exitDirective(DatalogParser.DirectiveContext ctx) {}
	public void enterPredicate(DatalogParser.PredicateContext ctx) {}
	public void exitPredicate(DatalogParser.PredicateContext ctx) {}
	public void enterRuleBody(DatalogParser.RuleBodyContext ctx) {}
	public void exitRuleBody(DatalogParser.RuleBodyContext ctx) {}
	public void enterAggregation(DatalogParser.AggregationContext ctx) {}
	public void exitAggregation(DatalogParser.AggregationContext ctx) {}
	public void enterRefmode(DatalogParser.RefmodeContext ctx) {}
	public void exitRefmode(DatalogParser.RefmodeContext ctx) {}
	public void enterFunctionalHead(DatalogParser.FunctionalHeadContext ctx) {}
	public void exitFunctionalHead(DatalogParser.FunctionalHeadContext ctx) {}
	public void enterPredicateName(DatalogParser.PredicateNameContext ctx) {}
	public void exitPredicateName(DatalogParser.PredicateNameContext ctx) {}
	public void enterPrimitiveType(DatalogParser.PrimitiveTypeContext ctx) {}
	public void exitPrimitiveType(DatalogParser.PrimitiveTypeContext ctx) {}
	public void enterPrimitiveConstant(DatalogParser.PrimitiveConstantContext ctx) {}
	public void exitPrimitiveConstant(DatalogParser.PrimitiveConstantContext ctx) {}
	public void enterParameter(DatalogParser.ParameterContext ctx) {}
	public void exitParameter(DatalogParser.ParameterContext ctx) {}
	public void enterComparison(DatalogParser.ComparisonContext ctx) {}
	public void exitComparison(DatalogParser.ComparisonContext ctx) {}
	public void enterExpr(DatalogParser.ExprContext ctx) {}
	public void exitExpr(DatalogParser.ExprContext ctx) {}
	public void enterPredicateList(DatalogParser.PredicateListContext ctx) {}
	public void exitPredicateList(DatalogParser.PredicateListContext ctx) {}
	public void enterParameterList(DatalogParser.ParameterListContext ctx) {}
	public void exitParameterList(DatalogParser.ParameterListContext ctx) {}

	public void enterEveryRule(ParserRuleContext ctx) {}
	public void exitEveryRule(ParserRuleContext ctx) {}
	public void visitErrorNode(ErrorNode node) {}
	public void visitTerminal(TerminalNode node) {}


	static List<String> collect(PredicateNameContext ctx) {
		List<String> list = new ArrayList<>();
		while (ctx != null) {
			list.add(0, ctx.IDENTIFIER().getText());
			ctx = ctx.predicateName();
		}
		return list;
	}
	static List<PredicateContext> collect(PredicateListContext ctx) {
		List<PredicateContext> list = new ArrayList<>();
		while (ctx != null) {
			list.add(0, ctx.predicate());
			ctx = ctx.predicateList();
		}
		return list;
	}
	static String join(List<String> list, String delim) {
		StringJoiner joiner = new StringJoiner(delim);
		for (String s : list) joiner.add(s);
		return joiner.toString();
	}
	static String joinName(PredicateNameContext ctx) {
		return join(collect(ctx), ":");
	}
}

