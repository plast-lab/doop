package org.clyze.deepdoop.datalog

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.ParseTreeProperty
import org.antlr.v4.runtime.tree.TerminalNode
import org.clyze.deepdoop.datalog.clause.*
import org.clyze.deepdoop.datalog.component.*
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.LogicalElement.LogicType
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*
import static org.clyze.deepdoop.datalog.DatalogParser.*

class DatalogListenerImpl extends DatalogBaseListener {

	ParseTreeProperty<String>          _name
	ParseTreeProperty<List<String>>    _names
	ParseTreeProperty<IElement>        _elem
	ParseTreeProperty<List<IElement>>  _elems
	ParseTreeProperty<List<IAtom>>     _atoms
	ParseTreeProperty<IExpr>           _expr
	ParseTreeProperty<List<IExpr>>     _exprs

	boolean                            _inDecl
	Component                          _currComp
	String                             _currCompName
	Program                            _program

	DatalogListenerImpl(String filename) {
		_name       = new ParseTreeProperty<>()
		_names      = new ParseTreeProperty<>()
		_elem       = new ParseTreeProperty<>()
		_elems      = new ParseTreeProperty<>()
		_atoms      = new ParseTreeProperty<>()
		_expr       = new ParseTreeProperty<>()
		_exprs      = new ParseTreeProperty<>()
		_program    = new Program()
		_currComp   = _program.globalComp

		SourceManager.v().setOutputFile(new File(filename).getAbsolutePath())
	}

	Program getProgram() { return _program }

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
		_currCompName = ctx.IDENTIFIER(0).getText()
		if (ctx.L_BRACK() != null) {
			recLoc(ctx)
			_currComp = new Component(_currCompName, (ctx.IDENTIFIER(1) == null ? null : ctx.IDENTIFIER(1).getText()))
		}
	}
	void exitComp(CompContext ctx) {
		if (ctx.identifierList() != null) {
			def inits = get(_names, ctx.identifierList())
			inits.each { id -> _program.addInit(id, _currCompName) }
		}
		if (ctx.L_BRACK() != null) {
			_program.addComponent(_currComp)
			_currComp = _program.globalComp
		}
	}
	void enterCmd(CmdContext ctx) {
		_currCompName = ctx.IDENTIFIER().getText()
		if (ctx.L_BRACK() != null) {
			recLoc(ctx)
			_currComp = new CmdComponent(_currCompName)
		}
	}
	void exitCmd(CmdContext ctx) {
		if (ctx.identifierList() != null) {
			def inits = get(_names, ctx.identifierList())
			inits.each{ id -> _program.addInit(id, _currCompName) }
		}
		if (ctx.L_BRACK() != null) {
			_program.addComponent(_currComp)
			_currComp = _program.globalComp
		}
	}
	void exitInitialize(InitializeContext ctx) {
		def compName = ctx.IDENTIFIER().getText()
		def inits = get(_names, ctx.identifierList())
		inits.each{ id -> _program.addInit(id, compName) }
	}
	void exitPropagate(PropagateContext ctx) {
		def predNames = (ctx.ALL() != null ? [] as Set : get(_names, ctx.predicateNameList()).collect() as Set)
		Set<IAtom> preds = [] as Set
		predNames.each{ predName -> preds.add(new StubAtom(predName)) }
		_program.addPropagation(
				ctx.IDENTIFIER(0).getText(),
				preds,
				ctx.GLOBAL() != null ? null : ctx.IDENTIFIER(1).getText())
	}
	void exitPredicateNameList(PredicateNameListContext ctx) {
		def predName = get(_name, ctx.predicateName())
		def list = get(_names, ctx.predicateNameList(), predName)
		_names.put(ctx, list)
	}
	void enterDeclaration(DeclarationContext ctx) {
		_inDecl = true
	}
	void exitDeclaration(DeclarationContext ctx) {
		recLoc(ctx)
		_inDecl = false

		if (ctx.refmode() == null) {
			def typesList = get(_atoms, ctx.predicateList())
			Set<IAtom> types = [] as Set
			if (typesList != null)
				for (IAtom type : typesList)
					if (type instanceof Predicate) {
						def p = type as Predicate
						types.add(new Entity(p.name, p.stage, p.exprs))
					}
					else
						types.add(type)

			def atom = get(_elem, ctx.predicate()) as IAtom
			if (types.isEmpty()) {
				def p = atom as Predicate
				atom = new Entity(p.name, p.stage, p.exprs)
			}

			if (isConstraint(atom, types))
				_currComp.addCons(new Constraint(atom, new LogicalElement(LogicType.AND, types)))
			else
				_currComp.addDecl(new Declaration(atom, types))
		}
		else {
			def refmode = get(_elem, ctx.refmode()) as RefMode
			List<IExpr> exprs = [ refmode.entityVar ]
			def entity = new Entity(get(_name, ctx.predicateName()), exprs)
			def primitive = get(_elem, ctx.predicate()) as Primitive
			_currComp.addDecl(new RefModeDeclaration(refmode, entity, primitive))
		}
	}

	void exitConstraint(ConstraintContext ctx) {
		recLoc(ctx)
		_currComp.addCons(new Constraint(get(_elem, ctx.ruleBody(0)), get(_elem, ctx.ruleBody(1))))
	}

	void exitRule_(Rule_Context ctx) {
		recLoc(ctx)
		if (ctx.predicateList() != null) {
			def headAtoms = get(_atoms, ctx.predicateList())
			def firstAtom = headAtoms.first()
			if (firstAtom instanceof Directive && firstAtom.name() == "lang:entity") {
				assert headAtoms.size() == 1
				_currComp.markEntity((firstAtom as Directive).backtick.name())
			}
			def head = new LogicalElement(LogicType.AND, headAtoms.collect() as Set)
			def body = get(_elem, ctx.ruleBody()) as IElement
			_currComp.addRule(new Rule(head, body))
		} else {
			LogicalElement head = new LogicalElement(get(_elem, ctx.predicate()))
			AggregationElement aggregation = (AggregationElement) get(_elem, ctx.aggregation())
			_currComp.addRule(new Rule(head, aggregation))
		}
	}

	void exitPredicate(PredicateContext ctx) {
		recLoc(ctx)
		assert (_inDecl && ctx.BACKTICK() == null) || (!_inDecl && ctx.CAPACITY() == null)

		String      name     = get(_name, ctx.predicateName(0))
		List<IExpr> exprs    = (ctx.exprList() == null ? [] : get(_exprs, ctx.exprList()))
		IExpr       expr     = get(_expr, ctx.expr())
		String      capacity = (ctx.CAPACITY() == null ? null : ctx.CAPACITY().getText())
		String      stage    = (ctx.AT_STAGE() == null ? null : ctx.AT_STAGE().getText())
		StubAtom    backtick = (ctx.BACKTICK() == null ? null : new StubAtom(get(_name, ctx.predicateName(1))))

		boolean     isRefMode    = (ctx.predicateName(0) == null)
		boolean     isFunctional = hasToken(ctx, "[")
		boolean     isPrimitive  = (!isRefMode && (capacity != null || isPrimitive(name)))
		boolean     isPredicate  = (!isRefMode && !isFunctional && !isPrimitive)
		boolean     isDirective  = (!isRefMode && (name.startsWith("lang:") || ctx.BACKTICK() != null))

		if (isPrimitive)
			_elem.put(ctx, new Primitive(name, capacity, (VariableExpr) exprs.get(0)))
		else if (isPredicate && !isDirective)
			_elem.put(ctx, new Predicate(name, stage, exprs))
		else if (isPredicate && isDirective)
			_elem.put(ctx, new Directive(name, backtick))
		else if (isFunctional && !isDirective)
			_elem.put(ctx, new Functional(name, stage, exprs, expr))
		else if (isFunctional && isDirective)
			_elem.put(ctx, new Directive(name, backtick, (ConstantExpr)expr))
		else if (isRefMode)
			_elem.put(ctx, get(_elem, ctx.refmode()))
	}

	void exitRuleBody(RuleBodyContext ctx) {
		recLoc(ctx)
		IElement result

		if (ctx.comparison() != null)
			result = get(_elem, ctx.comparison())
		else if (ctx.predicate() != null)
			result = get(_elem, ctx.predicate())
		else if (ctx.ruleBody(1) == null)
			result = new GroupElement(get(_elem, ctx.ruleBody(0)))
		else {
			def token = getToken(ctx, 0)
			List<IElement> list = [ get(_elem, ctx.ruleBody(0)), get(_elem, ctx.ruleBody(1)) ]
			result = new LogicalElement(token.equals(",") ? LogicType.AND : LogicType.OR, list)
		}

		def token = getToken(ctx, 0)
		if (token != null && token == "!")
			result = new NegationElement(result)

		_elem.put(ctx, result)
	}

	void exitAggregation(AggregationContext ctx) {
		recLoc(ctx)
		def variable = new VariableExpr(ctx.IDENTIFIER().getText())
		def predicate = get(_elem, ctx.predicate()) as Predicate
		def body = get(_elem, ctx.ruleBody())
		_elem.put(ctx, new AggregationElement(variable, predicate, body))
	}

	void exitRefmode(RefmodeContext ctx) {
		recLoc(ctx)
		def name  = get(_name, ctx.predicateName())
		def stage = (ctx.AT_STAGE() == null ? null : ctx.AT_STAGE().getText())
		_elem.put(ctx, new RefMode(name, stage, new VariableExpr(ctx.IDENTIFIER().getText()), get(_expr, ctx.expr())))
	}

	void exitPredicateName(PredicateNameContext ctx) {
		recLoc(ctx)
		def child = ctx.predicateName()
		def name = ctx.IDENTIFIER().getText()
		if (child != null)
			name = get(_name, child) + ":" + name
		_name.put(ctx, name)
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
			_expr.put(ctx, new ConstantExpr(constant))
		}
		else if (ctx.REAL() != null)    _expr.put(ctx, new ConstantExpr(Double.parseDouble(ctx.REAL().getText())))
		else if (ctx.BOOLEAN() != null) _expr.put(ctx, new ConstantExpr(Boolean.parseBoolean(ctx.BOOLEAN().getText())))
		else if (ctx.STRING() != null)  _expr.put(ctx, new ConstantExpr(ctx.STRING().getText()))
	}

	void exitExpr(ExprContext ctx) {
		IExpr e
		if (ctx.IDENTIFIER() != null)
			e = new VariableExpr(ctx.IDENTIFIER().getText())
		else if (ctx.predicateName() != null) {
			def name = get(_name, ctx.predicateName())
			def stage = ctx.AT_STAGE() == null ? null : ctx.AT_STAGE().getText()
			List<IExpr> exprs = (ctx.exprList() == null ? [] : get(_exprs, ctx.exprList()))
			e = new FunctionalHeadExpr(name, stage, exprs)
		}
		else if (ctx.constant() != null)
			e = get(_expr, ctx.constant())
		else {
			List<ExprContext> exprs = ctx.expr()
			if (exprs.size() == 2) {
				def left = get(_expr, exprs.get(0))
				BinOperator op = null
				switch (getToken(ctx, 0)) {
					case "+": op = BinOperator.PLUS ; break
					case "-": op = BinOperator.MINUS; break
					case "*": op = BinOperator.MULT ; break
					case "/": op = BinOperator.DIV  ; break
				}
				def right = get(_expr, exprs.get(1))
				e = new BinaryExpr(left, op, right)
			}
			else
				e = new GroupExpr(get(_expr, exprs.get(0)))
		}

		_expr.put(ctx, e)
	}

	void exitComparison(ComparisonContext ctx) {
		def token = getToken(ctx, 0)
		def left = get(_expr, ctx.expr(0))
		def right = get(_expr, ctx.expr(1))
		BinOperator op = null
		switch (token) {
			case "=" : op = BinOperator.EQ ; break
			case "<" : op = BinOperator.LT ; break
			case "<=": op = BinOperator.LEQ; break
			case ">" : op = BinOperator.GT ; break
			case ">=": op = BinOperator.GEQ; break
			case "!=": op = BinOperator.NEQ; break
		}

		_elem.put(ctx, new ComparisonElement(left, op, right))
	}

	void exitPredicateList(PredicateListContext ctx) {
		def atom = get(_elem, ctx.predicate()) as IAtom
		List<IAtom> list = get(_atoms, ctx.predicateList(), atom)
		_atoms.put(ctx, list)
	}

	void exitExprList(ExprListContext ctx) {
		def p = get(_expr, ctx.expr())
		List<IExpr> list = get(_exprs, ctx.exprList(), p)
		_exprs.put(ctx, list)
	}

	void exitIdentifierList(IdentifierListContext ctx) {
		def id = ctx.IDENTIFIER().getText()
		List<String> list = get(_names, ctx.identifierList(), id)
		_names.put(ctx, list)
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
