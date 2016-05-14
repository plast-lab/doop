package deepdoop.datalog;

import java.util.List;

public class Entity extends Predicate {

	public Entity(String name) {
		super(name);
	}

	@Override
	public Predicate init(String id) {
		return new Entity(id + ":" + _name);
	}

	@Override
	public void setTypes(List<Predicate> types) {}

	@Override
	public String toString() {
		return _name + "/1";
	}
}
