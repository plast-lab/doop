package deepdoop.datalog.clause;

import deepdoop.actions.IVisitor;
import deepdoop.datalog.element.atom.*;
import java.util.Arrays;
import java.util.HashSet;

public class RefModeDeclaration extends Declaration {

	public RefModeDeclaration(RefMode refmode, Predicate entity, Primitive primitive) {
		super(refmode, new HashSet<>(Arrays.asList(entity, primitive)));
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
