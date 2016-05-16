package deepdoop.datalog;

public class GroupElement implements IElement {

	IElement _element;

	public GroupElement(IElement element) {
		_element = element;
	}

	@Override
	public GroupElement init(String id) {
		return new GroupElement(_element.init(id));
	}

	@Override
	public void flatten() {
		_element.flatten();
	}

	@Override
	public String toString() {
		return "(" + _element + ")";
	}
}
