package deepdoop.datalog;

import java.util.Map;

interface IElement extends IInitializable<IElement> {
	default void flatten() {}
	// Should only be overridden by elements that can appear in the head of a rule
	default Map<String, IAtom> getAtoms() { return null; }
}
