package deepdoop.datalog.element.atom;

import deepdoop.actions.IVisitor;
import deepdoop.datalog.expr.ConstantExpr;
import deepdoop.datalog.expr.VariableExpr;
import java.util.Arrays;
import java.util.List;

public class Directive implements IAtom {

	public final String       name;
	public final StubAtom     backtick;
	public final ConstantExpr constant;
	public final boolean      isPredicate;

	int                       _arity;

	public Directive(String name, StubAtom backtick) {
		assert backtick != null;
		this.name         = name;
		this.backtick     = backtick;
		this.constant     = null;
		this.isPredicate  = true;
		this._arity       = 1;
	}
	public Directive(String name, StubAtom backtick, ConstantExpr constant) {
		this.name         = name;
		this.backtick     = backtick;
		this.constant     = constant;
		this.isPredicate  = false;
		_arity            = (backtick == null ? 1 : 2);
	}

	@Override
	public String name() { return name; }
	@Override
	public String stage() { return null; }
	@Override
	public int arity() { return _arity; }
	@Override
	public List<VariableExpr> getVars() {
		return (_arity == 1 ? Arrays.asList((VariableExpr[])null) : Arrays.asList(null, null));
	}
	@Override
	public IAtom instantiate(String stage, List<VariableExpr> vars) {
		assert arity() == vars.size();
		return this;
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
