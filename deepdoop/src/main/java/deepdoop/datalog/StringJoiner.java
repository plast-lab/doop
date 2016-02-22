package deepdoop.datalog;

// Simple simulation of StringJoiner from Java 1.8
public class StringJoiner {
	String _delimeter;
	StringBuilder _builder;

	public StringJoiner(String delimeter) {
		_delimeter = delimeter;
		_builder = new StringBuilder();
	}

	public StringJoiner add(CharSequence str) {
		_builder.append(str).append(_delimeter);
		return this;
	}

	public int length() {
		int len = _builder.length();
		return (len > 0) ? len - _delimeter.length() : 0;
	}

	@Override
	public String toString() {
		int len = _builder.length();
		return (len > 0) ? _builder.delete(len - _delimeter.length(), len).toString() : "";
	}
}
