package com.autoroute.gpx;

import com.autoroute.osm.LatLon;
import com.autoroute.osm.WayPoint;
import io.jenetics.jpx.GPX;

import java.util.List;

public class GpxGenerator {

    public static GPX generate(List<LatLon> coordinates, List<WayPoint> wayPoints) {
        var builder = GPX.builder()
            .addTrack(track -> track
                .addSegment(segment -> {
                    for (LatLon coordinate : coordinates) {
                        segment.addPoint(p -> p.lat(coordinate.lat()).lon(coordinate.lon()));
                    }
                }));
        for (WayPoint wayPoint : wayPoints) {
            builder = builder.addWayPoint(b -> b.lat(wayPoint.latLon().lat())
                .lon(wayPoint.latLon().lon())
                .name(wayPoint.name()));
        }
        return builder.build();
    }
}
