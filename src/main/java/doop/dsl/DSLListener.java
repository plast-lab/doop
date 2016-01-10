//package doop.dsl;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;

class DSLListener implements DatalogListener {
	public void enterProgram(DatalogParser.ProgramContext ctx) {}
	public void exitProgram(DatalogParser.ProgramContext ctx) {}
	public void enterDeclaration(DatalogParser.DeclarationContext ctx) {
		System.out.println("DECL: " + ctx.predicate().predicateName().IDENTIFIER().getText());
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
}

