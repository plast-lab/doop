package deepdoop.datalog.element;

import deepdoop.actions.IVisitable;
import deepdoop.actions.IVisitor;
import java.util.Collections;
import java.util.Map;

public class NegationElement implements IElement {

	public final IElement element;

	public NegationElement(IElement element) {
		this.element = element;
	}

	@Override
	public IVisitable accept(IVisitor v) {
		v.enter(this);
		Map<IVisitable, IVisitable> m =
			Collections.singletonMap(element, element.accept(v));
		return v.exit(this, m);
	}

	@Override
	public String toString() {
		return "!" + element;
	}
}
