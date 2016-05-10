package deepdoop.datalog;

import java.util.Arrays;
import java.util.List;

public class RefMode extends Predicate {
	Entity _entity;
	Primitive _primitive;

	public RefMode(String name, Entity entity, Primitive primitive) {
		super(name, null, Arrays.asList(entity._name, primitive._name));
		_entity = entity;
		_primitive = primitive;
	}

	@Override
	public void setTypes(List<String> types) {}

	@Override
	public String toString() {
		return _name + "/1 (" + _types.get(1) + " -> " + _entity._name + ")";
	}
}
