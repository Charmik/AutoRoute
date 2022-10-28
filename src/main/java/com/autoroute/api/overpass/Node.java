package com.autoroute.api.overpass;

import com.autoroute.osm.LatLon;
import com.autoroute.osm.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record Node(long id, String[] keys, String[] values, LatLon latLon) {

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
            ", keys=" + keys +
            ", values=" + values +
            ", latLon=" + latLon +
            '}';
    }
}
