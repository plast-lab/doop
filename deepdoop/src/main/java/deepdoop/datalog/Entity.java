package deepdoop.datalog;

public class Entity implements IAtom {

	String _name;

	public Entity(String name) {
		_name = name;
	}

	@Override
	public Entity init(String id) {
		return new Entity(id + ":" + _name);
	}

	@Override
	public String name() {
		return _name;
	}

	@Override
	public int arity() {
		return 1;
	}

	@Override
	public String toString() {
		return _name + "/1";
	}
}
