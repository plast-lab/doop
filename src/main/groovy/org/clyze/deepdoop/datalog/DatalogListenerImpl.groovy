package org.clyze.deepdoop.datalog

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.TerminalNode
import org.clyze.deepdoop.datalog.clause.*
import org.clyze.deepdoop.datalog.component.*
import org.clyze.deepdoop.datalog.component.Propagation.Alias
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.LogicalElement.LogicType
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

import static org.clyze.deepdoop.datalog.DatalogParser.*

class DatalogListenerImpl extends DatalogBaseListener {

	def       values = [:]
	def       inDecl = false
	Component currComp
	String    currCompName
	def       program = new Program()

	DatalogListenerImpl(String filename) {
		currComp = program.globalComp
		SourceManager.v().setOutputFile(new File(filename).getAbsolutePath())
	}

	void enterComp(CompContext ctx) {
		currCompName = ctx.IDENTIFIER(0).text
		if (ctx.L_BRACK()) {
			recLoc(ctx)
			currComp = new Component(currCompName, (ctx.IDENTIFIER(1) == null ? null : ctx.IDENTIFIER(1).text))
		}
	}
	void exitComp(CompContext ctx) {
		if (ctx.identifierList()) {
			def inits = values[ctx.identifierList()]
			inits.each { id -> program.addInit(id, currCompName) }
		}
		if (ctx.L_BRACK() != null) {
			program.addComponent(currComp)
			currComp = program.globalComp
		}
	}
	void enterCmd(CmdContext ctx) {
		currCompName = ctx.IDENTIFIER().text
		if (ctx.L_BRACK()) {
			recLoc(ctx)
			currComp = new CmdComponent(currCompName)
		}
	}
	void exitCmd(CmdContext ctx) {
		if (ctx.identifierList()) {
			def inits = values[ctx.identifierList()]
			inits.each{ id -> program.addInit(id, currCompName) }
		}
		if (ctx.L_BRACK()) {
			program.addComponent(currComp)
			currComp = program.globalComp
		}
	}
	void exitInitialize(InitializeContext ctx) {
		def compName = ctx.IDENTIFIER().text
		def inits = values[ctx.identifierList()]
		inits.each{ program.addInit(it, compName) }
	}
	void exitPropagate(PropagateContext ctx) {
		program.addPropagation(new Propagation(
			ctx.IDENTIFIER(0).text,
			(values[ctx.propagationList()] + []) as Set,
			ctx.IDENTIFIER(1)?.text ))
	}
	void exitIdentifierList(IdentifierListContext ctx) {
		def id = ctx.IDENTIFIER().text
		def list = (values[ctx.identifierList()] ?: []) << id
		values[ctx] = list
	}
	void exitPropagationElement(PropagationElementContext ctx) {
		if (ctx.ALL())
			values[ctx] = new Alias(orig: null, alias: null)
		else if (ctx.AS()) {
			def orig  = values[ctx.predicateName(0)]
			def alias = values[ctx.predicateName(1)]
			values[ctx] = new Alias(orig: new Stub(orig), alias: new Stub(alias))
		}
		else {
			def orig  = values[ctx.predicateName(0)]
			values[ctx] = new Alias(orig: new Stub(orig), alias: null)
		}
	}
	void exitPropagationList(PropagationListContext ctx) {
		def propElement = values[ctx.propagationElement()]
		def list = (values[ctx.propagationList()] ?: []) << propElement
		values[ctx] = list
	}

	void enterDeclaration(DeclarationContext ctx) {
		inDecl = true
	}
	void exitDeclaration(DeclarationContext ctx) {
		recLoc(ctx)
		inDecl = false

		if (ctx.refmode()) {
			def p = values[ctx.singleAtom(0)] as Predicate
			def entity = new Entity(p.name, p.stage, p.exprs.first())
			def refmode = values[ctx.refmode()] as RefMode
			p = values[ctx.singleAtom(1)] as Predicate
			def primitive = new Primitive(p.name, p.exprs.first())
			currComp.addDecl(new RefModeDeclaration(refmode, entity, primitive))
		}
		else {
			def atom = values[ctx.predicate()] as IAtom
			def types = []

			// Normal predicate declaration
			if (ctx.predicateList()) {
				values[ctx.predicateList()].each { type ->
					def p = type as Predicate
					assert p.arity == 1
					if (Primitive.isPrimitive(p.name))
						types << new Primitive(p.name, p.exprs.first())
					else
						types << new Entity(p.name, p.stage, p.exprs.first())
				}

				def annotation = values[ctx.annotation()] as Annotation
				if (annotation && annotation.kind == Annotation.Kind.CONSTRUCTOR) {
					assert atom instanceof Functional
					atom = new Constructor(atom as Functional, types.last() as IAtom)
				}
				else if (annotation && annotation.kind == Annotation.Kind.ENTITY) {
					assert atom instanceof Predicate
					assert atom.arity == 1
					def p = atom as Predicate
					atom = new Entity(p.name, null, p.exprs.first())
				}
			}
			// Entity declaration
			else {
				def p = atom as Predicate
				assert p.arity == 1
				atom = new Entity(p.name, null, p.exprs.first())
			}

			if (isConstraint(atom, types))
				currComp.addCons(new Constraint(atom, new LogicalElement(LogicType.AND, types)))
			else
				currComp.addDecl(new Declaration(atom, types))
		}
	}
	void exitConstraint(ConstraintContext ctx) {
		recLoc(ctx)
		currComp.addCons(new Constraint(values[ctx.compound(0)], values[ctx.compound(1)]))
	}
	void exitRule_(Rule_Context ctx) {
		recLoc(ctx)
		if (ctx.predicateList()) {
			def headAtoms = values[ctx.predicateList()]
			def firstAtom = headAtoms.first()
			if (firstAtom instanceof Directive && firstAtom.name == "lang:entity") {
				assert headAtoms.size() == 1
				currComp.markEntity((firstAtom as Directive).backtick.name)
			}
			def head = new LogicalElement(LogicType.AND, headAtoms)
			currComp.addRule(new Rule(head, null))
		}
		else if (ctx.predicateListExt()) {
			def headAtoms = values[ctx.predicateListExt()]
			def head = new LogicalElement(LogicType.AND, headAtoms)
			def bodyElements = values[ctx.compound()] as IElement
			def body = new LogicalElement(LogicType.AND, [bodyElements])
			currComp.addRule(new Rule(head, body))
		}
		else {
			LogicalElement head = new LogicalElement(values[ctx.functional()])
			AggregationElement aggregation = (AggregationElement) values[ctx.aggregation()]
			currComp.addRule(new Rule(head, aggregation))
		}
	}
	void enterLineMarker(LineMarkerContext ctx) {
		// Line number of the original file (emitted by C-Preprocessor)
		def markerLine = Integer.parseInt(ctx.INTEGER(0).text)
		// Actual line in the output file for this line marker
		def markerActualLine = ctx.start.getLine()
		// Name of the original file (emitted by C-Preprocessor)
		def sourceFile = ctx.STRING().text
		// Remove quotes from file values
		sourceFile = sourceFile.substring(1, sourceFile.length()-1)

		// Ignore first line of output. It reports the values of the C-Preprocessed file
		if (markerActualLine == 1) return
		// Ignore lines for system info (e.g. <built-in> or /usr/include/stdc-predef.h)
		if (sourceFile.startsWith("<") || sourceFile.startsWith("/usr/include")) return

		def t = (ctx.INTEGER(1) != null ? Integer.parseInt(ctx.INTEGER(1).text) : 0)
		// 1 - Start of a new file
		if (t == 0 || t == 1)
			SourceManager.v().lineMarkerStart(markerLine, markerActualLine, sourceFile)
		// 2 - Returning to previous file
		else if (t == 2)
			SourceManager.v().lineMarkerEnd()
		// 3 - Following text comes from a system header file (#include <> vs #include "")
		// 4 - Following text should be treated as being wrapped in an implicit extern "C" block.
		else
			// TODO handle in a different way (report it)
			println "Weird line marker flag: $t"
	}

	void exitAnnotation(AnnotationContext ctx) {
		values[ctx] = new Annotation(ctx.IDENTIFIER().text)
	}

	void exitPredicate(PredicateContext ctx) {
		if (ctx.directive())       values[ctx] = values[ctx.directive()]
		else if (ctx.refmode())    values[ctx] = values[ctx.refmode()]
		else if (ctx.singleAtom()) values[ctx] = values[ctx.singleAtom()]
		else if (ctx.atom())       values[ctx] = values[ctx.atom()]
		else if (ctx.functional()) values[ctx] = values[ctx.functional()]
	}
	void exitDirective(DirectiveContext ctx) {
		assert !inDecl
		recLoc(ctx)
		if (ctx.expr())
			values[ctx] = new Directive(
				values[ctx.predicateName(0)],
				new Stub(values[ctx.predicateName(1)]),
				values[ctx.expr()] as ConstantExpr)
		else
			values[ctx] = new Directive(
				values[ctx.predicateName(0)],
				new Stub(values[ctx.predicateName(1)]))
	}
	void exitRefmode(RefmodeContext ctx) {
		recLoc(ctx)
		values[ctx] = new RefMode(
			values[ctx.predicateName()],
			ctx.AT_STAGE()?.text,
			new VariableExpr(ctx.IDENTIFIER().text),
			values[ctx.expr()])
	}
	void exitSingleAtom(SingleAtomContext ctx) {
		recLoc(ctx)
		def name = values[ctx.predicateName()]
		values[ctx] = new Predicate(name, ctx.AT_STAGE()?.text, [values[ctx.expr()] ])
	}
	void exitAtom(AtomContext ctx) {
		recLoc(ctx)
		if (ctx.expr())
			values[ctx] = new Predicate(
				values[ctx.predicateName()],
				ctx.AT_STAGE()?.text,
				[values[ctx.expr()] ] + values[ctx.exprList()])
		else
			values[ctx] = new Predicate(
				values[ctx.predicateName()],
				ctx.AT_STAGE()?.text,
				[])
	}
	void exitFunctionalHead(FunctionalHeadContext ctx) {
		recLoc(ctx)
		values[ctx] = new FunctionalHeadExpr(
			values[ctx.predicateName()],
			ctx.AT_STAGE()?.text,
			ctx.exprList() ? values[ctx.exprList()] : [])
	}
	void exitFunctional(FunctionalContext ctx) {
		recLoc(ctx)
		def fHead = (values[ctx.functionalHead()] as FunctionalHeadExpr).functional
		def valueExpr = values[ctx.expr()]
		if (fHead.name.startsWith("lang:"))
			values[ctx] = new Directive(fHead.name, null, valueExpr)
		else
			values[ctx] = new Functional(fHead.name, fHead.stage, fHead.keyExprs, valueExpr)
	}

	void exitAggregation(AggregationContext ctx) {
		recLoc(ctx)
		values[ctx] = new AggregationElement(
				new VariableExpr(ctx.IDENTIFIER().text),
				values[ctx.predicate()] as Predicate,
				values[ctx.compound()] as IElement)
	}

	void exitConstruction(ConstructionContext ctx) {
		recLoc(ctx)
		values[ctx] = new Constructor(
				values[ctx.functional()] as Functional,
				new Stub(values[ctx.predicateName()] as String))
	}

	void exitPredicateList(PredicateListContext ctx) {
		def atom = values[ctx.predicate()] as IAtom
		def list = (values[ctx.predicateList()] ?: []) << atom
		values[ctx] = list
	}

	void exitPredicateListExt(PredicateListExtContext ctx) {
		def list = (values[ctx.predicateListExt()] ?: [])
		if (ctx.predicate())
			list << (values[ctx.predicate()] as IAtom)
		else
			list << (values[ctx.construction()] as IAtom)
		values[ctx] = list
	}

	void exitCompound(CompoundContext ctx) {
		recLoc(ctx)
		IElement result

		if (ctx.comparison())
			result = values[ctx.comparison()]
		else if (ctx.predicate())
			result = values[ctx.predicate()]
		else if (ctx.compound(1) == null)
			result = new GroupElement(values[ctx.compound(0)])
		else {
			def token = getTerminalToken(ctx, 0)
			List<IElement> list = [values[ctx.compound(0)], values[ctx.compound(1)] ]
			result = new LogicalElement(token.equals(",") ? LogicType.AND : LogicType.OR, list)
		}

		def token = getTerminalToken(ctx, 0)
		if (token != null && token == "!")
			result = new NegationElement(result)

		values[ctx] = result
	}

	void exitPredicateName(PredicateNameContext ctx) {
		recLoc(ctx)
		def name = ctx.IDENTIFIER().text
		if (ctx.predicateName())
			name = values[ctx.predicateName()] + ":" + name
		values[ctx] = name
	}

	void exitConstant(ConstantContext ctx) {
		if (ctx.INTEGER()) {
			def str = ctx.INTEGER().text
			Long constant
			if (str.startsWith("0x") || str.startsWith("0X")) {
				str = str.substring(2)
				constant = Long.parseLong(str, 16)
			}
			else if (str.startsWith("0") && str.length() > 1) {
				str = str.substring(1)
				constant = Long.parseLong(str, 8)
			}
			else if (str.startsWith("2^")) {
				str = str.substring(2)
				constant = 1L << Integer.parseInt(str)
			}
			else {
				constant = Long.parseLong(str, 10)
			}
			values[ctx] = new ConstantExpr(constant)
		}
		else if (ctx.REAL())    values[ctx] = new ConstantExpr(Double.parseDouble(ctx.REAL().text))
		else if (ctx.BOOLEAN()) values[ctx] = new ConstantExpr(Boolean.parseBoolean(ctx.BOOLEAN().text))
		else if (ctx.STRING())  values[ctx] = new ConstantExpr(ctx.STRING().text)
	}

	void exitExpr(ExprContext ctx) {
		IExpr e
		if (ctx.IDENTIFIER())
			e = new VariableExpr(ctx.IDENTIFIER().text)
		else if (ctx.functionalHead())
			e = values[ctx.functionalHead()]
		else if (ctx.constant())
			e = values[ctx.constant()]
		else {
			List<ExprContext> exprs = ctx.expr()
			if (exprs.size() == 2) {
				def left = values[exprs.get(0)]
				BinOperator op = null
				switch (getTerminalToken(ctx, 0)) {
					case "+": op = BinOperator.PLUS ; break
					case "-": op = BinOperator.MINUS; break
					case "*": op = BinOperator.MULT ; break
					case "/": op = BinOperator.DIV  ; break
				}
				def right = values[exprs.get(1)]
				e = new BinaryExpr(left, op, right)
			}
			else
				e = new GroupExpr(values[exprs.get(0)])
		}

		values[ctx] = e
	}

	void exitExprList(ExprListContext ctx) {
		def p = values[ctx.expr()]
		def list = (values[ctx.exprList()] ?: []) << p
		values[ctx] = list
	}

	void exitComparison(ComparisonContext ctx) {
		def token = getTerminalToken(ctx, 0)
		def left = values[ctx.expr(0)] as IExpr
		def right = values[ctx.expr(1)] as IExpr
		BinOperator op = null
		switch (token) {
			case "=" : op = BinOperator.EQ ; break
			case "<" : op = BinOperator.LT ; break
			case "<=": op = BinOperator.LEQ; break
			case ">" : op = BinOperator.GT ; break
			case ">=": op = BinOperator.GEQ; break
			case "!=": op = BinOperator.NEQ; break
		}

		values[ctx] = new ComparisonElement(left, op, right)
	}

	void visitErrorNode(ErrorNode node) {
		throw new RuntimeException("Parsing error")
	}

	static String getTerminalToken(ParserRuleContext ctx, int index) {
		def i = (0..(ctx.getChildCount() - 1))
				.findAll { ctx.getChild(it) instanceof TerminalNode }[index]
		return i != null ? ctx.getChild(i).text : null
	}
	static boolean isConstraint(IAtom atom, List<IAtom> types) {
		if (atom.vars.any { it.isDontCare() }) return true

		// Entities declaration
		if (types.isEmpty()) return false

		if (types.any { t ->
			def vars = t.vars
			return vars.size() != 1 || vars.get(0).isDontCare()
		}) return true

		def bodyCount = types.sum{ it.vars.size() }
		return (atom.arity != bodyCount)
	}
	static void recLoc(ParserRuleContext ctx) {
		SourceManager.v().recLoc(ctx.start.getLine())
	}
}
