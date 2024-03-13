package com.autoroute.gpx;

import com.autoroute.osm.WayPoint;
import com.autoroute.sight.Sight;

import java.util.List;

public class Mapper {

    public static List<WayPoint> sightsToWayPoint(List<Sight> sight) {
        return sight.stream()
            .map(Mapper::sightToWayPoint)
            .toList();
    }

    public static WayPoint sightToWayPoint(Sight sight) {
        return new WayPoint(sight.id(), sight.latLon(), sight.name());
    }

}
