package com.autoroute.api.overpass;

import com.autoroute.osm.LatLon;

import java.util.Map;

public record Node(long id, Map<String, String> tags, LatLon latLon) {

    public String getName() {
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
}
