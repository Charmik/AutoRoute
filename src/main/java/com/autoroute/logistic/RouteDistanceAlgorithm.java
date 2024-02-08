package com.autoroute.logistic;

import com.autoroute.Constants;
import com.autoroute.api.overpass.Box;
import com.autoroute.api.overpass.OverPassAPI;
import com.autoroute.api.overpass.OverpassResponse;
import com.autoroute.logistic.rodes.*;
import com.autoroute.logistic.rodes.dijkstra.DijkstraAlgorithm;
import com.autoroute.logistic.rodes.dijkstra.DijkstraCache;
import com.autoroute.osm.LatLon;
import com.autoroute.osm.tags.SightMapper;
import com.autoroute.osm.tags.TagsFileReader;
import com.autoroute.sight.Sight;
import com.autoroute.sight.SightAdder;
import com.autoroute.utils.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RouteDistanceAlgorithm {

    private static final Logger LOGGER = LogManager.getLogger(RouteDistanceAlgorithm.class);
    private static final int CORES = 1; //Runtime.getRuntime().availableProcessors();
    private static final int MAX_FINDING_TIME = 1 * 60 * 1000;

    // TODO: moved ThreadPool from here

    private static final ExecutorService OSM_POOL = Executors.newFixedThreadPool(CORES);

    private final OverPassAPI overPassAPI;
    private final String user; // TODO: delete this field and move logic inside PointVisitor

    public RouteDistanceAlgorithm(LatLon start, int maxDistanceKM, String user) {
        this.user = user;
        this.overPassAPI = new OverPassAPI(start, maxDistanceKM);
    }

    public List<Route> buildRoutes(LatLon start,
                                   int minDistanceKM,
                                   int maxDistanceKM,
                                   PointVisiter pointVisiter,
                                   int threads) {
        LOGGER.info("Start buildRoute");
        final OverpassResponse rodes =
            overPassAPI.getRodes(new LatLon(start.lat(), start.lon()), maxDistanceKM * 1000 / 2);

        // for fast testing only
//        Utils.writeVertecesToFile(rodes);
//        final OverpassResponse rodes = Utils.readVertices(new LatLon(start.lat(), start.lon()), 60);

        return buildRoutes(rodes, start, minDistanceKM, maxDistanceKM);
    }

    /**
     * Build a route with the given distance restrictions via the given way points.
     *
     * @param rodes
     * @param start       Start points of the route
     * @param minDistance the minimum distance of the trip.
     * @param maxDistance the maximum distance of the trip.
     */
    private List<Route> buildRoutes(OverpassResponse rodes,
                                    LatLon start,
                                    int minDistance,
                                    int maxDistance) {
        var tagsReader = new TagsFileReader();
        tagsReader.readTags();

        final Future<OverpassResponse> nodesFuture = getNodesAsync(start, maxDistance, tagsReader);

        long startBuildingGraph = System.currentTimeMillis();
        final Graph fullGraph = GraphBuilder.buildFullGraph(rodes, start, minDistance, maxDistance);
        DijkstraCache.createCache(fullGraph);
        long finishBuildingGraph = System.currentTimeMillis();
        LOGGER.info("build graph for: {}s", (finishBuildingGraph - startBuildingGraph) / 1000);
        LOGGER.info("Start generateRoutes");
        List<Route> routes = generateRoutes(rodes, start, minDistance, maxDistance, fullGraph);
        long finishGeneratedRoutes = System.currentTimeMillis();
        LOGGER.info("generated routes for: {}s", (finishGeneratedRoutes - finishBuildingGraph) / 1000);

        var goodSights = getSights(nodesFuture, start, tagsReader);

        List<Route> routesWithSights = new ArrayList<>();
        for (int i = 0; i < routes.size(); i++) {
            var vertices = routes.get(i);
            final Route newRoute = SightAdder.addSights(vertices, goodSights, fullGraph);
            if (!newRoute.sights().isEmpty()) {
                routesWithSights.add(newRoute);
            }
            LOGGER.info("processed: {}/{} routes with: {} sights", i + 1, routes.size(), goodSights.size());
        }
        // TODO: if 2 routes have the same sights - choose only 1 of them?
        LOGGER.info("found: {} routes with good sights", routesWithSights.size());

        // TODO: save original index in gpx
        if (!Utils.isDebugging()) {
            routesWithSights.sort((r1, r2) -> {
                final Set<Sight> s1 = r1.sights();
                final Set<Sight> s2 = r2.sights();
                int rating1 = 0;
                for (Sight sight : s1) {
                    rating1 += sight.rating();
                }
                int rating2 = 0;
                for (Sight sight : s2) {
                    rating2 += sight.rating();
                }
                return Integer.compare(rating2, rating1);
            });
        }

        int routeCount = 0;
        for (Route route : routesWithSights) {
            routeCount++;
            Utils.writeDebugGPX(route,
                routeCount + "_" + (int) (LogisticUtils.getCycleDistanceSlow(route.route())));
        }
        LOGGER.info("were: {} routes, with sights found: {}", routes.size(), routesWithSights.size());
        long finishAddingSights = System.currentTimeMillis();
        LOGGER.info("added sights for: {}s", (finishAddingSights - finishGeneratedRoutes) / 1000);
        return routesWithSights;
    }

    @NotNull
    private static List<Route> generateRoutes(OverpassResponse rodes, LatLon start, int minDistance, int maxDistance, Graph fullGraph) {
        final Vertex startVertexFullGraph = fullGraph.findNearestVertex(start);
        LOGGER.info("start building compact graph");
        // TODO: can we reuse fullGraph not to build Vertexes, just copy it?
        Graph compactGraph = GraphBuilder.buildCompactGraph(rodes, start,
            startVertexFullGraph.getIdentificator(), minDistance, maxDistance, fullGraph);

        var dijkstra = new DijkstraAlgorithm(fullGraph, startVertexFullGraph);
        fullGraph.calculateDistanceForNeighbours();
        fullGraph.buildIdentificatorToVertexMap();
        dijkstra.run();
        compactGraph.calculateDistanceForNeighbours();

        var startVertexCompactGraph = compactGraph.findNearestVertex(start);
        assert startVertexFullGraph.getIdentificator() == startVertexCompactGraph.getIdentificator();
        return generateRoutesFromGraph(compactGraph, startVertexCompactGraph, dijkstra);
    }

    private List<Sight> getSights(Future<OverpassResponse> nodesFuture, LatLon start, TagsFileReader tagsReader) {
        LOGGER.info("waiting nodes from async query");
        OverpassResponse overpassResponse;
        try {
            overpassResponse = nodesFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.warn("exception in getting nodes", e);
            return Collections.emptyList();
        }
        LOGGER.info("found: {} original sights", overpassResponse.getNodes().size());
        var sights = SightMapper.getSightsFromNodes(overpassResponse.getNodes(), tagsReader);
        sights.sort(Comparator.comparingInt(Sight::rating).reversed());
        LOGGER.info("found: {} sorted sights", overpassResponse.getNodes().size());
        var goodSights = new ArrayList<>(sights.stream()
            .filter(s -> s.name() != null)
            .filter(s -> s.rating() != 0)
            .filter(s -> LatLon.distanceKM(s.latLon(), start) > 5)
            .toList());
        LOGGER.info("found: {} good sights", goodSights.size());
        return goodSights;
    }

    @NotNull
    private Future<OverpassResponse> getNodesAsync(LatLon start, int maxDistance, TagsFileReader tagsReader) {
        double diffDegree = ((double) maxDistance / Constants.KM_IN_ONE_DEGREE) / 2;
        final Box box = new Box(
            start.lat() - diffDegree,
            start.lon() - diffDegree,
            start.lat() + diffDegree,
            start.lon() + diffDegree
        );
        LOGGER.info("getNodesAsync box: {}", box);
        return OSM_POOL.submit(() -> {
            for (int i = 0; i < 5; i++) {
                OverpassResponse response = overPassAPI.getNodesInBoxByTags(box, tagsReader.getTags());
                if (!response.getNodes().isEmpty()) {
                    LOGGER.info("got nodes by async query");
                    return response;
                }
                LOGGER.info("didn't find any sights, try again: {}", i);
            }
            LOGGER.warn("we couldn't get sights with retries, return empty result");
            return new OverpassResponse();
        });
    }

    // TODO: make parallel, 1 dfs for every thread. duplicates in merge-thread once again?
    private static List<Route> generateRoutesFromGraph(Graph compactGraph,
                                                       Vertex startVertex,
                                                       DijkstraAlgorithm dijkstra) {
        LOGGER.info("Final graph has: {} vertices", compactGraph.getVertices().size());

        int tries = 0;
        final List<Cycle> cycles = new ArrayList<>();
        compactGraph.calculateDistanceForNeighbours();
        compactGraph.buildIdentificatorToVertexMap();
        int newSize;
        List<Route> routes = new ArrayList<>();

        long lastTimeFoundNewRouteTimestamp = System.currentTimeMillis();
        do {
            int oldSize = cycles.size();
            compactGraph.findAllCycles(startVertex, cycles, dijkstra);

            newSize = cycles.size();
            if (newSize > oldSize) {
                for (int i = oldSize; i < newSize; i++) {
                    var cycle = cycles.get(i);
                    LOGGER.info("wrote a cycle: {} with: {} vertexes", i + 1, cycle.size());

                    dijkstra.assertStartVertex(startVertex);
                    double minDistanceToCycle = Double.MAX_VALUE;
                    Vertex closestVertex = null;
                    int indexClosestVertex = -1;
                    for (int j = 0; j < cycle.size(); j++) {
                        Vertex v = cycle.getVertices().get(j);
                        final double distanceToV = dijkstra.getDistance(v);
                        if (distanceToV < minDistanceToCycle) {
                            minDistanceToCycle = distanceToV;
                            closestVertex = v;
                            indexClosestVertex = j;
                        }
                        /*
                        This assertion fails because we have vertexes from different graphs. change all  of them from fullGraph?
                        if (j > 0) {
                            Vertex u = cycle.getVertices().get(j - 1);
                            assert u.containsNeighbor(v) : "route: " + (i + 1);
                            assert v.containsNeighbor(u) : "route: " + (i + 1);
                        }
                        */
                    }
                    assert closestVertex != null;
                    assert indexClosestVertex != -1;
                    final List<Vertex> routeToCycle = dijkstra.getRouteFromFullGraph(closestVertex);

                    final List<Vertex> fullRoute = new ArrayList<>(routeToCycle);
                    int j = (indexClosestVertex + 1) % cycle.size();
                    while (j != indexClosestVertex) {
                        fullRoute.add(cycle.getVertices().get(j));
                        j = (j + 1) % cycle.size();
                    }
                    // TODO: try to find another way back home if possible if not - take the same way.
                    Collections.reverse(routeToCycle);
                    fullRoute.addAll(routeToCycle);
                    final double routeDistance = LogisticUtils.getCycleDistanceSlow(fullRoute);
                    Route route = new Route(fullRoute, routeDistance);
                    routes.add(route);
                    // TODO: put distance in Route class which we return

                    Utils.writeDebugGPX(fullRoute, "routes/" + (i + 1) + "_" + (int) routeDistance);
                    lastTimeFoundNewRouteTimestamp = System.currentTimeMillis();
                }
                tries = 0;
                continue;
            }
            tries++;
            final long now = System.currentTimeMillis();
            int maxTime = MAX_FINDING_TIME;
            if (now - lastTimeFoundNewRouteTimestamp > maxTime) {
                LOGGER.info("couldn't find a new route for more then: {} seconds", maxTime / 1000);
                break;
            }
            if (tries % 1000 == 0) {
                LOGGER.info("build cycles tries: {}", tries);
            }
            // TODO: should depends on the distance. gives more tries for longer routes
        } while (tries != 25000 && newSize < 100);

        LOGGER.info("findAllCycles finished, found: {} cycles", cycles.size());
        return routes;
    }
}
