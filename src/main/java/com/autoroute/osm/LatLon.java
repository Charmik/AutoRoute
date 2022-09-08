package com.autoroute.osm;

import java.io.Serializable;

public record LatLon(double lat, double lon) implements Serializable {

    //    private static final double EPS = ((double) Constants.KM_IN_ONE_DEGREE) / 100 * 3;
    private static final double EPS = 0.001;
    private static final double EPS_BIGGER = 0.05;

    public boolean IsClosePoint(LatLon other) {
        return Math.abs(lat - other.lat) < EPS && Math.abs(lon - other.lon) < EPS;
    }

    public boolean IsCloseInCity(LatLon other) {
        return Math.abs(lat - other.lat) < EPS_BIGGER && Math.abs(lon - other.lon) < EPS_BIGGER;
    }

    public static LatLon castFromWayPoint(io.jenetics.jpx.WayPoint point) {
        var lat = point.getLatitude();
        var lon = point.getLongitude();
        return new LatLon(lat.doubleValue(), lon.doubleValue());
    }

    @Override
    public String toString() {
        return lat + "_" + lon;
    }
}
