package org.clyze.deepdoop.actions

import org.clyze.deepdoop.datalog.*
import org.clyze.deepdoop.datalog.clause.*
import org.clyze.deepdoop.datalog.component.*
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.atom.*

class NormalizeVisitingActor extends PostOrderVisitor<IVisitable> implements IActor<IVisitable>, TDummyActor<IVisitable> {

	Map<String, Component> allComps

	NormalizeVisitingActor(Map<String, Component> allComps) {
		// Implemented this way, because Java doesn't allow usage of "this"
		// keyword before all implicit/explicit calls to super/this have
		// returned
		super(null)
		actor = this

		this.allComps = allComps
	}

	Program exit(Program n, Map<IVisitable, IVisitable> m) {
		Program flatP = Program.from(n.globalComp, [:], n.inits, n.props)
		n.comps.values().each{ flatP.addComponent(m[it] as Component) }
		return flatP
	}

	CmdComponent exit(CmdComponent n, Map<IVisitable, IVisitable> m) { n }

	// Flatten components that extend other components
	Component exit(Component n, Map<IVisitable, IVisitable> m) {
		Component currComp = n
		Component flatComp = new Component(currComp)
		while (currComp.superComp != null) {
			currComp = allComps[currComp.superComp]
			flatComp.addAll(currComp)
		}
		return flatComp
	}

	Constraint exit(Constraint n, Map<IVisitable, IVisitable> m) {
		def head = m[n.head] as IElement
		def body = m[n.body] as IElement
		return (head == n.head && body == n.body ? n : new Constraint(head, body))
	}

	Rule exit(Rule n, Map<IVisitable, IVisitable> m) {
		def head = m[n.head] as LogicalElement
		def body = m[n.body] as IElement
		return (head == n.head && body == n.body ? n : new Rule(head, body, false))
	}

	AggregationElement exit(AggregationElement n, Map<IVisitable, IVisitable> m) {
		def body = m[n.body] as IElement
		return (body == n.body ? n : new AggregationElement(n.var, n.predicate, body))
	}

	ComparisonElement exit(ComparisonElement n, Map<IVisitable, IVisitable> m) { n }

	GroupElement exit(GroupElement n, Map<IVisitable, IVisitable> m) {
		def e = m[n.element] as IElement
		return (e == n.element ? n : new GroupElement(e))
	}

	// Flatten LogicalElement "trees"
	LogicalElement exit(LogicalElement n, Map<IVisitable, IVisitable> m) {
		Set<IElement> newElements = []
		n.elements.each{ e ->
			def flatE = m[e] as IElement
			if (flatE instanceof LogicalElement && (flatE as LogicalElement).type == n.type)
				newElements << (flatE as LogicalElement).elements
			else
				newElements << flatE
		}
		return new LogicalElement(n.type, newElements)
	}

	NegationElement exit(NegationElement n, Map<IVisitable, IVisitable> m) {
		def e = m[n.element] as IElement
		return (e == n.element ? n : new NegationElement(e))
	}

	Constructor exit(Constructor n, Map<IVisitable, IVisitable> m) { n }
	Directive exit(Directive n, Map<IVisitable, IVisitable> m)     { n }
	Entity exit(Entity n, Map<IVisitable, IVisitable> m)           { n }
	Functional exit(Functional n, Map<IVisitable, IVisitable> m)   { n }
	Predicate exit(Predicate n, Map<IVisitable, IVisitable> m)     { n }
	Primitive exit(Primitive n, Map<IVisitable, IVisitable> m)     { n }
	RefMode exit(RefMode n, Map<IVisitable, IVisitable> m)         { n }
}
