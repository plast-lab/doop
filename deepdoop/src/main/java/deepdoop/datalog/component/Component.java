package deepdoop.datalog.component;

import deepdoop.actions.IVisitable;
import deepdoop.actions.IVisitor;
import deepdoop.datalog.clause.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
		this.declarations = other.declarations;
		this.constraints  = other.constraints;
		this.rules        = other.rules;
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
	public IVisitable accept(IVisitor v) {
		v.enter(this);
		Map<IVisitable, IVisitable> m = new HashMap<>();
		for (Declaration d : declarations) m.put(d, d.accept(v));
		for (Constraint c : constraints)   m.put(c, c.accept(v));
		for (Rule r : rules)               m.put(r, r.accept(v));
		return v.exit(this, m);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (Declaration d : declarations) builder.append(d + "\n");
		for (Constraint c : constraints)   builder.append(c + "\n");
		for (Rule r : rules)               builder.append(r + "\n");
		return builder.toString();
	}

	//void TEST() {
	//	System.out.println(name);
	//	System.out.println("Atoms: " + getAtoms().keySet().toString());
	//	System.out.println("DeclAtoms: " + getDeclaringAtoms().keySet().toString());
	//	System.out.println("InputAtoms: " + getInputAtoms().keySet().toString());
	//	System.out.println("\n");
	//}
}
