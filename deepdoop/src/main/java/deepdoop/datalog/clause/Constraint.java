package deepdoop.datalog.clause;

import deepdoop.actions.IVisitable;
import deepdoop.actions.IVisitor;
import deepdoop.datalog.element.IElement;
import java.util.HashMap;
import java.util.Map;

public class Constraint implements IVisitable {

	public final IElement head;
	public final IElement body;

	public Constraint(IElement head, IElement body) {
		this.head = head;
		this.body = body;
	}

	@Override
	public IVisitable accept(IVisitor v) {
		v.enter(this);
		Map<IVisitable, IVisitable> m = new HashMap<>();
		m.put(head, head.accept(v));
		m.put(body, body.accept(v));
		return v.exit(this, m);
	}

	@Override
	public String toString() {
		return head + " -> " + body + ".";
	}
}
