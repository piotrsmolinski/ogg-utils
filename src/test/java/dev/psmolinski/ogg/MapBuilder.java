package dev.psmolinski.ogg;

import java.util.HashMap;
import java.util.Map;

public class MapBuilder<K,V> {

    private Map<K,V> map = new HashMap<>();

    public static <K,V> MapBuilder<K,V> empty() {
        return new MapBuilder<>();
    }

    public MapBuilder<K,V> put(K key, V value) {
        this.map.put(key, value);
        return this;
    }

    public Map<K,V> build() {
        return new HashMap<>(map);
    }
}
