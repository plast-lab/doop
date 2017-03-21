package org.clyze.deepdoop.actions

import org.clyze.deepdoop.datalog.*
import org.clyze.deepdoop.datalog.clause.*
import org.clyze.deepdoop.datalog.component.*
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.datalog.expr.*

class FlatteningActor implements IActor<IVisitable> {

	Map<String, Component> _allComps

	FlatteningActor(Map<String, Component> allComps) {
		_allComps = allComps
	}


	@Override
	Program exit(Program n, Map<IVisitable, IVisitable> m) {
		Program flatP = Program.from(n.globalComp, [:], n.inits, n.props)
		n.comps.values().each{ flatP.addComponent(m[it] as Component) }
		return flatP
	}

	@Override
	CmdComponent exit(CmdComponent n, Map<IVisitable, IVisitable> m) {
		return n
	}
	// Flatten components that extend other components
	@Override
	Component exit(Component n, Map<IVisitable, IVisitable> m) {
		Component currComp = n
		Component flatComp = new Component(currComp)
		while (currComp.superComp != null) {
			currComp = _allComps[currComp.superComp]
			flatComp.addAll(currComp)
		}
		return flatComp
	}

	@Override
	Constraint exit(Constraint n, Map<IVisitable, IVisitable> m) {
		def head = m[n.head] as IElement
		def body = m[n.body] as IElement
		return (head == n.head && body == n.body ? n : new Constraint(head, body))
	}
	@Override
	Rule exit(Rule n, Map<IVisitable, IVisitable> m) {
		def head = m[n.head] as LogicalElement
		def body = m[n.body] as IElement
		return (head == n.head && body == n.body ? n : new Rule(head, body, false))
	}

	@Override
	AggregationElement exit(AggregationElement n, Map<IVisitable, IVisitable> m) {
		def body = m[n.body] as IElement
		return (body == n.body ? n : new AggregationElement(n.var, n.predicate, body))
	}
	@Override
	ComparisonElement exit(ComparisonElement n, Map<IVisitable, IVisitable> m) {
		return n
	}
	@Override
	GroupElement exit(GroupElement n, Map<IVisitable, IVisitable> m) {
		def e = m[n.element] as IElement
		return (e == n.element ? n : new GroupElement(e))
	}
	// Flatten LogicalElement "trees"
	@Override
	LogicalElement exit(LogicalElement n, Map<IVisitable, IVisitable> m) {
		Set<IElement> newElements = [] as Set
		n.elements.each{ e ->
			def flatE = m[e] as IElement
			if (flatE instanceof LogicalElement && (flatE as LogicalElement).type == n.type)
				newElements.addAll((flatE as LogicalElement).elements)
			else
				newElements.add(flatE)
		}
		return new LogicalElement(n.type, newElements)
	}
	@Override
	NegationElement exit(NegationElement n, Map<IVisitable, IVisitable> m) {
		def e = m[n.element] as IElement
		return (e == n.element ? n : new NegationElement(e))
	}

	
	@Override
	Directive exit(Directive n, Map<IVisitable, IVisitable> m)   { return n }
	@Override
	Functional exit(Functional n, Map<IVisitable, IVisitable> m) { return n }
	@Override
	Predicate exit(Predicate n, Map<IVisitable, IVisitable> m)   { return n }
	@Override
	Entity exit(Entity n, Map<IVisitable, IVisitable> m)         { return n }
	@Override
	Primitive exit(Primitive n, Map<IVisitable, IVisitable> m)   { return n }
	@Override
	RefMode exit(RefMode n, Map<IVisitable, IVisitable> m)       { return n }

	void enter(Program n) {}

	void enter(CmdComponent n) {}
	void enter(Component n) {}

	void enter(Constraint n) {}
	void enter(Declaration n) {}
	IVisitable exit(Declaration n, Map<IVisitable, IVisitable> m) { return null }
	void enter(RefModeDeclaration n) {}
	IVisitable exit(RefModeDeclaration n, Map<IVisitable, IVisitable> m) { return null }
	void enter(Rule n) {}

	void enter(AggregationElement n) {}
	void enter(ComparisonElement n) {}
	void enter(GroupElement n) {}
	void enter(LogicalElement n) {}
	void enter(NegationElement n) {}

	void enter(Directive n) {}
	void enter(Functional n) {}
	void enter(Predicate n) {}
	void enter(Entity n) {}
	void enter(Primitive n) {}
	void enter(RefMode n) {}
	void enter(StubAtom n) {}
	IVisitable exit(StubAtom n, Map<IVisitable, IVisitable> m) { return null }

	void enter(BinaryExpr n) {}
	IVisitable exit(BinaryExpr n, Map<IVisitable, IVisitable> m) { return null }
	void enter(ConstantExpr n) {}
	IVisitable exit(ConstantExpr n, Map<IVisitable, IVisitable> m) { return null }
	void enter(FunctionalHeadExpr n) {}
	IVisitable exit(FunctionalHeadExpr n, Map<IVisitable, IVisitable> m) { return null }
	void enter(GroupExpr n) {}
	IVisitable exit(GroupExpr n, Map<IVisitable, IVisitable> m) { return null }
	void enter(VariableExpr n) {}
	IVisitable exit(VariableExpr n, Map<IVisitable, IVisitable> m) { return null }
}
