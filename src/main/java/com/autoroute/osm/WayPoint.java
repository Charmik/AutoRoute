package com.autoroute.osm;

import java.io.Serializable;

public record WayPoint(long id, LatLon latLon, String name) implements Serializable {
}
