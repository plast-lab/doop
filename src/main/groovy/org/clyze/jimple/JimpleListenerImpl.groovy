package org.clyze.jimple

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.TerminalNode
import org.clyze.persistent.Position
import org.clyze.persistent.doop.*

import static org.clyze.jimple.JimpleParser.*
import org.clyze.persistent.doop.DynamicMethodInvocation

class JimpleListenerImpl extends JimpleBaseListener {

	String filename
	List<Map> pending
	Map varTypes = [:]
	Map heapCounters
	Class klass
	Method method
	Map methodInvoCounters
	Map values = [:]
	boolean inDecl

	BasicMetadata metadata = new BasicMetadata()

	JimpleListenerImpl(String filename) {
		this.filename = filename
	}

	void enterKlass(KlassContext ctx) {
		def id = ctx.IDENTIFIER(0)
		def line = id.symbol.line
		def startCol = id.symbol.charPositionInLine + 1
		def qualifiedName = ctx.IDENTIFIER(0).text
		def position = new Position(line, line, startCol, startCol + qualifiedName.length())
		def (packageName, className) = Parser.getClassInfo(qualifiedName)

		klass = new Class(
				position,
				filename,
				className,
				packageName,
				qualifiedName,
				hasToken(ctx, "interface"),
				ctx.modifier().any() { hasToken(it, "enum") },
				ctx.modifier().any() { hasToken(it, "static") },
				false, //isInner, missing?
				false  //isAnonymous, missing?
		)
		metadata.classes << klass

		addTypeUsage(ctx.IDENTIFIER(1))
		gatherIdentifiers(ctx.identifierList()).each { addTypeUsage it }
	}

	void exitField(FieldContext ctx) {
		def id = ctx.IDENTIFIER(1)
		def line = id.symbol.line
		def startCol = id.symbol.charPositionInLine + 1
		def type = ctx.IDENTIFIER(0).text + (hasToken(ctx, "[]") ? "[]" : "")
		def name = ctx.IDENTIFIER(1).text
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
		def line = id.symbol.line
		def startCol = id.symbol.charPositionInLine + 1
		def retType = ctx.IDENTIFIER(0).text
		def name = ctx.IDENTIFIER(1).text
		def position = new Position(line, line, startCol, startCol + name.length())

		def paramTypes = gatherIdentifiers(ctx.identifierList())
		def paramTypeNames = paramTypes.collect { it.text }
		def params = paramTypeNames.join(",")

		method = new Method(
				position,
				filename,
				name,
				klass.doopId, //declaringClassDoopId
				retType,
				"<${klass.doopId}: $retType $name($params)>", //doopId
				null, //params, TODO
				paramTypeNames as String[],
				ctx.modifier().any() { hasToken(it, "static") },
				0, //totalInvocations, missing?
				0  //totalAllocations, missing?
		)
		def endline = ctx.methodBody() ? getLastToken(ctx.methodBody()).symbol.line : line
		method.outerPosition = new Position(line, endline, 0, 0)
		metadata.methods << method

		heapCounters = [:]
		methodInvoCounters = [:]

		addTypeUsage(ctx.IDENTIFIER(0))
		paramTypes.each { addTypeUsage it }
		gatherIdentifiers(ctx.throwsExceptions()?.identifierList()).each { addTypeUsage it }
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
		def type = ctx.IDENTIFIER().text
		pending.each { v ->
			v.type = type
			varTypes[v.doopId] = type
		}
		addTypeUsage(ctx.IDENTIFIER())
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

		// Cast Assignment
		if (hasToken(ctx, "(") && !hasToken(ctx, "Phi"))
			addTypeUsage(ctx.IDENTIFIER(1))

		addTypeUsage(ctx.IDENTIFIER(2))
	}

	void exitReturnStmt(ReturnStmtContext ctx) {
		if (ctx.value()?.IDENTIFIER())
			metadata.usages << varUsage(ctx.value().IDENTIFIER(), UsageKind.DATA_READ)
	}

	void exitAllocationStmt(AllocationStmtContext ctx) {
		metadata.usages << varUsage(ctx.IDENTIFIER(0), UsageKind.DATA_WRITE)
		ctx.value().each {
			if (it.IDENTIFIER())
				metadata.usages << varUsage(it.IDENTIFIER(), UsageKind.DATA_READ)
		}

		def typeId = ctx.IDENTIFIER(1)
		def line = typeId.symbol.line
		def type = typeId.text
		def lastToken = getLastToken(ctx)
		int startCol, endCol
		TerminalNode newToken

		if ((newToken = findToken(ctx, "new"))) {
			startCol = newToken.symbol.charPositionInLine + 1
			endCol = typeId.symbol.charPositionInLine + 1 + type.length()
		} else if ((newToken = findToken(ctx, "newarray"))) {
			type = "$type[]" as String
			startCol = newToken.symbol.charPositionInLine + 1
			endCol = lastToken.symbol.charPositionInLine + 2
		} else if ((newToken = findToken(ctx, "newmultiarray"))) {
			def lastIsEmpty = lastToken.text == "[]"
			def dimensions = ctx.value().size() + (lastIsEmpty ? 1 : 0)
			type = type + (1..dimensions).collect { "[]" }.join()
			startCol = newToken.symbol.charPositionInLine + 1
			endCol = lastToken.symbol.charPositionInLine + (lastIsEmpty ? 3 : 2)
		}

		def c = heapCounters[type] ?: 0
		heapCounters[type] = c + 1

		metadata.heapAllocations << new HeapAllocation(
				new Position(line, line, startCol, endCol),
				filename,
				"${method.doopId}/new $type/$c", //doopId
				type,
				method.doopId //allocatingMethodDoopId
		)
	}

	void exitInvokeStmt(InvokeStmtContext ctx) {
		if (ctx.IDENTIFIER(0))
			metadata.usages << varUsage(ctx.IDENTIFIER(0), UsageKind.DATA_WRITE)
		if (ctx.IDENTIFIER(1))
			metadata.usages << varUsage(ctx.IDENTIFIER(1), UsageKind.DATA_READ)

		def methodClassId = ctx.methodSig().IDENTIFIER(0)
		def methodClass = methodClassId.text
		def methodName = ctx.methodSig().IDENTIFIER(2).text

		def line = methodClassId.symbol.line
		def startCol = methodClassId.symbol.charPositionInLine
		def endCol = getLastToken(ctx.methodSig()).symbol.charPositionInLine + 2

		String gDoopId
		if (ctx.dynamicMethodSig())
			gDoopId = dynamicInvokeMiddlePart(ctx)

		if (!gDoopId) {
			def c = methodInvoCounters["$methodClass|$methodName"] ?: 0
			methodInvoCounters["$methodClass|$methodName"] = c + 1
			gDoopId = "${method.doopId}/${methodClass}.$methodName/$c"
		}

		metadata.invocations << new MethodInvocation(
				new Position(line, line, startCol, endCol),
				filename,
				gDoopId, //doopId
				method.doopId //invokingMethodDoopId
		)
	}

	// This follows how Representation.dynamicInvokeMiddlePart()
	// works. Returns null for unsupported bootstrap methods, so
	// that a default path can be followed instead in the caller.
	String dynamicInvokeMiddlePart(InvokeStmtContext ctx) {
		def bootName = "${ctx.methodSig().IDENTIFIER(0).text}.${ctx.methodSig().IDENTIFIER(2).text}"

		def bootArgs = values[ctx.bootValueList().valueList()]
		if (!bootArgs)
			println("Warning: invokedynamic with null bootArgs in $filename")
		else if (bootArgs.size() > 1) {
			def v = bootArgs[1].methodSig()
			if (v) {
				def declClass = v.IDENTIFIER(0).text
				def mName = v.IDENTIFIER(2).text
				def invoId = DynamicMethodInvocation.genId(declClass, mName)
                                if (bootName == "java.lang.invoke.LambdaMetafactory.metafactory" ||
                                    bootName == "java.lang.invoke.LambdaMetafactory.altMetafactory") {
                                        def c = methodInvoCounters[invoId] ?: 0
                                        methodInvoCounters[invoId] = c+1
                                        return "${method.doopId}/${invoId}/$c"
                                }
                                else
                                        println("Warning: unsupported invokedynamic, unknown boot method: $bootName in $filename")
			} else
				println("Warning: unsupported invokedynamic, unknown boot argument 2: ${bootArgs[1].text} in $filename")
		} else
			println("Warning: unsupported invokedynamic, unknown boot arguments of arity ${bootArgs.size()} in $filename")
		return null
	}

	void exitMethodSig(MethodSigContext ctx) {
		addTypeUsage(ctx.IDENTIFIER(0))
		addTypeUsage(ctx.IDENTIFIER(1))
		gatherIdentifiers(ctx.identifierList()).each { addTypeUsage it }
	}

	void exitValueList(ValueListContext ctx) {
		values[ctx] = ((values[ctx.valueList()] ?: []) << ctx.value())

		if (ctx.value().IDENTIFIER())
			metadata.usages << varUsage(ctx.value().IDENTIFIER(), UsageKind.DATA_READ)
	}

	void exitJumpStmt(JumpStmtContext ctx) {
		(0..1).each {
			if (ctx.value(it)?.IDENTIFIER())
				metadata.usages << varUsage(ctx.value(it).IDENTIFIER(), UsageKind.DATA_READ)
		}
	}

	void visitErrorNode(ErrorNode node) {
		throw new RuntimeException("Parsing error in $filename")
	}


	Variable var(TerminalNode id, boolean isLocal) {
		def line = id.symbol.line
		def startCol = id.symbol.charPositionInLine + 1
		def name = id.text

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

		if (varTypes[v.doopId])
			v.type = varTypes[v.doopId]
		else
			pending.push(v)
		return v
	}

	Usage varUsage(TerminalNode id, UsageKind kind) {
		def line = id.symbol.line
		def startCol = id.symbol.charPositionInLine + 1
		def name = id.text

		def u = new Usage(
				new Position(line, line, startCol, startCol + name.length()),
				filename,
				"${method.doopId}/$name", //doopId
				kind
		)
	}

	Usage fieldUsage(FieldSigContext ctx, UsageKind kind) {
		def klass = ctx.IDENTIFIER(0).text
		def type = ctx.IDENTIFIER(1).text
		def name = ctx.IDENTIFIER(2).text

		def line = ctx.IDENTIFIER(0).symbol.line
		def startCol = ctx.IDENTIFIER(0).symbol.charPositionInLine
		def endCol = getLastToken(ctx).symbol.charPositionInLine + 1

		def u = new Usage(
				new Position(line, line, startCol, endCol),
				filename,
				"<$klass: $type $name>", //doopId
				kind
		)
	}

	void addTypeUsage(TerminalNode id) {
		if (!id) return

		def line = id.symbol.line
		def startCol = id.symbol.charPositionInLine + 1
		def name = id.text

		if (name == "void") return

		metadata.usages << new Usage(
				new Position(line, line, startCol, startCol + name.length()),
				filename,
				name, //doopId
				UsageKind.TYPE
		)
	}

	List<TerminalNode> gatherIdentifiers(IdentifierListContext ctx) {
		if (ctx == null) return []
		return gatherIdentifiers(ctx.identifierList()) + [ctx.IDENTIFIER()]
	}

	TerminalNode findToken(ParserRuleContext ctx, String token) {
		for (def i = 0; i < ctx.getChildCount(); i++)
			if (ctx.getChild(i) instanceof TerminalNode && (ctx.getChild(i) as TerminalNode).text == token)
				return ctx.getChild(i) as TerminalNode
		return null
	}

	boolean hasToken(ParserRuleContext ctx, String token) {
		findToken(ctx, token) != null
	}

	TerminalNode getLastToken(ParserRuleContext ctx) {
		TerminalNode last
		for (def i = 0; i < ctx.getChildCount(); i++)
			if (ctx.getChild(i) instanceof TerminalNode)
				last = ctx.getChild(i)
		return last
	}
}
