package org.clyze.jimple

import groovy.json.JsonOutput
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.*
import static org.clyze.jimple.JimpleParser.*

import org.clyze.persistent.Position
import org.clyze.persistent.doop.*

class JimpleListenerImpl extends JimpleBaseListener {

	String              _filename
	List<Map>           _pending
	Map<String, String> _types       = [:]
	int                 _heapCounter
	String              _klass
	String              _method
	boolean             _inDecl

	String              json

	BasicMetadata       metadata     = new BasicMetadata()

	JimpleListenerImpl(String filename) {
		_filename = filename
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
			metadata.variables.add(var(ctx.IDENTIFIER(), true))
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
			_types[v.doopId] = type
		}
	}

	void exitAssignmentStmt(AssignmentStmtContext ctx) {
		(0..1).each {
			if (ctx.IDENTIFIER(it) != null) {
				def name = ctx.IDENTIFIER(it).getText()
				metadata.variables.add(var(ctx.IDENTIFIER(it), name.startsWith("@parameter")))
			}
		}
		(0..1).each {
			if (ctx.value(it) != null && ctx.value(it).IDENTIFIER() != null)
				metadata.variables.add(var(ctx.value(it).IDENTIFIER(), true))
		}
	}

	void exitReturnStmt(ReturnStmtContext ctx) {
		if (ctx.value() != null && ctx.value().IDENTIFIER() != null)
			metadata.variables.add(var(ctx.value().IDENTIFIER(), true))
	}

	void exitAllocationStmt(AllocationStmtContext ctx) {
		metadata.variables.add(var(ctx.IDENTIFIER(0), true))
		metadata.heapAllocations.add(heap(ctx.IDENTIFIER(1)))
		_heapCounter++
	}

	void exitInvokeStmt(InvokeStmtContext ctx) {
		(0..1).each {
			if (ctx.IDENTIFIER(it) != null)
				metadata.variables.add(var(ctx.IDENTIFIER(it), true))
		}
	}

	void exitValueList(ValueListContext ctx) {
		if (ctx.value().IDENTIFIER() != null)
			metadata.variables.add(var(ctx.value().IDENTIFIER(), true))
	}

	void exitJumpStmt(JumpStmtContext ctx) {
		(0..1).each {
			if (ctx.value(it) != null && ctx.value(it).IDENTIFIER() != null)
				metadata.variables.add(var(ctx.value(it).IDENTIFIER(), true))
		}
	}

	Variable var(TerminalNode id, boolean isLocal) {
		def line = id.getSymbol().getLine()
		def startCol = id.getSymbol().getCharPositionInLine() + 1
		def name = id.getText()

		Position position = new Position(line, line, startCol, startCol + name.length())
		Variable v = new Variable(
			position, //position
			_filename, //sourceFileName
			name, //name
			"$_method/$name", //doopId
			null, //type, provided later
			null, //declaringMethodId (value missing?),
			isLocal, //isLocal
			!isLocal //isParameter
		)

		if (_types[v.doopId])
			v.type = _types[v.doopId]
		else
			_pending.push(v)
		return v
	}

	HeapAllocation heap(TerminalNode id) {
		def line = id.getSymbol().getLine()
		def startCol = id.getSymbol().getCharPositionInLine() + 1
		def type = id.getText()

		Position position = new Position(line, line, startCol, startCol + type.length())

		HeapAllocation h = new HeapAllocation(
			position, //position
			_filename, //sourceFileName
			"$_method/$_heapCounter", //doopId
			type, //type
			null //allocatingMethodId (value missing?)
		)

		return h
	}

	List<String> gatherIdentifiers(IdentifierListContext ctx) {
		if (ctx == null) return []
		return gatherIdentifiers(ctx.identifierList()) + [ctx.IDENTIFIER().getText()]
	}
}
