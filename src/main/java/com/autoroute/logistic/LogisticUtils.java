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

    public static double getCycleDistanceSlow(List<Vertex> list) {
        assert list.size() >= 2;
        double distCycle = 0;
        for (int i = 1; i < list.size(); i++) {
            final Vertex prevV = list.get(i - 1);
            final Vertex nextV = list.get(i);
            distCycle += LatLon.distanceKM(prevV.getLatLon(), nextV.getLatLon());
        }
        return distCycle;
    }

}
