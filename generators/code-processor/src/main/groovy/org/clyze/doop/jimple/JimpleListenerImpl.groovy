package org.clyze.doop.jimple

import groovy.transform.CompileStatic
import org.antlr.v4.runtime.ANTLRFileStream
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.antlr.v4.runtime.tree.TerminalNode
import org.clyze.persistent.metadata.jvm.JvmMetadata
import org.clyze.persistent.model.Element
import org.clyze.persistent.model.Position
import org.clyze.persistent.model.Usage
import org.clyze.persistent.model.UsageKind
import org.clyze.persistent.model.jvm.JvmClass
import org.clyze.persistent.model.jvm.JvmDynamicMethodInvocation
import org.clyze.persistent.model.jvm.JvmField
import org.clyze.persistent.model.jvm.JvmHeapAllocation
import org.clyze.persistent.model.jvm.JvmMethod
import org.clyze.persistent.model.jvm.JvmMethodInvocation
import org.clyze.persistent.model.jvm.JvmVariable
import org.codehaus.groovy.runtime.StackTraceUtils

import java.util.function.Consumer
import java.util.regex.Pattern

import static org.clyze.doop.jimple.JimpleParser.*
//import static org.clyze.doop.common.JavaRepresentation.stripQuotes

@CompileStatic
class JimpleListenerImpl extends JimpleBaseListener {

	String filename
	List<JvmVariable> pending
	Map varTypes = [:]
	Map heapCounters
	JvmClass klass
	JvmMethod method
	Map methodInvoCounters
	Map values = [:]
	boolean inDecl
	boolean inInterface

	JvmMetadata metadata = new JvmMetadata()

	Consumer<Element> processor

	JimpleListenerImpl(String filename, Consumer<Element> processor = null) {
		this.filename = filename
		if (processor)
			this.processor = processor
		else
			this.processor = { Element e ->
				if (e instanceof JvmClass) metadata.jvmClasses << (e as JvmClass)
				else if (e instanceof JvmField) metadata.jvmFields << (e as JvmField)
				else if (e instanceof JvmMethod) metadata.jvmMethods << (e as JvmMethod)
				else if (e instanceof JvmVariable) metadata.jvmVariables << (e as JvmVariable)
				else if (e instanceof Usage) metadata.usages << (e as Usage)
				else if (e instanceof JvmHeapAllocation) metadata.jvmHeapAllocations << (e as JvmHeapAllocation)
				else if (e instanceof JvmMethodInvocation) metadata.jvmInvocations << (e as JvmMethodInvocation)
			}
	}

	void enterKlass(KlassContext ctx) {
		def id = ctx.IDENTIFIER(0)
		def line = id.symbol.line
		def startCol = id.symbol.charPositionInLine + 1
		def qualifiedName = stripQuotes(ctx.IDENTIFIER(0).text)
		def position = new Position(line, line, startCol, startCol + qualifiedName.length())
		def packageName = getPackageName(qualifiedName)
		def className = getClassName(qualifiedName)
		inInterface = hasToken(ctx, "interface")

		def modifier = ctx.modifier()
		boolean isEnum      = modifier? modifier.any { it.text == "enum" }   : false
		boolean isStatic    = modifier? modifier.any { it.text == "static" } : false
		boolean isInner     = false
		boolean isAnonymous = false
		boolean isAbstract  = modifier? modifier.any { it.text == "abstract" } : false
		boolean isFinal     = modifier? modifier.any { it.text == "final" } : false
		boolean isPublic    = modifier? modifier.any { it.text == "public" } : false
		boolean isProtected = modifier? modifier.any { it.text == "protected" } : false
		boolean isPrivate   = modifier? modifier.any { it.text == "private" } : false

		klass = new JvmClass(
				position,
				filename,
				false,
				null,
				className,
				packageName,
				qualifiedName,
				inInterface,
				isEnum,
				isStatic,
				isInner,
				isAnonymous,
				isAbstract,
				isFinal,
				isPublic,
				isProtected,
				isPrivate
		)
		processor.accept klass

		addTypeUsage(ctx.IDENTIFIER(1))
		gatherIdentifiers(ctx.identifierList()).each { addTypeUsage it }
	}

	void exitField(FieldContext ctx) {
		def id = ctx.IDENTIFIER(1)
		def line = id.symbol.line
		def startCol = id.symbol.charPositionInLine + 1
		def type = ctx.IDENTIFIER(0).text + (hasToken(ctx, "[]") ? "[]" : "")
		def name = stripQuotes(ctx.IDENTIFIER(1).text)
		def position = new Position(line, line, startCol, startCol + name.length())

		processor.accept new JvmField(
				position,
				filename,
				false,
				null,
				name,
				"<${klass.symbolId}: $type $name>", //doopId
				type,
				klass.symbolId, //declaringClassId
				ctx.modifier().any() { it.text == "static" }
		)
	}

	void enterMethod(MethodContext ctx) {
		def id = ctx.IDENTIFIER(1)
		def line = id.symbol.line
		def startCol = id.symbol.charPositionInLine + 1
		def retType = ctx.IDENTIFIER(0).text
		def name = stripQuotes(ctx.IDENTIFIER(1).text)
		def position = new Position(line, line, startCol, startCol + name.length())

		def paramTypes = gatherIdentifiers(ctx.identifierList())
		def paramTypeNames = paramTypes.collect { it.text }
		def params = paramTypeNames.join(",")

		def endline = ctx.methodBody() ? getLastToken(ctx.methodBody()).symbol.line : line

		def modifier = ctx.modifier()
		boolean isStatic       = modifier ? modifier.any { it.text == "static" } : false
		boolean isAbstract     = modifier ? modifier.any { it.text == "abstract" } : false
		boolean isNative       = modifier ? modifier.any { it.text == "native" } : false
		boolean isSynchronized = modifier ? modifier.any { it.text == "synchronized" } : false
		boolean isFinal        = modifier ? modifier.any { it.text == "final" } : false
		boolean isSynthetic    = false
		boolean isPublic       = modifier ? modifier.any { it.text == "public" } : false
		boolean isProtected    = modifier ? modifier.any { it.text == "protected" } : false
		boolean isPrivate      = modifier ? modifier.any { it.text == "private" } : false

		method = new JvmMethod(
				position,
				filename,
				false,
				null,
				name,
				klass.symbolId, //declaringClassId
				retType,
				"<${klass.symbolId}: $retType $name($params)>", //doopId
				null, //params, TODO
				paramTypeNames as String[],
				isStatic,
				inInterface,
				isAbstract,
				isNative,
				isSynchronized,
				isFinal,
				isSynthetic,
				isPublic,
				isProtected,
				isPrivate,
				new Position(line, endline, 0, 0)
		)

		heapCounters = [:].withDefault { 0 }
		methodInvoCounters = [:].withDefault { 0 }
		processor.accept method

		addTypeUsage(ctx.IDENTIFIER(0))
		paramTypes.each { addTypeUsage it }
		gatherIdentifiers(ctx.throwsExceptions()?.identifierList()).each { addTypeUsage it }
	}

	void exitIdentifierList(IdentifierListContext ctx) {
		if (inDecl) processor.accept var(ctx.IDENTIFIER(), true)
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
			varTypes[v.symbolId] = type
		}
		addTypeUsage(ctx.IDENTIFIER())
	}

	void exitComplexAssignmentStmt(ComplexAssignmentStmtContext ctx) {
		if (ctx.IDENTIFIER())
			addVarUsage(ctx.IDENTIFIER(), UsageKind.DATA_READ)

		if (ctx.fieldSig())
			processor.accept fieldUsage(ctx.fieldSig(), UsageKind.DATA_WRITE)

		(0..1).each {
			if (ctx.value(it)?.IDENTIFIER())
				addVarUsage(ctx.value(it).IDENTIFIER(), UsageKind.DATA_READ)
		}
	}

	void exitAssignmentStmt(AssignmentStmtContext ctx) {
		addVarUsage(ctx.IDENTIFIER(0), UsageKind.DATA_WRITE)

		// @parameterN and @this
		if (ctx.IDENTIFIER(2)) {
			def v = var(ctx.IDENTIFIER(1), true)
			v.type = ctx.IDENTIFIER(2).text
			pending.pop()
			processor.accept v
			addTypeUsage(ctx.IDENTIFIER(2))
		} else if (ctx.IDENTIFIER(1))
			addVarUsage(ctx.IDENTIFIER(1), UsageKind.DATA_READ)

		// Cast Assignment
		if (hasToken(ctx, "(") && !hasToken(ctx, "Phi"))
			addTypeUsage(ctx.IDENTIFIER(1))
		// Read field
		else if (ctx.fieldSig())
			processor.accept fieldUsage(ctx.fieldSig(), UsageKind.DATA_READ)
		// Phi
		else if (hasToken(ctx, "Phi"))
			gatherIdentifiers(ctx.identifierList()).each { addVarUsage(it, UsageKind.DATA_READ) }
	}

	void exitReturnStmt(ReturnStmtContext ctx) {
		if (ctx.value()?.IDENTIFIER())
			addVarUsage(ctx.value().IDENTIFIER(), UsageKind.DATA_READ)
	}

	void exitAllocationStmt(AllocationStmtContext ctx) {
		addVarUsage(ctx.IDENTIFIER(0), UsageKind.DATA_WRITE)
		ctx.value().each {
			if (it.IDENTIFIER())
				addVarUsage(it.IDENTIFIER(), UsageKind.DATA_READ)
		}

		def typeId = ctx.IDENTIFIER(1)
		def line = typeId.symbol.line
		def type = typeId.text
		def lastToken = getLastToken(ctx)
		int startCol = 0, endCol = 0
		TerminalNode newToken
		boolean isArray = false

		if ((newToken = findToken(ctx, "new"))) {
			startCol = newToken.symbol.charPositionInLine + 1
			endCol = typeId.symbol.charPositionInLine + 1 + type.length()
		} else if ((newToken = findToken(ctx, "newarray"))) {
			type = "$type[]" as String
			startCol = newToken.symbol.charPositionInLine + 1
			endCol = lastToken.symbol.charPositionInLine + 2
			isArray = true
		} else if ((newToken = findToken(ctx, "newmultiarray"))) {
			def lastIsEmpty = lastToken.text == "[]"
			def dimensions = ctx.value().size() + (lastIsEmpty ? 1 : 0)
			type = type + (1..dimensions).collect { "[]" }.join("")
			startCol = newToken.symbol.charPositionInLine + 1
			endCol = lastToken.symbol.charPositionInLine + (lastIsEmpty ? 3 : 2)
			isArray = true
		}

		def c = heapCounters[type] as int
		heapCounters[type] = c + 1

		processor.accept new JvmHeapAllocation(
				new Position(line, line, startCol, endCol),
				filename,
				false,
				null,
				"${method.symbolId}/new $type/$c", //doopId
				type,
				method.symbolId, //allocatingMethodId
				false, //inIIB
				isArray)
	}

	void exitInvokeStmt(InvokeStmtContext ctx) {
		if (ctx.IDENTIFIER(0)) {
			if (hasToken(ctx, "=")) {
				addVarUsage(ctx.IDENTIFIER(0), UsageKind.DATA_WRITE)
				if (ctx.IDENTIFIER(1))
					addVarUsage(ctx.IDENTIFIER(1), UsageKind.DATA_READ)
			} else
				addVarUsage(ctx.IDENTIFIER(0), UsageKind.DATA_READ)
		}

		def methodClassId = ctx.methodSig().IDENTIFIER(0)
		def methodClass = methodClassId.text
		def methodName = stripQuotes(ctx.methodSig().IDENTIFIER(2).text)

		def line = methodClassId.symbol.line
		def startCol = methodClassId.symbol.charPositionInLine
		def endCol = getLastToken(ctx.methodSig()).symbol.charPositionInLine + 2

		String gDoopId = null
		String targetReturnType = null    // TODO: NOT IMPLEMENTED
		String targetParamTypes = null    // TODO: NOT IMPLEMENTED
		if (ctx.dynamicMethodSig())
			gDoopId = dynamicInvokeMiddlePart(ctx)

		if (!gDoopId) {
			def c = methodInvoCounters["$methodClass|$methodName"] as int
			methodInvoCounters["$methodClass|$methodName"] = c + 1
			gDoopId = "${method.symbolId}/${methodClass}.${methodName}/$c"
		}

		processor.accept new JvmMethodInvocation(
				new Position(line, line, startCol, endCol),
				filename,
				false,
				null,
				methodName,
				gDoopId, //symbolId
				methodClass,
				targetReturnType,
				targetParamTypes,
				method.symbolId, //invokingMethodId
				false //inIIB
		)
	}

	// This follows how Representation.dynamicInvokeIdMiddle() works.
	String dynamicInvokeMiddlePart(InvokeStmtContext ctx) {
		def bootName = "${ctx.methodSig().IDENTIFIER(0).text}.${ctx.methodSig().IDENTIFIER(2).text}"
		def dynamicName = ctx.STRING().text.replaceAll('"', '')
		def invoId = JvmDynamicMethodInvocation.genericId(getClassName(bootName), dynamicName)
		def c = methodInvoCounters[invoId] as int
		methodInvoCounters[invoId] = c + 1
		return "${method.symbolId}/${invoId}/$c"
	}

	void exitMethodSig(MethodSigContext ctx) {
		addTypeUsage(ctx.IDENTIFIER(0))
		addTypeUsage(ctx.IDENTIFIER(1))
		gatherIdentifiers(ctx.identifierList()).each { addTypeUsage it }
	}

	void exitValueList(ValueListContext ctx) {
		def list = (values[ctx.valueList()] ?: []) as List
		values[ctx] = (list << ctx.value())

		if (ctx.value().IDENTIFIER())
			addVarUsage(ctx.value().IDENTIFIER(), UsageKind.DATA_READ)
	}

	void exitJumpStmt(JumpStmtContext ctx) {
		(0..1).each {
			if (ctx.value(it)?.IDENTIFIER())
				addVarUsage(ctx.value(it).IDENTIFIER(), UsageKind.DATA_READ)
		}
	}

	void visitErrorNode(ErrorNode node) {
		throw new RuntimeException("Parsing error in $filename")
	}


	JvmVariable var(TerminalNode id, boolean isLocal) {
		def line = id.symbol.line
		def startCol = id.symbol.charPositionInLine + 1
		def name = stripQuotes(id.text)

		def v = new JvmVariable(
				new Position(line, line, startCol, startCol + name.length()),
				filename,
				false,
				null,
				name,
				"${method.symbolId}/$name", //doopId
				null, //type, provided later
				method.symbolId, //declaringMethodId
				isLocal,
				!isLocal,
				false //inIIB
		)

		if (varTypes[v.symbolId])
			v.type = varTypes[v.symbolId] as String
		else
			pending.push(v)
		return v
	}

	void addVarUsage(TerminalNode id, UsageKind kind) {
		def line = id.symbol.line
		def startCol = id.symbol.charPositionInLine + 1
		def name = stripQuotes(id.text)
		def endCol = startCol + name.length()

		processor.accept new Usage(
				new Position(line, line, startCol, endCol),
				filename,
				false,
				null,
				getUsageId(filename, line, line, startCol, endCol),
				"${method.symbolId}/$name", //doopId
				kind
		)
	}

	Usage fieldUsage(FieldSigContext ctx, UsageKind kind) {
		def klassStr = ctx.IDENTIFIER(0).text
		def type = ctx.IDENTIFIER(1).text
		def name = stripQuotes(ctx.IDENTIFIER(2).text)

		def line = ctx.IDENTIFIER(0).symbol.line
		def startCol = ctx.IDENTIFIER(0).symbol.charPositionInLine
		def endCol = getLastToken(ctx).symbol.charPositionInLine + 1

		new Usage(
				new Position(line, line, startCol, endCol),
				filename,
				false,
				null,
				getUsageId(filename, line, line, startCol, endCol),
				"<$klassStr: $type $name>", //doopId
				kind
		)
	}

	static String getUsageId(String filename, def startLine, def endLine, def startCol, def endCol) {
		return "Usage-${filename}-${endLine}-${endLine}-${startCol}-${endCol}"
	}

	void addTypeUsage(TerminalNode id) {
		if (!id) return

		def line = id.symbol.line
		def startCol = id.symbol.charPositionInLine + 1
		def name = stripQuotes(id.text)
		def endCol = startCol + name.length()

		if (name == "void") return

		processor.accept new Usage(
				new Position(line, line, startCol, endCol),
				filename,
				false,
				null,
				getUsageId(filename, line, line, startCol, endCol),
				name, //doopId
				UsageKind.TYPE
		)
	}

	List<TerminalNode> gatherIdentifiers(IdentifierListContext ctx) {
		if (ctx == null) return []
		return gatherIdentifiers(ctx.identifierList()) + [ctx.IDENTIFIER()]
	}

	static TerminalNode findToken(ParserRuleContext ctx, String token) {
		for (def i = 0; i < ctx.childCount; i++)
			if (ctx.getChild(i) instanceof TerminalNode && (ctx.getChild(i) as TerminalNode).text == token)
				return ctx.getChild(i) as TerminalNode
		return null
	}

	static boolean hasToken(ParserRuleContext ctx, String token) { findToken(ctx, token) != null }

	static TerminalNode getLastToken(ParserRuleContext ctx) {
		TerminalNode last = null
		for (def i = 0; i < ctx.childCount; i++)
			if (ctx.getChild(i) instanceof TerminalNode)
				last = ctx.getChild(i) as TerminalNode
		return last
	}

	static String getPackageName(String qualifiedName) {
		def i = qualifiedName.lastIndexOf(".")
		i >= 0 ? qualifiedName[0..(i - 1)] : ''
	}

	static String getClassName(String qualifiedName) {
		def i = qualifiedName.lastIndexOf(".")
		i >= 0 ? qualifiedName[(i + 1)..-1] : qualifiedName
	}

	static void parseJimple(String filename, String baseDir, Closure processor) {
		// filename: XYZ/abc/def/Foo.jimple
		if (!baseDir.endsWith("/")) baseDir += "/"
		// abc/def/Foo.jimple
		def listener = new JimpleListenerImpl(filename - baseDir, processor)
		def parser = new JimpleParser(new CommonTokenStream(new JimpleLexer(new ANTLRFileStream(filename))))
		try {
			ParseTreeWalker.DEFAULT.walk(listener, parser.program())
		} catch (all) {
			all = StackTraceUtils.deepSanitize all
			throw new Throwable("Jimple File: $filename", all)
		}
	}

    static Walker parseJimpleText(String fileName, String text) {
        def parser = new JimpleParser(new CommonTokenStream(new JimpleLexer(new ANTLRInputStream(text))))
        return new Walker(fileName, parser.program())
    }

    static class Walker {
        private final String fileName
        private final ProgramContext ctx

        Walker(String fileName, ProgramContext ctx) {
            this.fileName = fileName
            this.ctx = ctx
        }

        void walk(Closure processor) {
            def listener = new JimpleListenerImpl(fileName, processor)
            try {
                ParseTreeWalker.DEFAULT.walk(listener, ctx)
            } catch (all) {
                //all = StackTraceUtils.deepSanitize all
				throw new RuntimeException("Jimple class ${listener.filename}: ${all.message}", all)
            }
        }
    }

	private final static Pattern qPat = Pattern.compile("'")
	static String stripQuotes(CharSequence s) {
		return qPat.matcher(s).replaceAll("")
	}

}
