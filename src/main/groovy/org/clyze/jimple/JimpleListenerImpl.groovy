package org.clyze.jimple

import groovy.json.JsonOutput
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.*
import static org.clyze.jimple.JimpleParser.*

class JimpleListenerImpl extends JimpleBaseListener {

	String              _filename
	List<Map>           _vars        = []
	List<Map>           _pending
	Map<String, String> _types       = [:]
	List<Map>           _heaps       = []
	int                 _heapCounter
	String              _klass
	String              _method
	boolean             _inDecl

	String              json

	JimpleListenerImpl(String filename) {
		_filename = filename
	}


	void exitProgram(ProgramContext ctx) {
		json = JsonOutput.toJson([
			Field: [],
			Variable: _vars,
			HeapAllocation: _heaps,
			Class: [],
			MethodInvocation: [],
			Method: [],
			Occurrence: []
		])
		json = JsonOutput.prettyPrint(json)
	}

	void enterKlass(KlassContext ctx) {
		_klass = ctx.IDENTIFIER(0).getText()
	}

	void enterMethod(MethodContext ctx) {
		def args = gatherIdentifiers(ctx.identifierList(0)).join(",")
		_method = "<$_klass: ${ctx.IDENTIFIER(0).getText()} ${ctx.IDENTIFIER(1).getText()}($args)>"
		_heapCounter = 0
	}

	void exitIdentifierList(IdentifierListContext ctx) {
		if (_inDecl)
			_vars.push(var(ctx.IDENTIFIER(), true))
	}

	void enterDeclarationStmt(DeclarationStmtContext ctx) {
		_inDecl = true
		_pending = []
	}

	void exitDeclarationStmt(DeclarationStmtContext ctx) {
		_inDecl = false
		def type = ctx.IDENTIFIER().getText()
		_pending.each { v ->
			v.type = type
			_types[v.doopName] = type
		}
	}

	void exitAssignmentStmt(AssignmentStmtContext ctx) {
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

	void exitReturnStmt(ReturnStmtContext ctx) {
		if (ctx.value() != null && ctx.value().IDENTIFIER() != null)
			_vars.push(var(ctx.value().IDENTIFIER(), true))
	}

	void exitAllocationStmt(AllocationStmtContext ctx) {
		_vars.push(var(ctx.IDENTIFIER(0), true))
		_heaps.push(heap(ctx.IDENTIFIER(1)))
		_heapCounter++
	}

	void exitInvokeStmt(InvokeStmtContext ctx) {
		(0..1).each {
			if (ctx.IDENTIFIER(it) != null)
				_vars.push(var(ctx.IDENTIFIER(it), true))
		}
	}

	void exitValueList(ValueListContext ctx) {
		if (ctx.value().IDENTIFIER() != null)
			_vars.push(var(ctx.value().IDENTIFIER(), true))
	}

	void exitJumpStmt(JumpStmtContext ctx) {
		(0..1).each {
			if (ctx.value(it) != null && ctx.value(it).IDENTIFIER() != null)
				_vars.push(var(ctx.value(it).IDENTIFIER(), true))
		}
	}

	Map var(TerminalNode id, boolean isLocal) {
		def line = id.getSymbol().getLine()
		def startCol = id.getSymbol().getCharPositionInLine() + 1
		def name = id.getText()
		def v = [
			name: name,
			doopName: "$_method/$name",
			isLocal: isLocal,
			isParameter: !isLocal,
			sourceFileName: _filename,
			position: [
				startLine: line,
				startColumn: startCol,
				endLine: line,
				endColumn: startCol + name.length()
			]]
		v.id = v.hashCode()
		if (_types[v.doopName])
			v.type = _types[v.doopName]
		else
			_pending.push(v)
		return v
	}

	Map heap(TerminalNode id) {
		def line = id.getSymbol().getLine()
		def startCol = id.getSymbol().getCharPositionInLine() + 1
		def type = id.getText()
		def h = [
			type: type,
			doopID: "$_method/$_heapCounter",
			sourceFileName: _filename,
			position: [
				startLine: line,
				startColumn: startCol,
				endLine: line,
				endColumn: startCol + type.length()
			]]
		h.id = h.hashCode()
		return h
	}

	List<String> gatherIdentifiers(IdentifierListContext ctx) {
		if (ctx == null) return []
		return gatherIdentifiers(ctx.identifierList()) + [ctx.IDENTIFIER().getText()]
	}
}
