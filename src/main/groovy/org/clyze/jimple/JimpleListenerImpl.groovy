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
	Class               _klass
	Method              _method
	boolean             _inDecl

	BasicMetadata       metadata     = new BasicMetadata()

	JimpleListenerImpl(String filename) {
		_filename = filename
	}


	void enterKlass(KlassContext ctx) {
		def id = ctx.IDENTIFIER(0)
		def line = id.getSymbol().getLine()
		def startCol = id.getSymbol().getCharPositionInLine() + 1
		// abc.def.Foo
		def fullName = ctx.IDENTIFIER(0).getText()
		def position = new Position(line, line, startCol, startCol + fullName.length())
		def i = fullName.lastIndexOf(".")
		// abc.def
		def packageName = fullName[0..i]
		// Foo
		def className = fullName[(i+1)..-1]

		_klass = new Class(
			position,
			_filename,
			className,
			packageName,
			hasToken(ctx, "interface"),
			ctx.modifier().any() { hasToken(it, "enum") },
			ctx.modifier().any() { hasToken(it, "static") },
			false,//isInner, missing?
			false,//isAnonymous, missing?
		)
		_klass.doopId = fullName
	}

	void enterMethod(MethodContext ctx) {
		def id = ctx.IDENTIFIER(0)
		def line = id.getSymbol().getLine()
		def startCol = id.getSymbol().getCharPositionInLine() + 1
		def retType = ctx.IDENTIFIER(0).getText()
		def name = ctx.IDENTIFIER(1).getText()
		def position = new Position(line, line, startCol, startCol + name.length())
		def paramTypes = gatherIdentifiers(ctx.identifierList(0))
		def params = paramTypes.join(",")

		_method = new Method(
			position,
			_filename,
			name,
			_klass.doopId, //declaringClassId
			retType,
			"<$_klass: $name ${ctx.IDENTIFIER(1).getText()}($params)>", //doopId
			null, //params, TODO
			paramTypes as String[],
			ctx.modifier().any() { hasToken(it, "static") },
			0, //totalInvocations, missing?
			0, //totalAllocations, missing?
		)

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
		def position = new Position(line, line, startCol, startCol + name.length())

		def v = new Variable(
			position,
			_filename,
			name,
			"${_method.doopId}/$name", //doopId
			null, //type, provided later
			_method.doopId, //declaringMethodId
			isLocal,
			!isLocal
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
		def position = new Position(line, line, startCol, startCol + type.length())

		return new HeapAllocation(
			position,
			_filename,
			"${_method.doopId}/$_heapCounter", //doopId
			type,
			_method.doopId //allocatingMethodId
		)
	}

	List<String> gatherIdentifiers(IdentifierListContext ctx) {
		if (ctx == null) return []
		return gatherIdentifiers(ctx.identifierList()) + [ctx.IDENTIFIER().getText()]
	}
	boolean hasToken(ParserRuleContext ctx, String token) {
		for (int i = 0; i < ctx.getChildCount(); i++)
			if (ctx.getChild(i) instanceof TerminalNode &&
				((TerminalNode)ctx.getChild(i)).getText().equals(token))
				return true
		return false
	}
}
