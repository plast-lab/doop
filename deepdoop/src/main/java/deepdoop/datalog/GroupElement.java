package deepdoop.datalog;

public class GroupElement implements IElement {

	IElement _element;

	public GroupElement(IElement element) {
		_element = element;
	}

	@Override
	public void normalize() {
		_element.normalize();
	}

	@Override
	public String toString() {
		return "(" + _element + ")";
	}
}
