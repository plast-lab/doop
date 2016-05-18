package deepdoop.datalog;

public class Entity implements IAtom {

	String _name;

	public Entity(String name) {
		_name = name;
	}

	@Override
	public Entity init(String id) {
		return new Entity(Names.nameId(_name, id));
	}

	@Override
	public String name() {
		return _name;
	}

	@Override
	public IAtom.Type type() {
		return IAtom.Type.PREDICATE;
	}

	@Override
	public int arity() {
		return 1;
	}

	@Override
	public String toString() {
		//return _name + "/1";
		return _name + "(x) -> .";
	}
}
