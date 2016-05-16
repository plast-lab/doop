package deepdoop.datalog;

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
		return new RefMode(id + ":" + _name, _entity.init(id), _primitive);
	}

	@Override
	public String name() {
		return _name;
	}

	@Override
	public int arity() {
		return 2;
	}

	@Override
	public String toString() {
		return _name + "/1 (" + _primitive.name() + " -> " + _entity.name() + ")";
	}
}
