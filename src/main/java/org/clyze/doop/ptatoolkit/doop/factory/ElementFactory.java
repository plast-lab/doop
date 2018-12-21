package org.clyze.doop.ptatoolkit.doop.factory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class ElementFactory<T> {

	private Map<String, T> name2elem = new HashMap<>();
	protected int count = 0;
	
	public T get(String name) {
		T item = name2elem.get(name);
		if (item == null) {
			item = createElement(name);
			name2elem.put(name, item);
		}
		return item;
	}

	public boolean has(String name) {
		return name2elem.containsKey(name);
	}

	protected abstract T createElement(String name);

	public Collection<T> getAllElements() {
		return name2elem.values();
	}
}
