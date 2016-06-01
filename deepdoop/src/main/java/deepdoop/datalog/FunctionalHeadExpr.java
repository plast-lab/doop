package deepdoop.datalog;

import java.util.List;
import java.util.Map;

public class FunctionalHeadExpr implements IExpr {

	Functional _functional;

	FunctionalHeadExpr(Functional functional) {
		_functional = functional;
	}
	public FunctionalHeadExpr(String name, String stage, List<IExpr> keyExprs) {
		_functional = new Functional(name, stage, keyExprs, null);
	}

	@Override
	public FunctionalHeadExpr init(Initializer ini) {
		return new FunctionalHeadExpr(_functional.init(ini));
	}

	@Override
	public Map<String, IAtom> getAtoms() {
		return _functional.getAtoms();
	}

	@Override
	public String toString() {
		return _functional.toString();
	}
}
