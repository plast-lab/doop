package org.clyze.deepdoop.datalog.clause;

import java.util.Arrays;
import java.util.HashSet;
import org.clyze.deepdoop.actions.*;
import org.clyze.deepdoop.datalog.element.atom.*;

public class RefModeDeclaration extends Declaration {

	public RefModeDeclaration(RefMode refmode, Predicate entity, Primitive primitive) {
		super(refmode, new HashSet<>(Arrays.asList(entity, primitive)));
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
