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
		def packageName = fullName[0..(i-1)]
		// Foo
		def className = fullName[(i+1)..-1]

		_klass = new Class(
			position,
			_filename,
			className,
			packageName,
			fullName,
			hasToken(ctx, "interface"),
			ctx.modifier().any() { hasToken(it, "enum") },
			ctx.modifier().any() { hasToken(it, "static") },
			false, //isInner, missing?
			false  //isAnonymous, missing?
		)
		metadata.classes << _klass
	}

	void exitField(FieldContext ctx) {
		def id = ctx.IDENTIFIER(1)
		def line = id.getSymbol().getLine()
		def startCol = id.getSymbol().getCharPositionInLine() + 1
		def type = ctx.IDENTIFIER(0).getText() + (hasToken(ctx, "[]") ? "[]" : "")
		def name = ctx.IDENTIFIER(1).getText()
		def position = new Position(line, line, startCol, startCol + name.length())

		def f = new Field(
			position,
			_filename,
			name,
			"<$_klass: $type $name>", //doopId
			type,
			_klass.doopId, //declaringClassDoopId
			ctx.modifier().any() { hasToken(it, "static") }
		)
		metadata.fields << f
	}

	void enterMethod(MethodContext ctx) {
		def id = ctx.IDENTIFIER(1)
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
			_klass.doopId, //declaringClassDoopId
			retType,
			"<${_klass.doopId}: $retType $name($params)>", //doopId
			null, //params, TODO
			paramTypes as String[],
			ctx.modifier().any() { hasToken(it, "static") },
			0, //totalInvocations, missing?
			0  //totalAllocations, missing?
		)
		metadata.methods << _method

		_heapCounter = 0
	}

	void exitIdentifierList(IdentifierListContext ctx) {
		if (_inDecl)
			metadata.variables << var(ctx.IDENTIFIER(), true)
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

	void exitComplexAssignmentStmt(AssignmentStmtContext ctx) {
		(0..1).each {
			if (ctx.IDENTIFIER(it))
				metadata.usages << usage(ctx.value(it).IDENTIFIER(), UsageKind.DATA_READ)
		}
		(0..1).each {
			if (ctx.value(it)?.IDENTIFIER())
				metadata.usages << usage(ctx.value(it).IDENTIFIER(), UsageKind.DATA_READ)
		}
	}

	void exitAssignmentStmt(AssignmentStmtContext ctx) {
		if (ctx.IDENTIFIER(0))
			metadata.usages << usage(ctx.IDENTIFIER(0), UsageKind.DATA_WRITE)
		if (ctx.IDENTIFIER(1))
			metadata.usages << usage(ctx.IDENTIFIER(1), UsageKind.DATA_READ)

		(0..1).each {
			if (ctx.value(it)?.IDENTIFIER())
				metadata.usages << usage(ctx.value(it).IDENTIFIER(), UsageKind.DATA_READ)
		}
	}

	void exitReturnStmt(ReturnStmtContext ctx) {
		if (ctx.value()?.IDENTIFIER())
			metadata.usages << usage(ctx.value().IDENTIFIER(), UsageKind.DATA_READ)
	}

	void exitAllocationStmt(AllocationStmtContext ctx) {
		metadata.usages << usage(ctx.IDENTIFIER(0), UsageKind.DATA_WRITE)
		metadata.heapAllocations << heap(ctx.IDENTIFIER(1))
		_heapCounter++
	}

	void exitInvokeStmt(InvokeStmtContext ctx) {
		if (ctx.IDENTIFIER(0))
			metadata.usages << usage(ctx.IDENTIFIER(0), UsageKind.DATA_WRITE)
		if (ctx.IDENTIFIER(1))
			metadata.usages << usage(ctx.IDENTIFIER(1), UsageKind.DATA_READ)
	}

	void exitValueList(ValueListContext ctx) {
		if (ctx.value().IDENTIFIER())
			metadata.usages << usage(ctx.value().IDENTIFIER(), UsageKind.DATA_READ)
	}

	void exitJumpStmt(JumpStmtContext ctx) {
		(0..1).each {
			if (ctx.value(it)?.IDENTIFIER())
				metadata.usages << usage(ctx.value(it).IDENTIFIER(), UsageKind.DATA_READ)
		}
	}

	Variable var(TerminalNode id, boolean isLocal) {
		def line = id.getSymbol().getLine()
		def startCol = id.getSymbol().getCharPositionInLine() + 1
		def name = id.getText()

		def v = new Variable(
			new Position(line, line, startCol, startCol + name.length()),
			_filename,
			name,
			"${_method.doopId}/$name", //doopId
			null, //type, provided later
			_method.doopId, //declaringMethodDoopId
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

		return new HeapAllocation(
			new Position(line, line, startCol, startCol + type.length()),
			_filename,
			"${_method.doopId}/new $type/$_heapCounter", //doopId
			type,
			_method.doopId //allocatingMethodDoopId
		)
	}

	Usage usage(TerminalNode id, UsageKind kind) {
		def line = id.getSymbol().getLine()
		def startCol = id.getSymbol().getCharPositionInLine() + 1
		def name = id.getText()

		def u = new Usage(
			new Position(line, line, startCol, startCol + name.length()),
			_filename,
			"${_method.doopId}/$name", //doopId
			kind
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
