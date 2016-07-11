package deepdoop;

import java.util.HashMap;
import java.util.Map;

public class PropertyMap<K, P> {

	Map<K, P> _properties;

	public PropertyMap() {
		_properties = new HashMap<>();
	}

	public void put(K key, P property) {
		if (_properties.get(key) != null)
			throw new RuntimeException("MUST RETHINK THIS");
		_properties.put(key, property);
	}

	public P peek(K key) {
		return _properties.get(key);
	}

	// TODO can this be generic and return the same type? <T extends K>
	public P pop(K key) {
		return _properties.remove(key);
	}

	public void move(K key1, K key2) {
		_properties.put(key2, _properties.remove(key1));
	}
}
