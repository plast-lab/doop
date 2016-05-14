package deepdoop.datalog;

import java.util.List;

public class RefMode extends Predicate {
	Entity    _entity;
	Primitive _primitive;

	public RefMode(String name, Entity entity, Primitive primitive) {
		super(name);
		_entity    = entity;
		_primitive = primitive;
	}

	@Override
	public Predicate init(String id) {
		return new RefMode(id + ":" + _name, (Entity)_entity.init(id), (Primitive)_primitive);
	}

	@Override
	public void setTypes(List<Predicate> types) {}

	@Override
	public String toString() {
		return _name + "/1 (" + _primitive.getName() + " -> " + _entity.getName() + ")";
	}
}
