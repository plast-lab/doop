package org.clyze.jimple

import groovy.json.JsonOutput
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.*
import static org.clyze.jimple.JimpleParser.*

public class JimpleListenerImpl extends JimpleBaseListener {

	// JSON representation
	class Var {
		String name
		String doopName
		String type
		boolean isLocal
		boolean isParameter
		Map position
		String sourceFileName
		int id
	}

	String                       _filename
	ParseTreeProperty<List<Var>> _ids
	List<Var>                    _vars
	String                       _klass
	String                       _method
	boolean                      _inBody

	String                       json

	public JimpleListenerImpl(String filename) {
		_filename = filename
		_ids = new ParseTreeProperty<>()
		_vars = []
	}


	public void exitProgram(ProgramContext ctx) {
		json = JsonOutput.toJson([
			Field: [],
			Variable: _vars,
			HeapAllocation: [],
			Class: [],
			MethodInvocation: [],
			Method: [],
			Occurrence: []
		])
		json = JsonOutput.prettyPrint(json)
	}

	public void enterKlass(KlassContext ctx) {
		_klass = ctx.IDENTIFIER(0).getText()
	}

	public void enterMethod(MethodContext ctx) {
		_method = ctx.IDENTIFIER(1).getText()
		if (ctx.TAG_L() != null && ctx.TAG_R() != null)
			_method = ctx.TAG_L().getText() + _method + ctx.TAG_R().getText()
	}

	public void enterMethodBody(MethodBodyContext ctx) {
		_inBody = true
	}
	public void exitMethodBody(MethodBodyContext ctx) {
		_inBody = false
	}

	public void exitIdentifierList(IdentifierListContext ctx) {
		if (!_inBody) return

		def v = varInfo(ctx.IDENTIFIER())
		def l = []
		if (ctx.identifierList() != null)
			l = get(_ids, ctx.identifierList())
		put(_ids, ctx, l + [ v ])
	}

	public void exitDeclarationStmt(DeclarationStmtContext ctx) {
		def type = ctx.IDENTIFIER()
		def l = get(_ids, ctx.identifierList())
		l.each { v ->
			v.type = type
			v.isLocal = true
			v.isParameter = false
			_vars.push(v)
		}
	}

	public void exitAssignmentStmt(AssignmentStmtContext ctx) {
		def v1 = varInfo(ctx.IDENTIFIER(0))
		//v1.type = ?
		v1.isLocal = true
		v1.isParameter = false
		_vars.push(v1)

		def v2 = varInfo(ctx.IDENTIFIER(1))
		if (ctx.IDENTIFIER(2) != null) {
			v2.type = ctx.IDENTIFIER(2).getText()
			v2.isLocal = v2.name.startsWith("@parameter")
			v2.isParameter = !v2.isLocal
		}
		else {
			//v2.type = ?
			v2.isLocal = true
			v2.isParameter = false
		}
		_vars.push(v2)
	}

	public void exitReturnStmt(ReturnStmtContext ctx) {
		if (ctx.IDENTIFIER() != null) {
			def v = varInfo(ctx.IDENTIFIER())
			//v.type = ?
			v.isLocal = true
			v.isParameter = false
			_vars.push(v)
		}
	}

	public void exitInvokeStmt(InvokeStmtContext ctx) {
		def baseV = varInfo(ctx.IDENTIFIER(0))
		//baseV.type = ?
		baseV.isLocal = true
		baseV.isParameter = false
		_vars.push(baseV)

		// TODO
	}

	public void exitAllocationStmt(AllocationStmtContext ctx) {
		def v = varInfo(ctx.IDENTIFIER(0))
		//v.type = ?
		v.isLocal = true
		v.isParameter = false
	}

	public void visitErrorNode(ErrorNode node) {
		throw new RuntimeException("Parsing error");
	}

	Var varInfo(TerminalNode id) {
		def line = id.getSymbol().getLine()
		def startCol = id.getSymbol().getCharPositionInLine() + 1
		def name = id.getText()
		def v = new Var(
			name: name,
			doopName: _klass+"/"+_method+"/"+name,
			sourceFileName: _filename,
			position: [
				startLine: line,
				startColumn: startCol,
				endLine: line,
				endColumn: startCol + name.length() - 1
			])
		v.id = v.hashCode()
		return v
	}

	static <T> void put(ParseTreeProperty<T> values, ParseTree node, T value) {
		values.put(node, value);
	}

	static <T> T get(ParseTreeProperty<T> values, ParseTree node) {
		T t = values.get(node)
		values.removeFrom(node)
		return t
	}
}
