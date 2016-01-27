package deepdoop.datalog;

import java.util.List;
import java.util.StringJoiner;

public class LogicalElement implements IElement {

	boolean _isAND;
	List<IElement> _elements;

	public LogicalElement(boolean isAND, List<IElement> elements) {
		_isAND = isAND;
		_elements = elements;
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(_isAND ? ", " : "; ");
		for (IElement e : _elements) joiner.add(e.toString());
		return joiner.toString();
	}
}
