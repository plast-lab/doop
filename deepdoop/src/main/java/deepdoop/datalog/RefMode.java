package deepdoop.datalog;

import java.util.Arrays;
import java.util.List;

public class RefMode extends Predicate {
	Entity _entity;

	public RefMode(String name, String type, Entity entity) {
		super(name, Arrays.asList(entity._name, type));
		_entity = entity;
	}

	@Override
	public void setTypes(List<String> types) {}

	@Override
	public String toString() {
		return _name + "/1 (" + _types.get(1) + " -> " + _entity._name + ")";
	}
}
