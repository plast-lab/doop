package deepdoop.datalog;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
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


	// S1:P0 -> P0
	// S3:P1:past -> S1:P1, S2:P1 when S1 and S2 propagate P1 to S3
	// A list is needed for cases like the second one
	public static Set<String> revert(String name, Set<String> initIds, Map<String, Set<String>> reversePropsMap) {
		int i = name.indexOf(':');
		if (i == -1) return Collections.singleton(name);

		String id = name.substring(0, i);
		String newName = name.substring(i+1, name.length());

		if (newName.endsWith(":past") && reversePropsMap.get(id) != null) {
			newName = newName.substring(0, newName.lastIndexOf(":past"));
			Set<String> fromSet = reversePropsMap.get(id);
			Set<String> result = new HashSet<>();
			for (String fromId : fromSet) {
				result.add(fromId + ":" + newName);
			}
			return result;
		}
		else
			return Collections.singleton(newName);
	}
}
