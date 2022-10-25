package com.autoroute.gpx;

import com.autoroute.logistic.rodes.Vertex;
import com.autoroute.osm.LatLon;
import com.autoroute.osm.WayPoint;
import com.autoroute.sight.Sight;
import io.jenetics.jpx.GPX;

import java.util.List;
import java.util.Set;

public class GpxGenerator {

    public static GPX generateRouteWithWaypoints(List<LatLon> coordinates, List<WayPoint> wayPoints) {
        var builder = GPX.builder()
            .addTrack(track -> track
                .addSegment(segment -> {
                    for (LatLon coordinate : coordinates) {
                        segment.addPoint(p -> p.lat(coordinate.lat()).lon(coordinate.lon()));
                    }
                }));
        for (WayPoint wayPoint : wayPoints) {
            builder.addWayPoint(b -> b.lat(wayPoint.latLon().lat())
                .lon(wayPoint.latLon().lon())
                .name(wayPoint.name()));
        }
        return builder.build();
    }

    public static GPX generateRoute(List<Vertex> vertices, Set<Sight> sights) {
        var builder = GPX.builder()
            .addTrack(track -> track
                .addSegment(segment -> {
                    for (Vertex v : vertices) {
                        var coordinate = v.getLatLon();
                        segment.addPoint(p -> p.lat(coordinate.lat()).lon(coordinate.lon()));
                    }
                }));
        for (Sight wayPoint : sights) {
            builder.addWayPoint(b -> b.lat(wayPoint.latLon().lat())
                .lon(wayPoint.latLon().lon())
                .name(wayPoint.name()));
        }
        return builder.build();
    }

    public static GPX generateWithNeighbors(List<Vertex> vertices) {
        var builder = GPX.builder();
        for (Vertex v : vertices) {
            for (Vertex n : v.getNeighbors()) {
                builder.addTrack(track -> track
                    .addSegment(segment -> {
                        segment.addPoint(p -> p
                            .lat(v.getLatLon().lat())
                            .lon(v.getLatLon().lon()));
                        segment.addPoint(p -> p
                            .lat(n.getLatLon().lat())
                            .lon(n.getLatLon().lon()));
                    }));
            }
            if (v.getNeighbors().isEmpty()) {
                builder.addWayPoint(b -> b
                    .lat(v.getLatLon().lat())
                    .lon(v.getLatLon().lon())
                    .name(String.valueOf(v.getIdentificator())));
            }
        }
        return builder.build();
    }
}
