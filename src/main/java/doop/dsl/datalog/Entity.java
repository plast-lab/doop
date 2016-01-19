package doop.dsl.datalog;

import java.util.Arrays;

public class Entity extends Predicate {

	public Entity(String name) {
		super(name, Arrays.asList(name));
	}

	@Override
	public String toString() {
		return _name + "/1";
	}
}
