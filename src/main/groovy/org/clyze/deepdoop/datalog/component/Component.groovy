package org.clyze.deepdoop.datalog.component

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.clause.*
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.system.TSourceItem

@Canonical
class Component implements IVisitable, TSourceItem {

	String           name
	String           superComp
	Set<Declaration> declarations
	Set<Constraint>  constraints
	Set<Rule>        rules
	Set<String>      entities

	Component(Component other) {
		this.name         = other.name
		this.superComp    = other.superComp
		this.declarations = [] + other.declarations
		this.constraints  = [] + other.constraints
		this.rules        = [] + other.rules
		this.entities     = [] + other.entities
	}

	Component(String name, String superComp, Set<Declaration> declarations, Set<Constraint> constraints, Set<Rule> rules) {
		this.name         = name
		this.superComp    = superComp
		this.declarations = declarations
		this.constraints  = constraints
		this.rules        = rules
		this.entities     = []
	}
	Component(String name, String superComp) {
		this(name, superComp, [] as Set, [] as Set, [] as Set)
	}
	Component(String name) {
		this(name, null, [] as Set, [] as Set, [] as Set)
	}
	Component() {
		this(null, null, [] as Set, [] as Set, [] as Set)
	}

	void addDecl(Declaration d) {
		// forward patching
		if (d.atom.name in entities) {
			def p = d.atom as Predicate
			assert p.exprs.size() == 1
			def entity = new Entity(p.name, p.stage, p.exprs.first())
			d = new Declaration(entity, [] + d.types)
		}
		declarations << d
	}
	void addCons(Constraint c) { constraints << c }
	void addRule(Rule r) { rules << r }
	void addAll(Component other) {
		declarations += other.declarations
		constraints  += other.constraints
		rules        += other.rules
	}
	void markEntity(String entityName) {
		entities << entityName
		// backwards patching
		def decl = declarations.find{ it.atom.name == entityName }
		if (decl != null) {
			def p = decl.atom as Predicate
			assert p.exprs.size() == 1
			def entity = new Entity(p.name, p.stage, p.exprs.first())
			declarations.remove(decl)
			declarations << new Declaration(entity, decl.types)
		}
	}

	def <T> T accept(IVisitor<T> v) { v.visit(this) }
}
