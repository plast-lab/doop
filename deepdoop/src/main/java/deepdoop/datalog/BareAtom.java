package deepdoop.datalog;

import deepdoop.datalog.IAtom.Type;

public class BareAtom implements IAtom {

	String _name;
	Type   _type;
	int    _arity;

	public BareAtom(String name, Type type, int arity) {
		_name  = name;
		_type  = type;
		_arity = arity;
	}
	@Override
	public String name()  { return _name; }
	@Override
	public Type   type()  { return _type; }
	@Override
	public int    arity() { return _arity; }
	@Override
	public BareAtom init(String id) { return this; }
}
