package com.autoroute.api.overpass;

import com.autoroute.osm.LatLon;

import java.util.Map;

public record OverpassResponse(long id, Map<String, String> tags, LatLon latLon) {

    public String getName() {
        if (tags.containsKey("name")) {
            return tags.get("name");
        }
        String name = "";
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            name += "<" + entry.getKey() + ":" + entry.getValue() + ">";
        }
        return name;
    }

}
