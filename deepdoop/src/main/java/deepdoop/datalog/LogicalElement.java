package deepdoop.datalog;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class LogicalElement implements IElement {

	enum LogicType { AND, OR }

	LogicType                _logicType;
	List<? extends IElement> _elements;

	public LogicalElement(LogicType logicType, List<? extends IElement> elements) {
		_logicType = logicType;
		_elements  = elements;
	}
	public LogicalElement(IElement element) {
		_logicType = LogicType.AND;
		_elements  = Arrays.asList(element);
	}

	public List<? extends IElement> getElements() {
		return _elements;
	}

	@Override
	public LogicalElement init(String id) {
		List<IElement> newElements = new ArrayList<>();
		for (IElement e : _elements) newElements.add(e.init(id));
		return new LogicalElement(_logicType, newElements);
	}

	@Override
	public void flatten() {
		List<IElement> list = new ArrayList<>();
		for (IElement e : _elements) {
			e.flatten();
			if (e instanceof LogicalElement && ((LogicalElement)e)._logicType == _logicType)
				list.addAll(((LogicalElement)e)._elements);
			else
				list.add(e);
		}
		_elements = list;
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(_logicType == LogicType.AND ? ", " : "; ");
		for (IElement e : _elements) joiner.add(e.toString());
		return joiner.toString();
	}
}
