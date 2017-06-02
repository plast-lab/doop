package org.clyze.deepdoop.datalog

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.TerminalNode
import org.clyze.deepdoop.actions.InfoCollectingVisitingActor
import org.clyze.deepdoop.actions.NormalizeVisitingActor
import org.clyze.deepdoop.datalog.clause.Constraint
import org.clyze.deepdoop.datalog.clause.Declaration
import org.clyze.deepdoop.datalog.clause.RefModeDeclaration
import org.clyze.deepdoop.datalog.clause.Rule
import org.clyze.deepdoop.datalog.component.CmdComponent
import org.clyze.deepdoop.datalog.component.Component
import org.clyze.deepdoop.datalog.component.Propagation
import org.clyze.deepdoop.datalog.component.Propagation.Alias
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.LogicalElement.LogicType
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.ErrorId
import org.clyze.deepdoop.system.ErrorManager
import org.clyze.deepdoop.system.SourceManager

import static org.clyze.deepdoop.datalog.Annotation.Kind.*
import static org.clyze.deepdoop.datalog.DatalogParser.*

class DatalogListenerImpl extends DatalogBaseListener {

	def values = [:]
	def inRArrow = false
	Component currComp
	InfoCollectingVisitingActor infoActor = new InfoCollectingVisitingActor()
	List<Annotation> pendingAnnotations = []

	def program = new Program()

	DatalogListenerImpl(String filename) {
		currComp = program.globalComp
		SourceManager.instance.setOutputFile(new File(filename).getAbsolutePath())
	}

	void enterComponent(ComponentContext ctx) {
		recLoc(ctx)
		currComp = new Component(ctx.IDENTIFIER(0).text, ctx.IDENTIFIER(1)?.text)
	}

	void exitComponent(ComponentContext ctx) {
		values[ctx.identifierList()].each { String id ->
			program.addInit(id, currComp.name)
		}
		program.addComponent(currComp)
		currComp = program.globalComp
	}

	void enterCmd(CmdContext ctx) {
		recLoc(ctx)
		currComp = new CmdComponent(ctx.IDENTIFIER().text)
	}

	void exitCmd(CmdContext ctx) {
		values[ctx.identifierList()].each { String id ->
			program.addInit(id, currComp.name)
		}
		program.addComponent(currComp)
		currComp = program.globalComp
	}

	void exitInitialize(InitializeContext ctx) {
		values[ctx.identifierList()].each { String id ->
			program.addInit(id, ctx.IDENTIFIER().text)
		}
	}

	void exitPropagate(PropagateContext ctx) {
		program.addPropagation(new Propagation(
				ctx.IDENTIFIER(0).text,
				(values[ctx.propagationList()] + []) as Set,
				ctx.IDENTIFIER(1)?.text))
	}

	void enterRightArrow(RightArrowContext ctx) {
		inRArrow = true
		recLoc(ctx)
	}

	void exitRightArrow(RightArrowContext ctx) {
		inRArrow = false
		def loc = SourceManager.instance.getLastLoc()

		def annotations = gatherAnnotations(ctx.annotationList()) + pendingAnnotations

		if (ctx.predicateName()) {
			validateAnnotations("Entity", annotations)
			def entity = new Entity(values[ctx.predicateName()] as String, new VariableExpr("x"))
			def d = new Declaration(entity, [], annotations)
			values[ctx] = d
			currComp.addDecl(d)
		} else if (ctx.refmode()) {
			def p = values[ctx.normalPredicate(0)] as Predicate
			def entity = new Entity(p.name, p.stage, p.exprs.first())
			def refmode = values[ctx.refmode()] as RefMode
			p = values[ctx.normalPredicate(1)] as Predicate
			def primitive = new Primitive(p.name, p.exprs.first() as VariableExpr)
			def d = new RefModeDeclaration(refmode, entity, primitive)
			values[ctx] = d
			currComp.addDecl(d)
		} else {
			if (!annotations.any { it.kind == CONSTRAINT }) {
				validateAnnotations("Declaration", annotations)

				def headCompound = values[ctx.compound(0)] as LogicalElement
				assert headCompound.elements.size() == 1
				def atom = headCompound.elements.first() as IAtom

				// Types might appear out of order in body
				def types = []
				atom.accept(infoActor)
				def varsInHead = infoActor.vars[atom]
				def bodyCompound = (values[ctx.compound(1)] as LogicalElement)
						.accept(new NormalizeVisitingActor()) as LogicalElement
				bodyCompound.elements.each { t ->
					def p = t as Predicate
					assert p.arity == 1
					def type = Primitive.isPrimitive(p.name) ?
							new Primitive(p.name, p.exprs.first() as VariableExpr) :
							new Entity(p.name, p.stage, p.exprs.first())

					type.accept(infoActor)
					def vars = infoActor.vars[type]
					assert vars.size() == 1
					def index = varsInHead.indexOf(vars.first())
					if (index == -1)
						ErrorManager.error(loc, ErrorId.UNKNOWN_VAR, vars.first().name)
					types[index] = type
				}
				assert types.size() == varsInHead.size()

				if (annotations.any { it.kind == CONSTRUCTOR }) {
					assert atom instanceof Functional
					atom = new Constructor(atom as Functional, types.last() as IAtom)
				}

				def d = new Declaration(atom, types, annotations)
				values[ctx] = d
				currComp.addDecl(d)
			} else {
				validateAnnotations("Constraint", annotations)
				def headCompound = values[ctx.compound(0)] as LogicalElement
				def bodyCompound = values[ctx.compound(1)] as LogicalElement
				currComp.addCons(new Constraint(headCompound, bodyCompound))
			}
		}
	}

	void enterRightArrowBlock(RightArrowBlockContext ctx) {
		recLoc(ctx)
		pendingAnnotations = gatherAnnotations(ctx.annotationList())
	}

	void exitRightArrowBlock(RightArrowBlockContext ctx) {
		pendingAnnotations = []
	}

	void enterLeftArrow(LeftArrowContext ctx) {
		recLoc(ctx)
	}

	void exitLeftArrow(LeftArrowContext ctx) {
		if (ctx.predicateListExt()) {
			def headAtoms = values[ctx.predicateListExt()] as List<IAtom>
			def head = new LogicalElement(headAtoms)
			def body = ctx.compound() ? values[ctx.compound()] as LogicalElement : null
			currComp.addRule(new Rule(head, body))
		} else {
			def head = new LogicalElement(values[ctx.functional()] as Functional)
			def aggregation = values[ctx.aggregation()] as AggregationElement
			currComp.addRule(new Rule(head, new LogicalElement(aggregation)))
		}
	}

	void exitPredicate(PredicateContext ctx) {
		if (ctx.refmode()) values[ctx] = values[ctx.refmode()]
		else if (ctx.functional()) values[ctx] = values[ctx.functional()]
		else if (ctx.normalPredicate()) values[ctx] = values[ctx.normalPredicate()]
	}

	void exitRefmode(RefmodeContext ctx) {
		recLoc(ctx)
		values[ctx] = new RefMode(
				values[ctx.predicateName()] as String,
				ctx.AT_STAGE()?.text,
				new VariableExpr(ctx.IDENTIFIER().text),
				values[ctx.expr()] as IExpr)
	}

	void exitFunctional(FunctionalContext ctx) {
		recLoc(ctx)
		def name = values[ctx.predicateName()] as String
		def stage = ctx.AT_STAGE()?.text
		def keyExprs = (ctx.exprList() ? values[ctx.exprList()] : []) as List<IExpr>
		def valueExpr = values[ctx.expr()] as IExpr
		values[ctx] = new Functional(name, stage, keyExprs, valueExpr)
	}

	void exitNormalPredicate(NormalPredicateContext ctx) {
		recLoc(ctx)
		values[ctx] = new Predicate(
				values[ctx.predicateName()] as String,
				ctx.AT_STAGE()?.text,
				values[ctx.exprList()] as List<IExpr>)
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

	void exitPredicateListExt(PredicateListExtContext ctx) {
		def el = ctx.predicate() ? values[ctx.predicate()] : values[ctx.construction()]
		values[ctx] = (values[ctx.predicateListExt()] ?: []) << el
	}

	void exitCompoundElement(CompoundElementContext ctx) {
		IElement el
		if (ctx.predicate()) el = values[ctx.predicate()] as IElement
		else if (ctx.comparison()) el = values[ctx.comparison()] as IElement
		else el = values[ctx.compoundElement()] as IElement

		if (hasToken(ctx, "!")) values[ctx] = new NegationElement(el)
		else if (hasToken(ctx, "(")) values[ctx] = new GroupElement(el)
		else values[ctx] = el
	}

	void exitCompound(CompoundContext ctx) {
		def el = values[ctx.compoundElement()] as IElement
		if (ctx.compound()) {
			def compound = values[ctx.compound()] as IElement
			def type = hasToken(ctx, ",") ? LogicType.AND : LogicType.OR
			values[ctx] = new LogicalElement(type, [compound, el])
		} else {
			values[ctx] = new LogicalElement(el)
		}
	}

	void exitIdentifierList(IdentifierListContext ctx) {
		values[ctx] = ((values[ctx.identifierList()] ?: []) as List<String>) << ctx.IDENTIFIER().text
	}

	void exitPropagationElement(PropagationElementContext ctx) {
		if (ctx.ALL())
			values[ctx] = new Alias(orig: null, alias: null)
		else if (ctx.AS()) {
			def orig = values[ctx.predicateName(0)] as String
			def alias = values[ctx.predicateName(1)] as String
			values[ctx] = new Alias(orig: new Stub(orig), alias: new Stub(alias))
		} else {
			def orig = values[ctx.predicateName(0)] as String
			values[ctx] = new Alias(orig: new Stub(orig), alias: null)
		}
	}

	void exitPropagationList(PropagationListContext ctx) {
		values[ctx] = (values[ctx.propagationList()] ?: []) << values[ctx.propagationElement()]
	}

	// Special handling for annotation lists (instead of using "exit")
	List<Annotation> gatherAnnotations(AnnotationListContext ctx) {
		if (!ctx) return []
		def annotation = new Annotation(ctx.annotation().IDENTIFIER().text)
		return gatherAnnotations(ctx.annotationList()) << annotation
	}

	void enterLineMarker(LineMarkerContext ctx) {
		// Line number of the original file (emitted by C-Preprocessor)
		def markerLine = Integer.parseInt(ctx.INTEGER(0).text)
		// Actual line in the output file for this line marker
		def markerActualLine = ctx.start.getLine()
		// Name of the original file (emitted by C-Preprocessor)
		def sourceFile = ctx.STRING().text
		// Remove quotes from file values
		sourceFile = sourceFile.substring(1, sourceFile.length() - 1)

		// Ignore first line of output. It reports the values of the C-Preprocessed file
		if (markerActualLine == 1) return
		// Ignore lines for system info (e.g. <built-in> or /usr/include/stdc-predef.h)
		if (sourceFile.startsWith("<") || sourceFile.startsWith("/usr/include")) return

		def t = (ctx.INTEGER(1) != null ? Integer.parseInt(ctx.INTEGER(1).text) : 0)
		// 1 - Start of a new file
		if (t == 0 || t == 1)
			SourceManager.instance.lineMarkerStart(markerLine, markerActualLine, sourceFile)
		// 2 - Returning to previous file
		else if (t == 2)
			SourceManager.instance.lineMarkerEnd()
		// 3 - Following text comes from a system header file (#include <> vs #include "")
		// 4 - Following text should be treated as being wrapped in an implicit extern "C" block.
		else
			println "*** Weird line marker flag: $t ***"
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
			} else if (str.startsWith("0") && str.length() > 1) {
				str = str.substring(1)
				constant = Long.parseLong(str, 8)
			} else if (str.startsWith("2^")) {
				str = str.substring(2)
				constant = 1L << Integer.parseInt(str)
			} else {
				constant = Long.parseLong(str, 10)
			}
			values[ctx] = new ConstantExpr(constant)
		} else if (ctx.REAL()) values[ctx] = new ConstantExpr(Double.parseDouble(ctx.REAL().text))
		else if (ctx.BOOLEAN()) values[ctx] = new ConstantExpr(Boolean.parseBoolean(ctx.BOOLEAN().text))
		else if (ctx.STRING()) values[ctx] = new ConstantExpr(ctx.STRING().text)
	}

	void exitExpr(ExprContext ctx) {
		if (ctx.IDENTIFIER())
			values[ctx] = new VariableExpr(ctx.IDENTIFIER().text)
		else if (ctx.constant())
			values[ctx] = values[ctx.constant()] as ConstantExpr
		else if (hasToken(ctx, "("))
			values[ctx] = new GroupExpr(values[ctx.expr(0)] as IExpr)
		else {
			def e1 = ctx.expr(0) as IExpr
			def e2 = ctx.expr(1) as IExpr
			BinOperator op
			if (hasToken(ctx, "+")) op = BinOperator.PLUS
			else if (hasToken(ctx, "-")) op = BinOperator.MINUS
			else if (hasToken(ctx, "*")) op = BinOperator.MULT
			else op = BinOperator.DIV

			values[ctx] = new BinaryExpr(e1, op, e2)
		}
	}

	void exitExprList(ExprListContext ctx) {
		values[ctx] = (values[ctx.exprList()] ?: []) << values[ctx.expr()]
	}

	void exitComparison(ComparisonContext ctx) {
		def e1 = values[ctx.expr(0)] as IExpr
		def e2 = values[ctx.expr(1)] as IExpr
		BinOperator op
		if (hasToken(ctx, "=")) op = BinOperator.EQ
		else if (hasToken(ctx, "<")) op = BinOperator.LT
		else if (hasToken(ctx, "<=")) op = BinOperator.LEQ
		else if (hasToken(ctx, ">")) op = BinOperator.GT
		else if (hasToken(ctx, ">=")) op = BinOperator.GEQ
		else op = BinOperator.NEQ

		values[ctx] = new ComparisonElement(e1, op, e2)
	}

	void visitErrorNode(ErrorNode node) {
		throw new RuntimeException("Parsing error")
	}

	static boolean hasToken(ParserRuleContext ctx, String token) {
		for (def i = 0; i < ctx.getChildCount(); i++)
			if (ctx.getChild(i) instanceof TerminalNode && (ctx.getChild(i) as TerminalNode).text == token)
				return true
		return false
	}

	static void recLoc(ParserRuleContext ctx) {
		SourceManager.instance.recLoc(ctx.start.getLine())
	}

	static void validateAnnotations(String key, def annotations) {
		def allowedAnnotations = [
				"Entity"     : EnumSet.of(ENTITY, OUTPUT),
				"Declaration": EnumSet.of(CONSTRUCTOR, INPUT, OUTPUT),
				"Constraint" : EnumSet.of(CONSTRAINT),
		]
		def expectedAnnotations = allowedAnnotations[key]

		def loc = SourceManager.instance.getLastLoc()
		annotations.each {
			if (!(it.kind in expectedAnnotations))
				ErrorManager.error(loc, ErrorId.INVALID_ANNOTATION, it.kind, key)
		}
	}
}
