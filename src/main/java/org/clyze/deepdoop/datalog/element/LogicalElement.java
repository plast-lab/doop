package org.clyze.deepdoop.datalog.element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import org.clyze.deepdoop.actions.IVisitor;
import org.clyze.deepdoop.datalog.expr.*;
import org.clyze.deepdoop.system.*;

public class LogicalElement implements IElement {

	public enum LogicType { AND, OR }

	public final LogicType               type;
	public final Set<? extends IElement> elements;

	public LogicalElement(LogicType type, List<? extends IElement> elements) {
		this(type, new HashSet<>(elements));
		this._loc = SourceManager.v().getLastLoc();
	}
	public LogicalElement(LogicType type, Set<? extends IElement> elements) {
		this.type     = type;
		this.elements = elements;
		this._loc     = SourceManager.v().getLastLoc();
	}
	public LogicalElement(IElement element) {
		this.type     = LogicType.AND;
		this.elements = Collections.singleton(element);
		this._loc     = SourceManager.v().getLastLoc();
	}


	@Override
	public List<VariableExpr> getVars() {
		List<VariableExpr> list = new ArrayList<>();
		elements.forEach(e -> list.addAll(e.getVars()));
		return list;
	}
	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}

	SourceLocation _loc;
	@Override
	public SourceLocation location() { return _loc; }


	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(type == LogicType.AND ? ", " : "; ");
		elements.forEach(e -> joiner.add(e.toString()));
		return joiner.toString();
	}
}
