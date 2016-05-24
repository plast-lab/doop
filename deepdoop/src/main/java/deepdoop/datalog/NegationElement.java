package deepdoop.datalog;

public class NegationElement implements IElement {

	IElement _element;

	public NegationElement(IElement element) {
		_element = element;
	}

	@Override
	public NegationElement init(Initializer ini) {
		return new NegationElement(_element.init(ini));
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
