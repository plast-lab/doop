package deepdoop.datalog;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class LogicalElement implements IElement {

	boolean _isAND;
	List<IElement> _elements;

	public LogicalElement(boolean isAND, List<IElement> elements) {
		_isAND = isAND;
		_elements = elements;
	}

	public void normalize() {
		List<IElement> list = new ArrayList<>();
		for (IElement e : _elements) {
			e.normalize();
			if (e instanceof LogicalElement && ((LogicalElement)e)._isAND == _isAND)
				list.addAll(((LogicalElement)e)._elements);
			else
				list.add(e);
		}
		_elements = list;
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(_isAND ? ",\n" : ";\n");
		for (IElement e : _elements) joiner.add(e.toString());
		return joiner.toString();
	}
}
