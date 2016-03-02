package deepdoop.datalog;

public class NegationElement implements IElement {

	IElement _element;

	public NegationElement(IElement element) {
		_element = element;
	}

	@Override
	public void normalize() {
		_element.normalize();
	}

	@Override
	public String toString() {
		return "!" + _element;
	}
}
