package com.autoroute.osm;

import com.autoroute.logistic.LatLon;

import java.io.Serializable;

public record WayPoint(long id, LatLon latLon, String name) implements Serializable {
}
