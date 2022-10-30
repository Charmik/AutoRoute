package com.autoroute.sight;

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

    public static Route addSights(Route route, List<Sight> sights, Graph fullGraph) {
        Set<Sight> sightsInRoute = new HashSet<>();
        int i = 0;
        while (i < route.size()) {
            // TODO: use distance of route here
            if (sightsInRoute.size() > 15) {
                break;
            }
            var v = route.route().get(i);
            // TODO: we can fast sort by LatLon.fastDistance and then check only part of elements
            for (Sight sight : sights) {
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
                        if (angleAbs > 1) {
                            LOGGER.info("have angle: {} {} {} {}", angle, v1, v2, v3);
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
                        route.route().addAll(i + 1, routeFromVToSight);
                        i += routeFromVToSight.size();
                    }
                    sightsInRoute.add(sight);
                    // sights.remove(sight);
                    break;
                }
            }
            i++;
        }
        return new Route(route.route(), sightsInRoute);
    }

    public static void tryAddSight(Route route, Sight sights, Graph fullGraph) {

    }

}
