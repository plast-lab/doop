package org.clyze.deepdoop.actions

import org.clyze.deepdoop.datalog.Program
import org.clyze.deepdoop.datalog.clause.Constraint
import org.clyze.deepdoop.datalog.clause.Declaration
import org.clyze.deepdoop.datalog.clause.Rule
import org.clyze.deepdoop.datalog.component.CmdComponent
import org.clyze.deepdoop.datalog.component.Component
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.atom.*

class NormalizeVisitingActor extends PostOrderVisitor<IVisitable> implements IActor<IVisitable>, TDummyActor<IVisitable> {

	NormalizeVisitingActor() {
		// Implemented this way, because Java doesn't allow usage of "this"
		// keyword before all implicit/explicit calls to super/this have
		// returned
		super(null)
		actor = this
	}

	Program exit(Program n, Map<IVisitable, IVisitable> m) {
		// Flatten components that extend other components
		def newComps = [:]
		n.comps.values().each {
			Component currComp = m[it] as Component
			Component flatComp
			if (currComp.superComp) {
				flatComp = currComp.clone()
				while (currComp.superComp) {
					currComp = m[n.comps[currComp.superComp]] as Component
					flatComp.addAll(currComp)
				}
			} else
				flatComp = currComp

			newComps[flatComp.name] = flatComp
		}
		return new Program(m[n.globalComp] as Component, newComps, n.inits, n.props)
	}

	Component exit(Component n, Map<IVisitable, IVisitable> m) {
		def newComp = new Component(n.name, n.superComp)
		n.declarations.each { newComp.addDecl(m[it] as Declaration) }
		//n.constraints.each { newComp.addCons(m[it] as Constraint) }
		n.rules.each { newComp.addRule(m[it] as Rule) }
		return newComp
	}

	Constraint exit(Constraint n, Map<IVisitable, IVisitable> m) {
		new Constraint(m[n.head], m[n.body])
	}

	Declaration exit(Declaration n, Map<IVisitable, IVisitable> m) { n }

	Rule exit(Rule n, Map<IVisitable, IVisitable> m) {
		new Rule(m[n.head], m[n.body], false)
	}

	AggregationElement exit(AggregationElement n, Map<IVisitable, IVisitable> m) {
		new AggregationElement(n.var, n.predicate, m[n.body])
	}

	GroupElement exit(GroupElement n, Map<IVisitable, IVisitable> m) {
		new GroupElement(m[n.element])
	}

	// Flatten LogicalElement "trees"
	LogicalElement exit(LogicalElement n, Map<IVisitable, IVisitable> m) {
		def newElements = []
		n.elements.each { e ->
			def flatE = m[e] as IElement
			if (flatE instanceof LogicalElement && (flatE as LogicalElement).type == n.type)
				newElements += (flatE as LogicalElement).elements
			else
				newElements << flatE
		}
		return new LogicalElement(n.type, newElements)
	}

	NegationElement exit(NegationElement n, Map<IVisitable, IVisitable> m) {
		new NegationElement(m[n.element])
	}

	CmdComponent exit(CmdComponent n, Map<IVisitable, IVisitable> m) { n }

	ComparisonElement exit(ComparisonElement n, Map<IVisitable, IVisitable> m) { n }

	Constructor exit(Constructor n, Map<IVisitable, IVisitable> m) { n }

	Entity exit(Entity n, Map<IVisitable, IVisitable> m) { n }

	Functional exit(Functional n, Map<IVisitable, IVisitable> m) { n }

	Predicate exit(Predicate n, Map<IVisitable, IVisitable> m) { n }

	Primitive exit(Primitive n, Map<IVisitable, IVisitable> m) { n }

	RefMode exit(RefMode n, Map<IVisitable, IVisitable> m) { n }
}
