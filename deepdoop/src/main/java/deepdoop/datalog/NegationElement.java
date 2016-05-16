package deepdoop.datalog;

public class NegationElement implements IElement {

	IElement _element;

	public NegationElement(IElement element) {
		_element = element;
	}

	@Override
	public NegationElement init(String id) {
		return new NegationElement(_element.init(id));
	}

	@Override
	public void flatten() {
		_element.flatten();
	}

	@Override
	public String toString() {
		return "!" + _element;
	}
}
