package com.autoroute.api.osrm.services;

import com.autoroute.osm.LatLon;
import com.autoroute.osm.WayPoint;

import java.util.List;

public record OsrmResponse(String code, double distance, List<LatLon> coordinates, List<WayPoint> wayPoints) {

}
