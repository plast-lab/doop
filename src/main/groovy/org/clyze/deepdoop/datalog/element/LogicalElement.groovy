package org.clyze.deepdoop.datalog.element

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class LogicalElement implements IElement {

	enum LogicType { AND, OR }

	public final LogicType               type
	public final Set<? extends IElement> elements

	LogicalElement(LogicType type, List<? extends IElement> elements) {
		this(type, elements.collect() as Set)
		this._loc = SourceManager.v().getLastLoc()
	}
	LogicalElement(LogicType type, Set<? extends IElement> elements) {
		this.type     = type
		this.elements = elements
		this._loc     = SourceManager.v().getLastLoc()
	}
	LogicalElement(IElement element) {
		this.type     = LogicType.AND
		this.elements = [element]
		this._loc     = SourceManager.v().getLastLoc()
	}


	@Override
	List<VariableExpr> getVars() {
		return elements.collect{ it.getVars() }.flatten()
	}
	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this) }

	String toString() {
		return elements.collect{ it.toString() }.join(type == LogicType.AND ? ', ' : '; ')
	}

	SourceLocation _loc
	SourceLocation location() { return _loc }
}
