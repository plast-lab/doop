package deepdoop.datalog;

import java.util.Arrays;
import java.util.List;

public class Entity extends Predicate {

	public Entity(String name) {
		super(name, null, Arrays.asList(name));
	}

	@Override
	public void setTypes(List<String> types) {}

	@Override
	public String toString() {
		return _name + "/1";
	}
}
