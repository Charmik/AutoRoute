package com.autoroute.sight;

import com.autoroute.logistic.LogisticUtils;
import com.autoroute.logistic.rodes.Graph;
import com.autoroute.logistic.rodes.Route;
import com.autoroute.logistic.rodes.Vertex;
import com.autoroute.logistic.rodes.dijkstra.DijkstraAlgorithm;
import com.autoroute.osm.LatLon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SightAdder {

    private static final Logger LOGGER = LogManager.getLogger(SightAdder.class);
    private static final double SIGHTS_PER_KM = 0.1;

    public static Route addSights(Route route, List<Sight> sights, Graph fullGraph) {
        Set<Sight> sightsInRoute = new HashSet<>();
        int maxSights = (int) (route.routeDistance() * SIGHTS_PER_KM);
        for (Sight sight : sights) {
            if (sightsInRoute.size() > maxSights) {
                break;
            }
            Vertex v = LogisticUtils.findNearestVertex(sight.latLon(), route.route());
            if (LatLon.distanceKM(v.getLatLon(), sight.latLon()) < 0.2 && !sightsInRoute.contains(sight)) {
                final Vertex vInFullGraph = fullGraph.findByIdentificator(v.getIdentificator());
                final Vertex sightVertex = fullGraph.findNearestVertex(sight.latLon());
                var dijkstra = new DijkstraAlgorithm(fullGraph, vInFullGraph);
                dijkstra.run(sightVertex);
                final List<Vertex> routeFromVToSight = dijkstra.getRouteFromFullGraph(sightVertex);

                boolean addToPath = true;
                for (int j = 2; j < routeFromVToSight.size() - 2; j++) {
                    var v1 = routeFromVToSight.get(j).getLatLon();
                    var v2 = routeFromVToSight.get(j + 1).getLatLon();
                    var v3 = routeFromVToSight.get(j + 2).getLatLon();
                    final double angle = LatLon.angle(v1, v2, v3);
                    final double angleAbs = Math.abs(angle);
                    if (angleAbs < 1 || Math.abs(angleAbs - Math.PI * 2) < 1) {
                    } else {
                        addToPath = false;
                        break;
                    }
                }

                if (addToPath) {
                    final ArrayList<Vertex> reversedPath = new ArrayList<>(routeFromVToSight);
                    Collections.reverse(reversedPath);
                    routeFromVToSight.addAll(reversedPath);
                    // TODO: check that distance < maxDistance
                    // TODO: need to check if i + 1 bigger than route.size() ?
                    int index = route.getIndexByVertex(v);
                    route.route().addAll(index + 1, routeFromVToSight);
                    sightsInRoute.add(sight);
                    // sights.remove(sight);
                }
            }
        }
        return new Route(route.route(), sightsInRoute, LogisticUtils.getCycleDistanceSlow(route.route()));
    }
}
