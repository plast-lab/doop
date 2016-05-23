package deepdoop.datalog;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class RefMode implements IAtom {

	String       _name;
	VariableExpr _entityVar;
	IExpr        _valueExpr;
	// Declaration
	Predicate    _entity;
	Primitive    _primitive;
	// Instance
	String      _stage;
	boolean     _inDecl;


	public RefMode(String name, VariableExpr entityVar, IExpr valueExpr, Predicate entity, Primitive primitive) {
		_name      = name;
		_entityVar = entityVar;
		_valueExpr = valueExpr;
		_entity    = entity;
		_primitive = primitive;
		_inDecl    = true;
	}
	public RefMode(String name, String stage, VariableExpr entityVar, IExpr valueExpr) {
		_name      = name;
		_stage     = stage;
		_entityVar = entityVar;
		_valueExpr = valueExpr;
		_inDecl    = false;
	}


	@Override
	public Map<String, IAtom> getAtoms() {
		return Collections.singletonMap(_name, this);
	}

	@Override
	public String name() {
		return _name;
	}

	@Override
	public IAtom.Type type() {
		return IAtom.Type.REFMODE;
	}

	@Override
	public int arity() {
		return 2;
	}

	@Override
	public RefMode init(String id) {
		if (_inDecl)
			return new RefMode(Names.nameId(_name, id), _entityVar, _valueExpr, _entity.init(id), _primitive);
		else
			return new RefMode(Names.nameId(_name, id), _stage, _entityVar, _valueExpr);
	}

	@Override
	public String toString() {
		if (_inDecl) {
			return
				_entity.name() + "(" + _entityVar + "), " +
				_name + "(" + _entityVar + ":" + _valueExpr + ") -> " +
				_primitive.name() + "(" + _valueExpr + ").";
		}
		else {
			return _name + "(" + _entityVar + ":" + _valueExpr + ")";
		}
	}
}
