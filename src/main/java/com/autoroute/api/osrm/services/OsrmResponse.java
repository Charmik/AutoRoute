package com.autoroute.api.osrm.services;

import com.autoroute.osm.LatLon;

import java.util.List;

public record OsrmResponse(String code, double distance, List<LatLon> coordinates) {

}
