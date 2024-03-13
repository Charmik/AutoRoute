package com.autoroute.gpx;

import com.autoroute.logistic.rodes.Vertex;
import com.autoroute.logistic.LatLon;
import com.autoroute.osm.WayPoint;
import com.autoroute.sight.Sight;
import io.jenetics.jpx.GPX;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class GpxGenerator {

    public static GPX generateGPXWithWaypoints(List<LatLon> coordinates, List<WayPoint> wayPoints) {
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

    public static GPX generateGPXWithSights(List<Vertex> vertices, Set<Sight> sights) {
        List<LatLon> latLonList = vertices.stream()
            .map(v -> new LatLon(v.getLatLon().lat(), v.getLatLon().lon()))
            .toList();
        List<WayPoint> wayPoints = Mapper.sightsToWayPoint(new ArrayList<>(sights));
        return generateGPXWithWaypoints(latLonList, wayPoints);
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
