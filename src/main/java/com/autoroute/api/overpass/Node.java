package com.autoroute.api.overpass;

import com.autoroute.logistic.LatLon;
import com.autoroute.osm.Tag;

import java.util.*;
import java.util.stream.Collectors;

public class Node {

    private final long id;
    String[] keys;
    String[] values;
    private final LatLon latLon;

    public Node(long id, String[] keys, String[] values, LatLon latLon) {
        this.id = id;
        this.keys = keys;
        this.values = values;
        this.latLon = latLon;
    }

    public Map<String, String> tags() {
        Map<String, String> m = new HashMap<>();
        assert keys.length == values.length;
        for (int i = 0; i < keys.length; i++) {
            m.put(keys[i], values[i]);
        }
        return m;
    }

    public String getName() {
        var tags = tags(); // TODO: iterate over arrays
        if (tags.containsKey("name:en")) {
            return tags.get("name:en");
        }
        if (tags.containsKey("name")) {
            return tags.get("name");
        }
        StringBuilder name = new StringBuilder();
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            name.append(entry.getKey())
                    .append(":")
                    .append(entry.getValue())
                    .append("\n");
        }
        return name.toString();
    }

    public Set<Tag> getTags() {
        return tags().entrySet()
                .stream()
                .map(e -> new Tag(e.getKey(), e.getValue()))
                .collect(Collectors.toSet());
    }

    public void addTag(Tag tag) {
        if (tag.value() == null) {
            return;
        }
        if (!(tags().get(tag.key()) == null || tags().get(tag.key()).equals(tag.value()))) {
            return;
        }
        assert tags().get(tag.key()) == null || tags().get(tag.key()).equals(tag.value());
//        assert !Arrays.asList(keys).contains(tag.key());
        int newSize = keys.length + 1;
        var newKeys = new String[newSize];
        var newValues = new String[newSize];
        System.arraycopy(keys, 0, newKeys, 0, keys.length);
        System.arraycopy(values, 0, newValues, 0, keys.length);
        newKeys[newSize - 1] = tag.key();
        newValues[newSize - 1] = tag.value();

        this.keys = newKeys;
        this.values = newValues;
    }

    public long id() {
        return id;
    }

    public LatLon latLon() {
        return latLon;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Node node = (Node) o;

        return id == node.id;
    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }

    @Override
    public String toString() {
        return "Node{" +
                "id=" + id +
                "name=" + getName() +
                ", keys=" + Arrays.toString(keys) +
                ", values=" + Arrays.toString(values) +
                ", latLon=" + latLon +
                '}';
    }
}
