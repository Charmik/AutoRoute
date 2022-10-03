package com.autoroute.osm;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Comparator;

public record LatLon(double lat, double lon) implements Serializable, Comparable<LatLon> {

    public boolean isClosePoint(LatLon other) {
        double distance = distanceKM(this, other);
        return distance < 1;
    }

    public boolean isCloseInCity(LatLon other) {
        double distance = distanceKM(this, other);
        return distance < 5;
    }

    public static double fastDistance(LatLon l1, LatLon l2) {
        final double lonDiff = l1.lon - l2.lon;
        final double latDiff = l1.lat - l2.lat;
        return lonDiff * lonDiff + latDiff * latDiff;
    }

    public static double distanceKM(LatLon l1, LatLon l2) {
        double theta = l1.lon - l2.lon;
        double dist = Math.sin(deg2rad(l1.lat)) * Math.sin(deg2rad(l2.lat)) + Math.cos(deg2rad(l1.lat)) * Math.cos(deg2rad(l2.lat)) * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist = dist * 60 * 1.1515;
        dist = dist * 1.609344;
        return dist;
    }

    private static double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }

    private static double rad2deg(double rad) {
        return (rad * 180.0 / Math.PI);
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

    @Override
    public int compareTo(@NotNull LatLon o) {
        return Comparator.comparingDouble(LatLon::lat)
            .thenComparingDouble(LatLon::lon)
            .compare(this, o);
    }
}
