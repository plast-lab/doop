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

	String    _filename
	List<Var> _vars
	String    _klass
	String    _method
	boolean   _inDecl

	String    json

	public JimpleListenerImpl(String filename) {
		_filename = filename
		_vars     = []
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
	}

	public void exitIdentifierList(IdentifierListContext ctx) {
		if (_inDecl)
			_vars.push(var(ctx.IDENTIFIER(), true))
	}

	public void enterDeclarationStmt(DeclarationStmtContext ctx) {
		_inDecl = true
	}

	public void exitDeclarationStmt(DeclarationStmtContext ctx) {
		_inDecl = false
	}

	public void exitAssignmentStmt(AssignmentStmtContext ctx) {
		(0..1).each {
			if (ctx.IDENTIFIER(it) != null) {
				def name = ctx.IDENTIFIER(it).getText()
				_vars.push(var(ctx.IDENTIFIER(it), name.startsWith("@parameter")))
			}
		}
		(0..1).each {
			if (ctx.value(it) != null && ctx.value(it).IDENTIFIER() != null)
				_vars.push(var(ctx.value(it).IDENTIFIER(), true))
		}
	}

	public void exitReturnStmt(ReturnStmtContext ctx) {
		if (ctx.value() != null && ctx.value().IDENTIFIER() != null)
			_vars.push(var(ctx.value().IDENTIFIER(), true))
	}

	public void exitAllocationStmt(AllocationStmtContext ctx) {
		_vars.push(var(ctx.IDENTIFIER(0), true))
	}

	public void exitInvokeStmt(InvokeStmtContext ctx) {
		(0..1).each {
			if (ctx.IDENTIFIER(it) != null)
				_vars.push(var(ctx.IDENTIFIER(it), true))
		}
	}

	public void exitValueList(ValueListContext ctx) {
		if (ctx.value().IDENTIFIER() != null)
			_vars.push(var(ctx.value().IDENTIFIER(), true))
	}

	public void exitJumpStmt(JumpStmtContext ctx) {
		(0..1).each {
			if (ctx.value(it) != null && ctx.value(it).IDENTIFIER() != null)
				_vars.push(var(ctx.value(it).IDENTIFIER(), true))
		}
	}

	Var var(TerminalNode id, boolean isLocal) {
		def line = id.getSymbol().getLine()
		def startCol = id.getSymbol().getCharPositionInLine() + 1
		def name = id.getText()
		def v = new Var(
			name: name,
			doopName: _klass+"/"+_method+"/"+name,
			isLocal: isLocal,
			isParameter: !isLocal,
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
}
