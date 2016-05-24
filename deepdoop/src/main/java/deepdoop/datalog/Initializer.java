package deepdoop.datalog;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Initializer {

	String      _id;
	Set<String> _globalAtoms;

	public Initializer(String id, Set<String> globalAtoms) {
		_id          = id;
		_globalAtoms = globalAtoms;
	}

	public String id() {
		return _id;
	}

	public String name(String name, String stage) {
		if (exclude(name) || _id == null) {
			assert !"@past".equals(stage);
			return name;
		}
		else
			return _id + ":" + name + ("@past".equals(stage) ? ":past" : "");
	}
	public String name(String name) {
		return name(name, null);
	}

	public String stage(String stage) {
		return ("@past".equals(stage) ? null : stage);
	}

	public boolean exclude(String name) {
		return _globalAtoms.contains(name);
	}
}
