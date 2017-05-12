package org.clyze.deepdoop.actions

import groovy.transform.InheritConstructors
import org.clyze.deepdoop.datalog.*
import org.clyze.deepdoop.datalog.clause.*
import org.clyze.deepdoop.datalog.component.*
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

@InheritConstructors
class SouffleCodeGenVisitingActor extends DefaultCodeGenVisitingActor {

	boolean inDecl
	File currentFile

	String visit(Program p) {
		currentFile = createUniqueFile("out_", ".dl")
		results << new Result(Result.Kind.LOGIC, currentFile)

		// Transform program before visiting nodes
		def normalP = p.accept(new PostOrderVisitor<IVisitable>(new NormalizingActor(p.comps))) as Program
		def n = normalP.accept(new InitVisitingActor()) as Program
		return super.visit(n)
	}


	void enter(Declaration n) { inDecl = true }

	String exit(Declaration n, Map<IVisitable, String> m) {
		inDecl = false
		if (n.atom instanceof Entity) {
			def rest = (n.types.isEmpty() ? "" : " = ${n.types.first().name}")
			def entityName = n.atom.name
			emit ".type ${entityName}$rest"
			emit ".decl is${entityName}(?x:${entityName})"
		}
		else {
			def params = n.types.collect{ m[it] }.join(", ")
			emit ".decl ${n.atom.name}($params)"
		}
		return null
	}

	String exit(Rule n, Map<IVisitable, String> m) {
		emit "${m[n.head]} :- ${m[n.body]}."
	}


	String exit(ComparisonElement n, Map<IVisitable, String> m) { m[n.expr] }

	String exit(GroupElement n, Map<IVisitable, String> m) { "(${m[n.element]})" }

	String exit(LogicalElement n, Map<IVisitable, String> m) {
		def delim = (n.type == LogicalElement.LogicType.AND ? ", " : "; ")
		return n.elements.collect { m[it] }.join(delim)
	}

	String exit(NegationElement n, Map<IVisitable, String> m) { "!${m[n.element]}" }


	String exit(Constructor n, Map<IVisitable, String> m) {
		def params = (n.keyExprs + [n.valueExpr]).collect{ m[it] }.join(", ")
		return "is${n.type.name}($params)"
	}

	String exit(Entity n, Map<IVisitable, String> m) {
		if (inDecl) {
			return "${m[n.exprs.first()]}:${n.name}"
		}
		return null
	}

	String exit(Functional n, Map<IVisitable, String> m) {
		def params = (n.keyExprs + [n.valueExpr]).collect{ m[it] }.join(", ")
		return "${n.name}($params)"
	}

	String exit(Predicate n, Map<IVisitable, String> m) {
		def params = n.exprs.collect{ m[it] }.join(", ")
		return "${n.name}($params)"
	}

	String exit(Primitive n, Map<IVisitable, String> m) {
		if (inDecl) {
			return "${m[n.var]}:${n.name}"
		}
		return null
	}

	String exit(BinaryExpr n, Map<IVisitable, String> m) { "${m[n.left]} ${n.op} ${m[n.right]}" }

	String exit(ConstantExpr n, Map<IVisitable, String> m) { n.value as String }

	String exit(FunctionalHeadExpr n, Map<IVisitable, String> m) { m[n.functional] }

	String exit(GroupExpr n, Map<IVisitable, String> m) { "(${m[n.expr]})" }

	String exit(VariableExpr n, Map<IVisitable, String> m) { n.name }


	def emit(def data) { write currentFile, data }
}
