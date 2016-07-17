package deepdoop.datalog.element;

import deepdoop.actions.IVisitor;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

public class LogicalElement implements IElement {

	public enum LogicType { AND, OR }

	public final LogicType               type;
	public final Set<? extends IElement> elements;

	public LogicalElement(LogicType type, List<? extends IElement> elements) {
		this(type, new HashSet<>(elements));
	}
	public LogicalElement(LogicType type, Set<? extends IElement> elements) {
		this.type     = type;
		this.elements = elements;
	}
	public LogicalElement(IElement element) {
		this.type     = LogicType.AND;
		this.elements = Collections.singleton(element);
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(type == LogicType.AND ? ", " : "; ");
		for (IElement e : elements) joiner.add(e.toString());
		return joiner.toString();
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
