package com.autoroute.api.osrm.services;

import com.autoroute.osm.LatLon;
import com.autoroute.osm.WayPoint;

import java.io.Serializable;
import java.util.List;

public record OsrmResponse(String code, double distance, List<LatLon> coordinates,
                           List<WayPoint> wayPoints, Double kmPerOneNode) implements Serializable {

    public OsrmResponse(String code, double distance, List<LatLon> coordinates,
                        List<WayPoint> wayPoints) {
        this(code, distance, coordinates, wayPoints, null);
    }

    public OsrmResponse(OsrmResponse response, Double kmPerOneNode) {
        this(response.code, response.distance, response.coordinates, response.wayPoints, kmPerOneNode);
    }
}
