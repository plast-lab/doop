package org.clyze.deepdoop.datalog.component

import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.clause.*
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.system.*

class Component implements IVisitable, ISourceItem {

	public final String           name
	public final String           superComp
	public final Set<Declaration> declarations
	public final Set<Constraint>  constraints
	public final Set<Rule>        rules
	Set<String>                   _entities

	Component(Component other) {
		this.name         = other.name
		this.superComp    = other.superComp
		this.declarations = other.declarations.collect()
		this.constraints  = other.constraints.collect()
		this.rules        = other.rules.collect()
		this._entities    = other._entities.collect()
		this._loc         = SourceManager.v().getLastLoc()
	}

	Component(String name, String superComp, Set<Declaration> declarations, Set<Constraint> constraints, Set<Rule> rules) {
		this.name         = name
		this.superComp    = superComp
		this.declarations = declarations
		this.constraints  = constraints
		this.rules        = rules
		this._entities    = []
		this._loc         = SourceManager.v().getLastLoc()
	}
	Component(String name, String superComp) {
		this(name, superComp, new HashSet<>(), new HashSet<>(), new HashSet<>())
	}
	Component(String name) {
		this(name, null, new HashSet<>(), new HashSet<>(), new HashSet<>())
	}
	Component() {
		this(null, null, new HashSet<>(), new HashSet<>(), new HashSet<>())
	}

	void addDecl(Declaration d) {
		// forward patching
		if (_entities.contains(d.atom.name())) {
			def pred = d.atom as Predicate
			def entity = new Entity(pred.name, pred.stage, pred.exprs)
			d = new Declaration(entity, d.types.collect())
		}
		declarations.add(d)
	}
	void addCons(Constraint c) {
		constraints.add(c)
	}
	void addRule(Rule r) {
		rules.add(r)
	}
	void addAll(Component other) {
		declarations.addAll(other.declarations)
		constraints.addAll(other.constraints)
		rules.addAll(other.rules)
	}
	void markEntity(String entityName) {
		_entities.add(entityName)
		// backwards patching
		Declaration decl = null
		for (Declaration d : declarations)
			if (d.atom.name().equals(entityName)) {
				decl = d;
				break;
			}
		if (decl != null) {
			def pred = decl.atom as Predicate
			def entity = new Entity(pred.name, pred.stage, pred.exprs)
			declarations.remove(decl)
			declarations.add(new Declaration(entity as Predicate, decl.types.collect() as Set))
		}
	}


	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this) }

	String toString() {
		return "--------- $name ---------" +
			declarations.collect{ it.toString() } +
			constraints.collect{ it.toString() } +
			rules.collect{ it.toString() }.join("\n")
	}

	SourceLocation _loc
	SourceLocation location() { return _loc }
}
