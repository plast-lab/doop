package org.clyze.deepdoop.datalog.component;

import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;
import org.clyze.deepdoop.actions.*;
import org.clyze.deepdoop.datalog.clause.*;
import org.clyze.deepdoop.datalog.element.atom.*;
import org.clyze.deepdoop.system.*;

public class Component implements IVisitable, ISourceItem {

	public final String           name;
	public final String           superComp;
	public final Set<Declaration> declarations;
	public final Set<Constraint>  constraints;
	public final Set<Rule>        rules;
	Set<String>                   _entities;

	public Component(Component other) {
		this.name         = other.name;
		this.superComp    = other.superComp;
		this.declarations = new HashSet<>(other.declarations);
		this.constraints  = new HashSet<>(other.constraints);
		this.rules        = new HashSet<>(other.rules);
		this._entities    = new HashSet<>(other._entities);
		this._loc         = SourceManager.v().getLastLoc();
	}

	public Component(String name, String superComp, Set<Declaration> declarations, Set<Constraint> constraints, Set<Rule> rules) {
		this.name         = name;
		this.superComp    = superComp;
		this.declarations = declarations;
		this.constraints  = constraints;
		this.rules        = rules;
		this._entities    = new HashSet<>();
		this._loc         = SourceManager.v().getLastLoc();
	}
	public Component(String name, String superComp) {
		this(name, superComp, new HashSet<>(), new HashSet<>(), new HashSet<>());
	}
	public Component(String name) {
		this(name, null, new HashSet<>(), new HashSet<>(), new HashSet<>());
	}
	public Component() {
		this(null, null, new HashSet<>(), new HashSet<>(), new HashSet<>());
	}

	public void addDecl(Declaration d) {
		// forward patching
		if (_entities.contains(d.atom.name())) {
			Predicate pred = (Predicate) d.atom;
			Entity entity = new Entity(pred.name, pred.stage, pred.exprs);
			d = new Declaration(entity, new HashSet<>(d.types));
		}
		declarations.add(d);
	}
	public void addCons(Constraint c) {
		constraints.add(c);
	}
	public void addRule(Rule r) {
		rules.add(r);
	}
	public void addAll(Component other) {
		declarations.addAll(other.declarations);
		constraints.addAll(other.constraints);
		rules.addAll(other.rules);
	}
	public void markEntity(String entityName) {
		_entities.add(entityName);
		// backwards patching
		Declaration decl = null;
		for (Declaration d : declarations)
			if (d.atom.name().equals(entityName)) {
				decl = d;
				break;
			}
		if (decl != null) {
			Predicate pred = (Predicate) decl.atom;
			Entity entity = new Entity(pred.name, pred.stage, pred.exprs);
			declarations.remove(decl);
			declarations.add(new Declaration(entity, new HashSet<>(decl.types)));
		}
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}

	SourceLocation _loc;
	@Override
	public SourceLocation location() { return _loc; }


	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner("\n");
		joiner.add("---------" + name + "---------");
		declarations.forEach(d -> joiner.add(d.toString()));
		constraints.forEach(c -> joiner.add(c.toString()));
		rules.forEach(r -> joiner.add(r.toString()));
		return joiner.toString();
	}
}
