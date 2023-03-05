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
    private static final double SIGHTS_PER_KM = 0.05;

    public static Route addSights(Route route, List<Sight> sights, Graph fullGraph) {
        Set<Sight> sightsInRoute = new HashSet<>();
        int maxSights = (int) (route.routeDistance() * SIGHTS_PER_KM);
        for (Sight sight : sights) {
            if (sightsInRoute.size() > maxSights) {
                break;
            }
            Vertex v = LogisticUtils.findNearestVertex(sight.latLon(), route.route());
            if (v.isSynthetic()) {
                continue;
            }
            if (LatLon.distanceKM(v.getLatLon(), sight.latLon()) < 0.2 && !sightsInRoute.contains(sight)) {
                final Vertex vInFullGraph = fullGraph.findByIdentificator(v.getIdentificator());
                final Vertex sightVertex = fullGraph.findNearestVertex(sight.latLon());

                var dijkstra = new DijkstraAlgorithm(fullGraph, vInFullGraph);
                dijkstra.run(sightVertex);
                List<Vertex> routeFromVToSight = dijkstra.getRouteFromFullGraph(sightVertex);

                final double distanceFromLastToSight =
                    LatLon.distanceKM(routeFromVToSight.get(routeFromVToSight.size() - 1).getLatLon(), sight.latLon());
                for (int i = 0; i < routeFromVToSight.size() - 1; i++) {
                    final Vertex u = routeFromVToSight.get(i);
                    if (LatLon.distanceKM(u.getLatLon(), sight.latLon()) < distanceFromLastToSight) {
                        routeFromVToSight = routeFromVToSight.subList(0, i + 1);
                        break;
                    }
                }

                // TODO: we need to have ALL types of roads here in fullGraph to be able to find a route which is not a road
                if (routeFromVToSight.size() == 1) {
                    // we didn't find a route between start & finish
                    routeFromVToSight.add(new Vertex(sight.latLon()));
                }
                final ArrayList<Vertex> reversedPath = new ArrayList<>(routeFromVToSight);
                Collections.reverse(reversedPath);
                routeFromVToSight.addAll(reversedPath);
                // TODO: check that distance < maxDistance
                // TODO: need to check if i + 1 bigger than route.size() ?
                int index = route.getIndexByVertex(v);
                route.route().addAll(index + 1, routeFromVToSight);
                sightsInRoute.add(sight);
                // sights.remove(sight); // uncomment if we want unique sights
            }
        }
        // TODO: if we already have another route which include all sights from this route - skip this one?
        return new Route(route.route(), sightsInRoute, LogisticUtils.getCycleDistanceSlow(route.route()));
    }
}
