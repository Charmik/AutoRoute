package com.autoroute.logistic;

import com.autoroute.logistic.rodes.Vertex;
import com.autoroute.osm.LatLon;

import java.util.List;

public class LogisticUtils {

    public static Vertex findNearestVertex(LatLon latLon, List<Vertex> vertices) {
        Vertex minV = vertices.get(0);
        double minD = LatLon.distanceKM(latLon, vertices.get(0).getLatLon());
        for (int i = 1; i < vertices.size(); i++) {
            var v = vertices.get(i);
            var d = LatLon.distanceKM(latLon, v.getLatLon());
            if (d < minD) {
                minV = v;
                minD = d;
            }
        }
        return minV;
    }

}
