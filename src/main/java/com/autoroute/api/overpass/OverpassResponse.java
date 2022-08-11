package com.autoroute.api.overpass;

import com.autoroute.osm.LatLon;

public record OverpassResponse(long id, String name, LatLon latLon) {
}
