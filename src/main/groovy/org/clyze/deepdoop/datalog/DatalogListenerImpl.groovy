package org.clyze.deepdoop.datalog

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.ParseTreeProperty
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

	ParseTreeProperty<String>         name
	ParseTreeProperty<List<String>>   names
	ParseTreeProperty<Alias>          prop
	ParseTreeProperty<List<Alias>>    props
	ParseTreeProperty<IElement>       elem
	ParseTreeProperty<List<IElement>> elems
	ParseTreeProperty<List<IAtom>>    atoms
	ParseTreeProperty<IExpr>          expr
	ParseTreeProperty<List<IExpr>>    exprs

	boolean                           inDecl
	Component                         currComp
	String                            currCompName
	Program                           program

	DatalogListenerImpl(String filename) {
		name     = new ParseTreeProperty<>()
		names    = new ParseTreeProperty<>()
		prop     = new ParseTreeProperty<>()
		props    = new ParseTreeProperty<>()
		elem     = new ParseTreeProperty<>()
		elems    = new ParseTreeProperty<>()
		atoms    = new ParseTreeProperty<>()
		expr     = new ParseTreeProperty<>()
		exprs    = new ParseTreeProperty<>()
		program  = new Program()
		currComp = program.globalComp

		SourceManager.v().setOutputFile(new File(filename).getAbsolutePath())
	}

	void enterLineMarker(LineMarkerContext ctx) {
		// Line number of the original file (emitted by C-Preprocessor)
		def markerLine = Integer.parseInt(ctx.INTEGER(0).getText())
		// Actual line in the output file for this line marker
		def markerActualLine = ctx.start.getLine()
		// Name of the original file (emitted by C-Preprocessor)
		def sourceFile = ctx.STRING().getText()
		// Remove quotes from file name
		sourceFile = sourceFile.substring(1, sourceFile.length()-1)

		// Ignore first line of output. It reports the name of the C-Preprocessed file
		if (markerActualLine == 1) return
		// Ignore lines for system info (e.g. <built-in> or /usr/include/stdc-predef.h)
		if (sourceFile.startsWith("<") || sourceFile.startsWith("/usr/include")) return

		def t = (ctx.INTEGER(1) != null ? Integer.parseInt(ctx.INTEGER(1).getText()) : 0)
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

	void enterComp(CompContext ctx) {
		currCompName = ctx.IDENTIFIER(0).getText()
		if (ctx.L_BRACK() != null) {
			recLoc(ctx)
			currComp = new Component(currCompName, (ctx.IDENTIFIER(1) == null ? null : ctx.IDENTIFIER(1).getText()))
		}
	}
	void exitComp(CompContext ctx) {
		if (ctx.identifierList() != null) {
			def inits = get(names, ctx.identifierList())
			inits.each { id -> program.addInit(id, currCompName) }
		}
		if (ctx.L_BRACK() != null) {
			program.addComponent(currComp)
			currComp = program.globalComp
		}
	}
	void enterCmd(CmdContext ctx) {
		currCompName = ctx.IDENTIFIER().getText()
		if (ctx.L_BRACK() != null) {
			recLoc(ctx)
			currComp = new CmdComponent(currCompName)
		}
	}
	void exitCmd(CmdContext ctx) {
		if (ctx.identifierList() != null) {
			def inits = get(names, ctx.identifierList())
			inits.each{ id -> program.addInit(id, currCompName) }
		}
		if (ctx.L_BRACK() != null) {
			program.addComponent(currComp)
			currComp = program.globalComp
		}
	}
	void exitInitialize(InitializeContext ctx) {
		def compName = ctx.IDENTIFIER().getText()
		def inits = get(names, ctx.identifierList())
		inits.each{ program.addInit(it, compName) }
	}
	void exitPropagate(PropagateContext ctx) {
		program.addPropagation(new Propagation(
				fromId: ctx.IDENTIFIER(0).getText(),
				preds: get(props, ctx.propagationList()).collect() as Set,
				toId: ctx.IDENTIFIER(1)?.getText() ))
	}
	void exitPropagationElement(PropagationElementContext ctx) {
		if (ctx.ALL() != null)
			prop.put(ctx, new Alias(orig: null, alias: null))
		else if (ctx.AS() != null) {
			def orig  = get(name, ctx.predicateName(0))
			def alias = get(name, ctx.predicateName(1))
			prop.put(ctx, new Alias(orig: new Stub(orig), alias: new Stub(alias)))
		}
		else {
			def orig  = get(name, ctx.predicateName(0))
			prop.put(ctx, new Alias(orig: new Stub(orig), alias: null))
		}
	}
	void exitPropagationList(PropagationListContext ctx) {
		def propElement = get(prop, ctx.propagationElement())
		def list = get(props, ctx.propagationList(), propElement)
		props.put(ctx, list)
	}
	void enterDeclaration(DeclarationContext ctx) {
		inDecl = true
	}
	void exitDeclaration(DeclarationContext ctx) {
		recLoc(ctx)
		inDecl = false

		if (ctx.refmode() == null) {
			def typesList = get(atoms, ctx.predicateList())
			Set<IAtom> types = [] as Set
			if (typesList != null)
				for (IAtom type : typesList)
					if (type instanceof Predicate) {
						def p = type as Predicate
						types.add(new Entity(p.name, p.stage, p.exprs))
					}
					else
						types.add(type)

			def atom = get(elem, ctx.predicate()) as IAtom
			if (types.isEmpty()) {
				def p = atom as Predicate
				atom = new Entity(p.name, p.stage, p.exprs)
			}

			if (isConstraint(atom, types))
				currComp.addCons(new Constraint(atom, new LogicalElement(LogicType.AND, types)))
			else
				currComp.addDecl(new Declaration(atom, types))
		}
		else {
			def refmode = get(elem, ctx.refmode()) as RefMode
			List<IExpr> exprs = [ refmode.entityVar ]
			def entity = new Entity(get(name, ctx.predicateName()), exprs)
			def primitive = get(elem, ctx.predicate()) as Primitive
			currComp.addDecl(new RefModeDeclaration(refmode, entity, primitive))
		}
	}

	void exitConstraint(ConstraintContext ctx) {
		recLoc(ctx)
		currComp.addCons(new Constraint(get(elem, ctx.ruleBody(0)), get(elem, ctx.ruleBody(1))))
	}

	void exitRule_(Rule_Context ctx) {
		recLoc(ctx)
		if (ctx.predicateList() != null) {
			def headAtoms = get(atoms, ctx.predicateList())
			def firstAtom = headAtoms.first()
			if (firstAtom instanceof Directive && firstAtom.name() == "lang:entity") {
				assert headAtoms.size() == 1
				currComp.markEntity((firstAtom as Directive).backtick.name())
			}
			def head = new LogicalElement(LogicType.AND, headAtoms.collect() as Set)
			def body = get(elem, ctx.ruleBody()) as IElement
			currComp.addRule(new Rule(head, body))
		} else {
			LogicalElement head = new LogicalElement(get(elem, ctx.predicate()))
			AggregationElement aggregation = (AggregationElement) get(elem, ctx.aggregation())
			currComp.addRule(new Rule(head, aggregation))
		}
	}

	void exitPredicate(PredicateContext ctx) {
		recLoc(ctx)
		assert (inDecl && ctx.BACKTICK() == null) || (!inDecl && ctx.CAPACITY() == null)

		String      name     = get(name, ctx.predicateName(0))
		List<IExpr> exprs    = (ctx.exprList() == null ? [] : get(exprs, ctx.exprList()))
		IExpr       expr     = get(expr, ctx.expr())
		String      capacity = (ctx.CAPACITY() == null ? null : ctx.CAPACITY().getText())
		String      stage    = (ctx.AT_STAGE() == null ? null : ctx.AT_STAGE().getText())
		Stub backtick = (ctx.BACKTICK() == null ? null : new Stub(get(this.name, ctx.predicateName(1))))

		boolean     isRefMode    = (ctx.predicateName(0) == null)
		boolean     isFunctional = hasToken(ctx, "[")
		boolean     isPrimitive  = (!isRefMode && (capacity != null || isPrimitive(name)))
		boolean     isPredicate  = (!isRefMode && !isFunctional && !isPrimitive)
		boolean     isDirective  = (!isRefMode && (name.startsWith("lang:") || ctx.BACKTICK() != null))

		if (isPrimitive)
			elem.put(ctx, new Primitive(name, capacity, (VariableExpr) exprs.get(0)))
		else if (isPredicate && !isDirective)
			elem.put(ctx, new Predicate(name, stage, exprs))
		else if (isPredicate && isDirective)
			elem.put(ctx, new Directive(name, backtick))
		else if (isFunctional && !isDirective)
			elem.put(ctx, new Functional(name, stage, exprs, expr))
		else if (isFunctional && isDirective)
			elem.put(ctx, new Directive(name, backtick, (ConstantExpr)expr))
		else if (isRefMode)
			elem.put(ctx, get(elem, ctx.refmode()))
	}

	void exitRuleBody(RuleBodyContext ctx) {
		recLoc(ctx)
		IElement result

		if (ctx.comparison() != null)
			result = get(elem, ctx.comparison())
		else if (ctx.predicate() != null)
			result = get(elem, ctx.predicate())
		else if (ctx.ruleBody(1) == null)
			result = new GroupElement(get(elem, ctx.ruleBody(0)))
		else {
			def token = getToken(ctx, 0)
			List<IElement> list = [get(elem, ctx.ruleBody(0)), get(elem, ctx.ruleBody(1)) ]
			result = new LogicalElement(token.equals(",") ? LogicType.AND : LogicType.OR, list)
		}

		def token = getToken(ctx, 0)
		if (token != null && token == "!")
			result = new NegationElement(result)

		elem.put(ctx, result)
	}

	void exitAggregation(AggregationContext ctx) {
		recLoc(ctx)
		def variable = new VariableExpr(ctx.IDENTIFIER().getText())
		def predicate = get(elem, ctx.predicate()) as Predicate
		def body = get(elem, ctx.ruleBody())
		elem.put(ctx, new AggregationElement(variable, predicate, body))
	}

	void exitRefmode(RefmodeContext ctx) {
		recLoc(ctx)
		def name  = get(name, ctx.predicateName())
		def stage = (ctx.AT_STAGE() == null ? null : ctx.AT_STAGE().getText())
		elem.put(ctx, new RefMode(name, stage, new VariableExpr(ctx.IDENTIFIER().getText()), get(expr, ctx.expr())))
	}

	void exitPredicateName(PredicateNameContext ctx) {
		recLoc(ctx)
		def child = ctx.predicateName()
		def name = ctx.IDENTIFIER().getText()
		if (child != null)
			name = get(this.name, child) + ":" + name
		this.name.put(ctx, name)
	}

	void exitConstant(ConstantContext ctx) {
		if (ctx.INTEGER() != null) {
			def str = ctx.INTEGER().getText()
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
			expr.put(ctx, new ConstantExpr(constant))
		}
		else if (ctx.REAL() != null)    expr.put(ctx, new ConstantExpr(Double.parseDouble(ctx.REAL().getText())))
		else if (ctx.BOOLEAN() != null) expr.put(ctx, new ConstantExpr(Boolean.parseBoolean(ctx.BOOLEAN().getText())))
		else if (ctx.STRING() != null)  expr.put(ctx, new ConstantExpr(ctx.STRING().getText()))
	}

	void exitExpr(ExprContext ctx) {
		IExpr e
		if (ctx.IDENTIFIER() != null)
			e = new VariableExpr(ctx.IDENTIFIER().getText())
		else if (ctx.predicateName() != null) {
			def name = get(name, ctx.predicateName())
			def stage = ctx.AT_STAGE() == null ? null : ctx.AT_STAGE().getText()
			List<IExpr> exprs = (ctx.exprList() == null ? [] : get(exprs, ctx.exprList()))
			e = new FunctionalHeadExpr(name, stage, exprs)
		}
		else if (ctx.constant() != null)
			e = get(expr, ctx.constant())
		else {
			List<ExprContext> exprs = ctx.expr()
			if (exprs.size() == 2) {
				def left = get(expr, exprs.get(0))
				BinOperator op = null
				switch (getToken(ctx, 0)) {
					case "+": op = BinOperator.PLUS ; break
					case "-": op = BinOperator.MINUS; break
					case "*": op = BinOperator.MULT ; break
					case "/": op = BinOperator.DIV  ; break
				}
				def right = get(expr, exprs.get(1))
				e = new BinaryExpr(left, op, right)
			}
			else
				e = new GroupExpr(get(expr, exprs.get(0)))
		}

		expr.put(ctx, e)
	}

	void exitComparison(ComparisonContext ctx) {
		def token = getToken(ctx, 0)
		def left = get(expr, ctx.expr(0))
		def right = get(expr, ctx.expr(1))
		BinOperator op = null
		switch (token) {
			case "=" : op = BinOperator.EQ ; break
			case "<" : op = BinOperator.LT ; break
			case "<=": op = BinOperator.LEQ; break
			case ">" : op = BinOperator.GT ; break
			case ">=": op = BinOperator.GEQ; break
			case "!=": op = BinOperator.NEQ; break
		}

		elem.put(ctx, new ComparisonElement(left, op, right))
	}

	void exitPredicateList(PredicateListContext ctx) {
		def atom = get(elem, ctx.predicate()) as IAtom
		List<IAtom> list = get(atoms, ctx.predicateList(), atom)
		atoms.put(ctx, list)
	}

	void exitExprList(ExprListContext ctx) {
		def p = get(expr, ctx.expr())
		List<IExpr> list = get(exprs, ctx.exprList(), p)
		exprs.put(ctx, list)
	}

	void exitIdentifierList(IdentifierListContext ctx) {
		def id = ctx.IDENTIFIER().getText()
		List<String> list = get(names, ctx.identifierList(), id)
		names.put(ctx, list)
	}

	void visitErrorNode(ErrorNode node) {
		throw new RuntimeException("Parsing error")
	}

	static <T> T get(ParseTreeProperty<T> values, ParseTree node) {
		T t = values.get(node)
		values.removeFrom(node)
		return t
	}
	static <T> List<T> get(ParseTreeProperty<List<T>> values, ParseTree node, T newValue) {
		List<T> lt
		if (node != null)
			lt = get(values, node)
		else
			lt = []
		lt.add(newValue)
		return lt
	}
	static String getToken(ParserRuleContext ctx, int index) {
		for (int i = 0; i < ctx.getChildCount(); i++)
			if (ctx.getChild(i) instanceof TerminalNode && index-- == 0)
				return (ctx.getChild(i) as TerminalNode).getText()
		return null
	}
	static boolean hasToken(ParserRuleContext ctx, String token) {
		for (int i = 0; i < ctx.getChildCount(); i++)
			if (ctx.getChild(i) instanceof TerminalNode &&
				(ctx.getChild(i) as TerminalNode).getText().equals(token))
				return true
		return false
	}
	static boolean isPrimitive(String name) {
		switch (name) {
			case "uint":
			case "int":
			case "float":
			case "decimal":
			case "boolean":
			case "string":
				return true
			default:
				return false
		}
	}
	static boolean isConstraint(IAtom atom, Set<IAtom> types) {
		if (atom.getVars().stream().any{ v -> v.isDontCare }) return true

		// Entities declaration
		if (types.isEmpty()) return false

		if (types.stream().any{ t ->
				List<VariableExpr> vars = t.getVars()
			return vars.size() != 1 || vars.get(0).isDontCare
		}) return true

		def bodyCount = types.sum{ it.getVars().size() }
		return (atom.arity() != bodyCount)
	}
	static void recLoc(ParserRuleContext ctx) {
		SourceManager.v().recLoc(ctx.start.getLine())
	}
}
