package deepdoop.datalog;

import java.util.Collections;
import java.util.Map;

interface IAtomContainer {
	@SuppressWarnings("unchecked")
	default Map<String, IAtom> getAtoms() { return Collections.EMPTY_MAP; }
	@SuppressWarnings("unchecked")
	default Map<String, IAtom> getDeclaringAtoms() { return Collections.EMPTY_MAP; }
	@SuppressWarnings("unchecked")
	default Map<String, IAtom> getInputAtoms() { return Collections.EMPTY_MAP; }
}
