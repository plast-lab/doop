package deepdoop.datalog.component;

import deepdoop.actions.IVisitable;
import deepdoop.actions.IVisitor;
import deepdoop.datalog.clause.*;
import java.util.HashSet;
import java.util.Set;

public class Component implements IVisitable {

	public final String           name;
	public final String           superComp;
	public final Set<Declaration> declarations;
	public final Set<Constraint>  constraints;
	public final Set<Rule>        rules;

	public Component(Component other) {
		this.name         = other.name;
		this.superComp    = other.superComp;
		this.declarations = new HashSet<>(other.declarations);
		this.constraints  = new HashSet<>(other.constraints);
		this.rules        = new HashSet<>(other.rules);
	}
	public Component(String name, String superComp, Set<Declaration> declarations, Set<Constraint> constraints, Set<Rule> rules) {
		this.name         = name;
		this.superComp    = superComp;
		this.declarations = declarations;
		this.constraints  = constraints;
		this.rules        = rules;
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

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (Declaration d : declarations) builder.append(d + "\n");
		for (Constraint c : constraints)   builder.append(c + "\n");
		for (Rule r : rules)               builder.append(r + "\n");
		return builder.toString();
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
