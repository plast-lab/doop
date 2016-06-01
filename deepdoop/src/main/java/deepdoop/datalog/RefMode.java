package deepdoop.datalog;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class RefMode implements IAtom {

	String       _name;
	String       _stage;
	VariableExpr _entityVar;
	IExpr        _valueExpr;

	public RefMode(String name, String stage, VariableExpr entityVar, IExpr valueExpr) {
		assert !"@past".equals(stage);
		_name      = name;
		_stage     = stage;
		_entityVar = entityVar;
		_valueExpr = valueExpr;
	}

	@Override
	public String name() {
		return _name;
	}

	@Override
	public String stage() {
		return _stage;
	}

	@Override
	public int arity() {
		return 2;
	}

	@Override
	public List<IExpr> getExprs() {
		return Arrays.asList(_entityVar, _valueExpr);
	}

	@Override
	public List<VariableExpr> getExprsAsVars() {
		return Arrays.asList(_entityVar, (VariableExpr)_valueExpr);
	}

	@Override
	public Map<String, IAtom> getAtoms() {
		Map<String, IAtom> map = new HashMap<>();
		map.put(_name, this);
		map.putAll(_valueExpr.getAtoms());
		return map;
	}

	@Override
	public RefMode init(Initializer ini) {
		return new RefMode(ini.name(_name), ini.stage(_stage), _entityVar, _valueExpr.init(ini));
	}

	@Override
	public String toString() {
		return _name + (_stage == null ? "" : "@"+_stage) + "(" + _entityVar + ":" + _valueExpr + ")";
	}
}
