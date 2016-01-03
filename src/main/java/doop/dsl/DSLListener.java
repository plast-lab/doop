//package doop.dsl;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;

class DSLListener implements DatalogListener {
	public void enterProgram(DatalogParser.ProgramContext ctx) {}
	public void exitProgram(DatalogParser.ProgramContext ctx) {}
	public void enterDeclaration(DatalogParser.DeclarationContext ctx) {
		System.out.println("DECL: " + ctx.predicate().IDENTIFIER().getText());
	}
	public void exitDeclaration(DatalogParser.DeclarationContext ctx) {}
	public void enterRule_(DatalogParser.Rule_Context ctx) {}
	public void exitRule_(DatalogParser.Rule_Context ctx) {}
	public void enterPredicate(DatalogParser.PredicateContext ctx) {
		System.out.println(ctx.IDENTIFIER().getText());
	}
	public void exitPredicate(DatalogParser.PredicateContext ctx) {}
	public void enterRuleBody(DatalogParser.RuleBodyContext ctx) {}
	public void exitRuleBody(DatalogParser.RuleBodyContext ctx) {}
	public void enterFunctionalHead(DatalogParser.FunctionalHeadContext ctx) {}
	public void exitFunctionalHead(DatalogParser.FunctionalHeadContext ctx) {}
	public void enterComparison(DatalogParser.ComparisonContext ctx) {}
	public void exitComparison(DatalogParser.ComparisonContext ctx) {}
	public void enterExpr(DatalogParser.ExprContext ctx) {}
	public void exitExpr(DatalogParser.ExprContext ctx) {}
	public void enterPredicateList(DatalogParser.PredicateListContext ctx) {}
	public void exitPredicateList(DatalogParser.PredicateListContext ctx) {}
	public void enterVariable(DatalogParser.VariableContext ctx) {}
	public void exitVariable(DatalogParser.VariableContext ctx) {}
	public void enterVariableList(DatalogParser.VariableListContext ctx) {}
	public void exitVariableList(DatalogParser.VariableListContext ctx) {}

	public void enterEveryRule(ParserRuleContext ctx) {}
	public void exitEveryRule(ParserRuleContext ctx) {}
	public void visitErrorNode(ErrorNode node) {}
	public void visitTerminal(TerminalNode node) {}
}

