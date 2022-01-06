package util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * @author zavakid 2013-4-20 下午10:02:47
 * @since 1.0
 */
public class Counter<T> {

    private Map<T, Long> store = new HashMap<>();

    public synchronized void add(T t) {
        Long value = store.get(t);
        if (value == null) {
            store.put(t, 1L);
            return;
        }
        store.put(t, ++value);
    }

    public Long get(T t) {
        return store.get(t);
    }

    public Set<T> items() {
        return Collections.unmodifiableSet(store.keySet());
    }

    public Collection<Long> values() {
        return Collections.unmodifiableCollection(store.values());
    }

    public T getMostItem() {

        Long maxKey = -1L;
        T mostItem = null;
        for (Entry<T, Long> entry : this.store.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            if (entry.getValue() > maxKey) {
                maxKey = entry.getValue();
                mostItem = entry.getKey();
            }
        }
        return mostItem;
    }
}
