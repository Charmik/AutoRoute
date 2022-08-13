package com.autoroute.api.overpass;

import com.autoroute.osm.LatLon;

import java.util.Map;

public record OverpassResponse(long id, Map<String, String> tags, LatLon latLon) {

    public String getName() {
        return tags.get("name");
    }

}
