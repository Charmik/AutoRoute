package com.autoroute.api.trip.services;

import com.autoroute.osm.LatLon;
import com.autoroute.osm.WayPoint;

import java.io.Serializable;
import java.util.List;

public record OsrmResponse(double distance, List<LatLon> coordinates,
                           List<WayPoint> wayPoints, Double kmPerOneNode) implements Serializable {

    public OsrmResponse(double distance, List<LatLon> coordinates,
                        List<WayPoint> wayPoints) {
        this(distance, coordinates, wayPoints, null);
    }

    public OsrmResponse(OsrmResponse response, Double kmPerOneNode) {
        this(response.distance, response.coordinates, response.wayPoints, kmPerOneNode);
    }

    public OsrmResponse withKmPerOneNode(Double kmPerOneNode) {
        return new OsrmResponse(distance, coordinates, wayPoints, kmPerOneNode);
    }
}
