package org.clyze.doop.ptatoolkit.doop.factory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class ElementFactory<T> {

	private Map<String, T> name2elem = new HashMap<>();
	protected int count = 0;
	
	/**
	 * Retrieves an element by its name. If the element does not exist, it creates a new one using the createElement method and stores it in the name2elem map before returning it. This method ensures that each element is created only once and can be efficiently retrieved by its name.
	 * @param name the name of the element to retrieve
	 * @return the element associated with the given name, creating it if it does not exist
	 */
	public T get(String name) {
		T item = name2elem.get(name);
		if (item == null) {
			item = createElement(name);
			name2elem.put(name, item);
		}
		return item;
	}

	/* Checks if an element with the given name exists in the factory. */
	public boolean has(String name) {
		return name2elem.containsKey(name);
	}

	/**
	 * Creates a new element based on the given name. This method is abstract and must be implemented by subclasses to define how elements are created based on their names. The implementation of this method will depend on the specific type of elements being created by the factory.
	 * @param name the name of the element to create
	 * @return a new element instance based on the given name
	 */
	protected abstract T createElement(String name);

	/**
	 * Retrieves a collection of all elements created by this factory. The elements are stored in the name2elem map, and this method returns the values of that map as a collection. This allows clients to access all the elements that have been created by the factory.
	 * @return a collection of all elements created by this factory
	*/
	public Collection<T> getAllElements() {
		return name2elem.values();
	}
}
