package deepdoop.datalog;

import java.util.List;

public class RefMode implements IAtom {

	String    _name;
	Entity    _entity;
	Primitive _primitive;

	public RefMode(String name, Entity entity, Primitive primitive) {
		_name      = name;
		_entity    = entity;
		_primitive = primitive;
	}

	@Override
	public RefMode init(String id) {
		return new RefMode(Names.nameId(_name, id), _entity.init(id), _primitive);
	}

	@Override
	public String name() {
		return _name;
	}

	@Override
	public IAtom.Type type() {
		return IAtom.Type.REFMODE;
	}

	@Override
	public int arity() {
		return 2;
	}

	@Override
	public String toString() {
		//return _name + "/1 (" + _primitive.name() + " -> " + _entity.name() + ")";
		List<IExpr> vars = Names.newVars(2);
		return
			_entity.name() + "(" + vars.get(0) + "), " +
			_name + "(" + vars.get(0) + ":" + vars.get(1) + ") -> " +
			_primitive.name() + "(" + vars.get(1) + ").";
	}
}
