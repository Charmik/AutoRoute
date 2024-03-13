package com.autoroute.logistic;

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

    public boolean isCloseInCity(LatLon other, double kmDistance) {
        double distance = distanceKM(this, other);
        return distance < kmDistance;
    }

    public static double fastDistance(LatLon l1, LatLon l2) {
        final double lonDiff = l1.lon - l2.lon;
        final double latDiff = l1.lat - l2.lat;
        return lonDiff * lonDiff + latDiff * latDiff;
    }

    public static double distanceKM(LatLon l1, LatLon l2) {
        var lat1 = l1.lat;
        var lat2 = l2.lat;

        int R = 6371;
        double x =
            (Math.toRadians(l2.lon) - Math.toRadians(l1.lon)) * Math.cos(0.5 * (Math.toRadians(lat2) + Math.toRadians(lat1)));
        double y = Math.toRadians(lat2) - Math.toRadians(lat1);
        return R * Math.sqrt(x * x + y * y);
    }

    public static double angle(LatLon l1, LatLon l2, LatLon l3) {
        double angle1 = Math.atan2(l2.lon - l1.lon, l2.lat - l1.lat);
        double angle2 = Math.atan2(l3.lon - l2.lon, l3.lat - l2.lat);
        double result = angle1 - angle2;
        return result;
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
