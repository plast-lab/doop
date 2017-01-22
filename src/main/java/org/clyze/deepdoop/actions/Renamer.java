package org.clyze.deepdoop.actions;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Renamer {

	String      _removeId;
	String      _addId;
	Set<String> _ignoreAtoms;

	public Renamer(String removeId, String addId, Set<String> ignoreAtoms) {
		reset(removeId, addId, ignoreAtoms);
	}
	public void reset(String removeId, String addId, Set<String> ignoreAtoms) {
		_removeId    = removeId;
		_addId       = addId;
		_ignoreAtoms = ignoreAtoms;
	}

	public String addId() { return _addId; }

	public String rename(String name, String stage) {
		return handleStage(handleName(name), stage);
	}
	public String rename(String name) {
		return rename(name, null);
	}
	public String restage(String stage) {
		return "@past".equals(stage) ? null : stage;
	}

	// * prefix with component's (initialization) name
	// * remove component's prefix (going back to global space)
	String handleName(String name) {
		// atom is external => should remain unaltered
		if (_ignoreAtoms != null && _ignoreAtoms.contains(name)) {
			return name;
		}

		// != null => coming from another component
		// == null => coming from global space
		if (_removeId != null) {
			name = name.replaceFirst(_removeId + ":", "");
		}

		// != null => going to another component
		// == null => going to global space
		if (_addId != null) {
			name = _addId + ":" + name;
		}

		return name;
	}

	// * replace "@past" with ":past" (connecting different components)
	String handleStage(String name, String stage) {
		return "@past".equals(stage) ? name + ":past" : name;
	}


	// P0 -> P0
	// S1:P1 -> S1:P1
	// S3:P2:past -> S1:P2, S2:P2 when S1 and S2 propagate to S3
	public static Set<String> revert(String name, Set<String> initIds, Map<String, Set<String>> reversePropagations) {
		if (!name.endsWith(":past")) return Collections.singleton(name);

		int i = name.indexOf(':');
		if (i == -1) return Collections.singleton(name);

		String id = name.substring(0, i);
		String subName = name.substring(i+1, name.length());

		if (reversePropagations.get(id) != null) {
			subName = subName.substring(0, subName.lastIndexOf(":past"));
			Set<String> fromSet = reversePropagations.get(id);
			Set<String> result = new HashSet<>();
			for (String fromId : fromSet) {
				result.add(fromId + ":" + subName);
			}
			return result;
		}
		else
			return Collections.singleton(subName);
	}
}
