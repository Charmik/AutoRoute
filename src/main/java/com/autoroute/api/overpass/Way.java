package com.autoroute.api.overpass;

import java.util.HashMap;
import java.util.Map;

public record Way(long id, long[] nodesIds, String[] keys, String[] values) {

    public Map<String, String> tags() {
        Map<String, String> m = new HashMap<>();
        assert keys.length == values.length;
        for (int i = 0; i < keys.length; i++) {
            m.put(keys[i], values[i]);
        }
        return m;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Way way = (Way) o;

        return id == way.id;
    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }
}
