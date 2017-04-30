package org.clyze.jimple

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.*
import static org.clyze.jimple.JimpleParser.*

import org.clyze.persistent.Position
import org.clyze.persistent.doop.*

class JimpleListenerImpl extends JimpleBaseListener {

	String              filename
	List<Map>           pending
	Map<String, String> types   = [:]
	int                 heapCounter
	Class               klass
	Method              method
	Map<String,Integer> methodInvoCounters
	boolean             inDecl

	BasicMetadata       metadata = new BasicMetadata()

	JimpleListenerImpl(String filename) {
		this.filename = filename
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

		klass = new Class(
			position,
			filename,
			className,
			packageName,
			fullName,
			hasToken(ctx, "interface"),
			ctx.modifier().any() { hasToken(it, "enum") },
			ctx.modifier().any() { hasToken(it, "static") },
			false, //isInner, missing?
			false  //isAnonymous, missing?
		)
		metadata.classes << klass
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
			filename,
			name,
			"<$klass: $type $name>", //doopId
			type,
			klass.doopId, //declaringClassDoopId
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

		method = new Method(
			position,
			filename,
			name,
			klass.doopId, //declaringClassDoopId
			retType,
			"<${klass.doopId}: $retType $name($params)>", //doopId
			null, //params, TODO
			paramTypes as String[],
			ctx.modifier().any() { hasToken(it, "static") },
			0, //totalInvocations, missing?
			0  //totalAllocations, missing?
		)
		metadata.methods << method

		heapCounter = 0
		methodInvoCounters = [:]
	}

	void exitIdentifierList(IdentifierListContext ctx) {
		if (inDecl)
			metadata.variables << var(ctx.IDENTIFIER(), true)
	}

	void enterDeclarationStmt(DeclarationStmtContext ctx) {
		inDecl = true
		pending = []
	}

	void exitDeclarationStmt(DeclarationStmtContext ctx) {
		inDecl = false
		def type = ctx.IDENTIFIER().getText()
		pending.each { v ->
			v.type = type
			types[v.doopId] = type
		}
	}

	void exitComplexAssignmentStmt(ComplexAssignmentStmtContext ctx) {
		if (ctx.IDENTIFIER())
			metadata.usages << varUsage(ctx.IDENTIFIER(), UsageKind.DATA_READ)

		if (ctx.fieldSig())
			metadata.usages << fieldUsage(ctx.fieldSig(), UsageKind.DATA_WRITE)

		(0..1).each {
			if (ctx.value(it)?.IDENTIFIER())
				metadata.usages << varUsage(ctx.value(it).IDENTIFIER(), UsageKind.DATA_READ)
		}
	}

	void exitAssignmentStmt(AssignmentStmtContext ctx) {
		if (ctx.IDENTIFIER(0))
			metadata.usages << varUsage(ctx.IDENTIFIER(0), UsageKind.DATA_WRITE)
		if (ctx.IDENTIFIER(1))
			metadata.usages << varUsage(ctx.IDENTIFIER(1), UsageKind.DATA_READ)

		(0..1).each {
			if (ctx.value(it)?.IDENTIFIER())
				metadata.usages << varUsage(ctx.value(it).IDENTIFIER(), UsageKind.DATA_READ)
		}
	}

	void exitReturnStmt(ReturnStmtContext ctx) {
		if (ctx.value()?.IDENTIFIER())
			metadata.usages << varUsage(ctx.value().IDENTIFIER(), UsageKind.DATA_READ)
	}

	void exitAllocationStmt(AllocationStmtContext ctx) {
		metadata.usages << varUsage(ctx.IDENTIFIER(0), UsageKind.DATA_WRITE)
		metadata.heapAllocations << heap(ctx.IDENTIFIER(1))
		heapCounter++
	}

	void exitInvokeStmt(InvokeStmtContext ctx) {
		if (ctx.IDENTIFIER(0))
			metadata.usages << varUsage(ctx.IDENTIFIER(0), UsageKind.DATA_WRITE)
		if (ctx.IDENTIFIER(1))
			metadata.usages << varUsage(ctx.IDENTIFIER(1), UsageKind.DATA_READ)

		if (!ctx.methodSig(0).IDENTIFIER(1))
			throw new RuntimeException("Why?")

		def methodClass = ctx.methodSig(0).IDENTIFIER(0).getText()
		def retType     = ctx.methodSig(0).IDENTIFIER(1).getText()
		def methodName  = ctx.methodSig(0).IDENTIFIER(2).getText()
		def c = methodInvoCounters[methodName] ?: 0
		methodInvoCounters[methodName] = c+1

		def line = ctx.methodSig(0).IDENTIFIER(0).getSymbol().getLine()
		def startCol = ctx.methodSig(0).IDENTIFIER(0).getSymbol().getCharPositionInLine()
		def len = methodClass.length() + retType.length() + methodName.length() + 4

		metadata.invocations << new MethodInvocation(
			new Position(line, line, startCol, startCol + len),
			filename,
			"${method.doopId}/${methodClass}.$methodName/$c", //doopId
			method.doopId //invokingMethodDoopId
		)
	}

	void exitValueList(ValueListContext ctx) {
		if (ctx.value().IDENTIFIER())
			metadata.usages << varUsage(ctx.value().IDENTIFIER(), UsageKind.DATA_READ)
	}

	void exitJumpStmt(JumpStmtContext ctx) {
		(0..1).each {
			if (ctx.value(it)?.IDENTIFIER())
				metadata.usages << varUsage(ctx.value(it).IDENTIFIER(), UsageKind.DATA_READ)
		}
	}


	Variable var(TerminalNode id, boolean isLocal) {
		def line = id.getSymbol().getLine()
		def startCol = id.getSymbol().getCharPositionInLine() + 1
		def name = id.getText()

		def v = new Variable(
			new Position(line, line, startCol, startCol + name.length()),
			filename,
			name,
			"${method.doopId}/$name", //doopId
			null, //type, provided later
			method.doopId, //declaringMethodDoopId
			isLocal,
			!isLocal
		)

		if (types[v.doopId])
			v.type = types[v.doopId]
		else
			pending.push(v)
		return v
	}

	HeapAllocation heap(TerminalNode id) {
		def line = id.getSymbol().getLine()
		def startCol = id.getSymbol().getCharPositionInLine() + 1
		def type = id.getText()

		return new HeapAllocation(
			new Position(line, line, startCol, startCol + type.length()),
			filename,
			"${method.doopId}/new $type/$heapCounter", //doopId
			type,
			method.doopId //allocatingMethodDoopId
		)
	}

	Usage varUsage(TerminalNode id, UsageKind kind) {
		def line = id.getSymbol().getLine()
		def startCol = id.getSymbol().getCharPositionInLine() + 1
		def name = id.getText()

		def u = new Usage(
			new Position(line, line, startCol, startCol + name.length()),
			filename,
			"${method.doopId}/$name", //doopId
			kind
		)
	}
	Usage fieldUsage(FieldSigContext ctx, UsageKind kind) {
		def klass = ctx.IDENTIFIER(0).getText()
		def type  = ctx.IDENTIFIER(1).getText()
		def name  = ctx.IDENTIFIER(2).getText()

		def line = ctx.IDENTIFIER(0).getSymbol().getLine()
		def startCol = ctx.IDENTIFIER(0).getSymbol().getCharPositionInLine()
		def len = klass.length() + type.length() +  name.length() + 4

		def u = new Usage(
			new Position(line, line, startCol, startCol + len),
			filename,
			"<$klass: $type $name>", //doopId
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
