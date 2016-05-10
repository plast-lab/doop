package deepdoop.datalog;

import java.util.Arrays;
import java.util.List;

public class Primitive extends Predicate {

	public Primitive(String name, String capacity) {
		super(name, normalize(name, capacity), Arrays.asList(name));
	}

	@Override
	public void setTypes(List<String> types) {}

	@Override
	public String toString() {
		return _name + "[" + _capacity + "]/1";
	}

	static String normalize(String name, String capacity) {
		switch (name) {
			case "uint":
			case "int":
			case "float":
			case "decimal":
				return capacity == null ? "[64]" : capacity;
			default:
				return null;
		}
	}
}
