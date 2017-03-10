package org.clyze.deepdoop.actions;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.clyze.deepdoop.datalog.element.atom.IAtom;

public class Renamer {

	String      _removeId;
	String      _addId;
	Set<String> _externalAtoms;

	public Renamer(String removeId, String addId, Set<String> externalAtoms) {
		_removeId      = removeId;
		_addId         = addId;
		_externalAtoms = (externalAtoms != null ? externalAtoms : new HashSet<>());
	}
	public Renamer(String removeId, String addId) {
		this(removeId, addId, null);
	}
	public Renamer(String addId, Set<String> externalAtoms) {
		this(null, addId, externalAtoms);
	}

	public String getAddId() { return _addId; }

	public String init(IAtom atom) {
		String name = atom.name();
		// name should remain unaltered:
		// * we are in the global component (_addId == null)
		// * atom is external (to component)
		// * explicit stage "@past"
		if (_addId == null || _externalAtoms.contains(name) || "@past".equals(atom.stage()))
			return name;

		return _addId + ":" + name;
	}

	public String rename(IAtom atom) {
		String name = atom.name();
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
