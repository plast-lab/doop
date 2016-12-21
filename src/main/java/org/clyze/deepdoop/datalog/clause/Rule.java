package org.clyze.deepdoop.datalog.clause;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.clyze.deepdoop.actions.*;
import org.clyze.deepdoop.datalog.element.*;
import org.clyze.deepdoop.datalog.element.atom.*;
import org.clyze.deepdoop.datalog.expr.*;
import org.clyze.deepdoop.system.*;

public class Rule implements IVisitable, ISourceItem {

	public final LogicalElement head;
	public final IElement       body;
	public final boolean        isDirective;

	public Rule(LogicalElement head, IElement body) {
		this.head = head;
		this.body = body;
		this.isDirective = (
				body == null &&
				head.elements.size() == 1 &&
				head.elements.iterator().next() instanceof Directive);
		this._loc = SourceManager.v().getLastLoc();

		if (body != null) {
			List<VariableExpr> varsInHead = head.getVars();
			List<VariableExpr> varsInBody = body.getVars();
			varsInBody.stream()
			          .filter(v -> !v.isDontCare)
			          .filter(v -> !varsInHead.contains(v))
			          .filter(v -> Collections.frequency(varsInBody, v) == 1)
			          .forEach(v -> ErrorManager.warn(ErrorId.UNUSED_VAR, v.name));
		}
	}

	public Directive getDirective() {
		return (isDirective ? (Directive) head.elements.iterator().next() : null);
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}

	SourceLocation _loc;
	@Override
	public SourceLocation location() { return _loc; }
}
