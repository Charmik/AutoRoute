package com.autoroute.logistic;

import com.autoroute.api.overpass.OverPassAPI;
import com.autoroute.api.overpass.OverpassResponse;
import com.autoroute.api.trip.services.OsrmAPI;
import com.autoroute.api.trip.services.OsrmResponse;
import com.autoroute.api.trip.services.TooManyCoordinatesException;
import com.autoroute.logistic.rodes.Cycle;
import com.autoroute.logistic.rodes.DijkstraAlgorithm;
import com.autoroute.logistic.rodes.Graph;
import com.autoroute.logistic.rodes.GraphBuilder;
import com.autoroute.logistic.rodes.Vertex;
import com.autoroute.osm.LatLon;
import com.autoroute.osm.WayPoint;
import com.autoroute.utils.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class RouteDistanceAlgorithm {

    private static final Logger LOGGER = LogManager.getLogger(RouteDistanceAlgorithm.class);
    private static final int CORES = 1; //Runtime.getRuntime().availableProcessors();
    private static final double DEFAULT_KM_PER_ONE_NODE = 20;

    // TODO: moved ThreadPool from here

    private static final ExecutorService FILTER_NODES_POOL = Executors.newFixedThreadPool(CORES);
    private static final ExecutorService ALGORITHM_POOL = Executors.newFixedThreadPool(CORES);

    private final OsrmAPI osrmAPI;
    private final OverPassAPI overPassAPI;
    private final String user; // TODO: delete this field and move logic inside PointVisitor

    public RouteDistanceAlgorithm(String user) {
        this.user = user;
        this.osrmAPI = new OsrmAPI();
        this.overPassAPI = new OverPassAPI();
    }

    @Nullable
    public OsrmResponse buildRoute(LatLon start,
                                   int minDistance,
                                   int maxDistance,
                                   final List<WayPoint> wayPoints,
                                   PointVisiter pointVisiter,
                                   int threads) {
        LOGGER.info("Start buildRoute");

//        final OverpassResponse response =
//            overPassAPI.getRodes(new LatLon(start.lat(), start.lon()), maxDistance * 1000);
//                Utils.writeVertecesToFile(response);
        final OverpassResponse response = Utils.readVertices();
        return buildRoute(response, start, minDistance, maxDistance, wayPoints);
    }

    /**
     * Build a route with the given distance restrictions via the given way points.
     *
     * @param response
     * @param start            Start points of the route
     * @param minDistance      the minimum distance of the trip.
     * @param maxDistance      the maximum distance of the trip.
     * @param currentWayPoints waypoints
     */
    @Nullable
    private OsrmResponse buildRoute(OverpassResponse response,
                                    LatLon start,
                                    int minDistance,
                                    int maxDistance,
                                    final List<WayPoint> currentWayPoints) {

//        start = new LatLon(34.7657, 32.8728);
//        LatLon finish = new LatLon(34.7762, 32.9380);
//        Graph fullGraph = GraphBuilder.buildFullGraph(response, start, minDistance, maxDistance);
//        final Vertex startVertexFullGraph = fullGraph.findNearestVertex(start);
//        var dijkstra = new DijkstraAlgorithm(fullGraph, startVertexFullGraph);
//        dijkstra.run();
//        System.out.println(dijkstra.getDistance(fullGraph.findNearestVertex(finish)));
//        System.exit(42);


        Graph fullGraph = GraphBuilder.buildFullGraph(response, start, minDistance, maxDistance);
        final Vertex startVertexFullGraph = fullGraph.findNearestVertex(start);
        var dijkstra = new DijkstraAlgorithm(fullGraph, startVertexFullGraph);
        dijkstra.run();


        Graph compactGraph = GraphBuilder.buildGraph(response, start,
            startVertexFullGraph.getIdentificator(), minDistance, maxDistance);
        compactGraph.setFullGraph(fullGraph);
        final List<Cycle> cycles = new ArrayList<>();
        // AtomicBoolean stoppedFindingCycles = new AtomicBoolean(false);
        // TODO: try add waypoints inside in parallel. and build routes. return result as a parameter...:(
        // osrmRouteFromCycles(minDistance, maxDistance, start, cycles, stoppedFindingCycles);
        var startVertexCompactGraph = compactGraph.findNearestVertex(start);


        assert startVertexFullGraph.getIdentificator() == startVertexCompactGraph.getIdentificator();
        compactGraph.calculateSuperVertices();
        generateCyclesFromGraph(cycles, minDistance, maxDistance, startVertexCompactGraph, compactGraph, dijkstra);


//        stoppedFindingCycles.set(true);
//        while (stoppedFindingCycles.get()) {
//            Utils.sleep(5000);
//        }

        for (Cycle cycle : cycles) {

        }

        return null;
    }

    private static void generateCyclesFromGraph(List<Cycle> cycles,
                                                int minDistance,
                                                int maxDistance,
                                                Vertex startVertex,
                                                Graph g,
                                                DijkstraAlgorithm dijkstra) {
        LOGGER.info("Final graph has: {} vertices", g.getVertices().size());

        int tries = 0;
        g.calculateDistanceForNeighbours();
        int newSize;
        do {
            int oldSize = cycles.size();
            g.findAllCycles(startVertex, cycles, dijkstra);
            // TODO: remove it!? check in cycle finding...
//                var it = cycles.iterator();
//                while (it.hasNext()) {
//                    final Cycle c = it.next();
//                    final double d = distanceToCycle(startVertex, c) + distanceOfCycle(c);
//                    if (d > maxDistance) {
//                        it.remove();
////                        System.exit(43);
//                    }
//                }
            newSize = cycles.size();
            if (cycles.size() > oldSize) {
                for (int i = oldSize; i < cycles.size(); i++) {
                    var cycle = cycles.get(i);
                    new File("o/cycles").mkdirs();
                    Utils.writeGPX(cycle.vertices(), "cycles/cycle", i);
                    LOGGER.info("wrote a cycle: {} with: {} vertexes", i, cycle.size());

                    dijkstra.assertStartVertex(startVertex);
                    double minDistanceToCycle = Double.MAX_VALUE;
                    Vertex closestVertex = null;
                    int indexClosestVertex = -1;
                    for (int j = 0; j < cycle.vertices().size(); j++) {
                        Vertex v = cycle.vertices().get(j);
                        final double distanceToV = dijkstra.getDistance(v);
                        if (distanceToV < minDistanceToCycle) {
                            minDistanceToCycle = distanceToV;
                            closestVertex = v;
                            indexClosestVertex = j;
                        }
                    }
                    assert closestVertex != null;
                    assert indexClosestVertex != -1;
                    final List<Vertex> routeToCycle = dijkstra.getRouteFromFullGraph(closestVertex);
//                        Utils.writeGPX(routeToCycle, "orig2_", i);
                    assert routeToCycle.get(routeToCycle.size() - 1).getIdentificator() == closestVertex.getIdentificator();

                    final List<Vertex> fullRoute = new ArrayList<>(routeToCycle);
                    int j = (indexClosestVertex + 1) % cycle.vertices().size();
                    while (j != indexClosestVertex) {
                        fullRoute.add(cycle.vertices().get(j));
                        j = (j + 1) % cycle.vertices().size();
                    }
                    // TODO: routeToCycle += original routeToCycle reversed
//                        Utils.writeGPX(routeToCycle, "orig3_", i);

                    Collections.reverse(routeToCycle);
                    fullRoute.addAll(routeToCycle);
                    Utils.writeGPX(fullRoute, "route_", i);

                }
                tries = 0;
            }
            tries++;
            if (tries % 10 == 0) {
                LOGGER.info("build cycles tries: {}", tries);
            }
        } while (tries != 300 && newSize < 500);

        LOGGER.info("findAllCycles finished, found: {} cycles", cycles.size());
    }

    private static double distanceToCycle(Vertex startVertex, Cycle cycle) {
        assert !cycle.vertices().isEmpty();
        double minDistance = Double.MAX_VALUE;
        Vertex best = cycle.vertices().get(0);
        for (Vertex v : cycle.vertices()) {
            final double d = LatLon.distanceKM(startVertex.getLatLon(), v.getLatLon());
            if (d < minDistance) {
                minDistance = d;
                best = v;
            }
        }
        return minDistance;
    }

    private static double distanceOfCycle(Cycle cycle) {
        final List<Vertex> vertices = cycle.vertices();
        if (vertices.size() < 2) {
            LOGGER.warn("have cycle size size: {}, cycle: {}", vertices.size(), cycle);
            throw new IllegalArgumentException();
        }
        double d = LatLon.distanceKM(vertices.get(0).getLatLon(), vertices.get(1).getLatLon());
        for (int i = 1; i < vertices.size(); i++) {
            d += LatLon.distanceKM(vertices.get(i - 1).getLatLon(), vertices.get(i).getLatLon());
        }
        d += LatLon.distanceKM(vertices.get(vertices.size() - 1).getLatLon(), vertices.get(0).getLatLon());
        return d;
    }

    // TODO: can be done in parallel
    private OsrmResponse addWaypointsAsManyAsPossible(double maxDistance,
                                                      List<WayPoint> currentWayPoints,
                                                      List<WayPoint> erasedPoints,
                                                      OsrmResponse response) throws TooManyCoordinatesException {
        int maxNodesAdd = (int) (maxDistance / DEFAULT_KM_PER_ONE_NODE);
        LOGGER.info("try to add waypoints to the route with distance: {}. Current waypoints: {}. Can add more: {}",
            response.distance(), currentWayPoints.size(), maxNodesAdd);

        ArrayList<WayPoint> erasedShuffle = filterErasedPoints(erasedPoints, response);

        int count = 0;
        for (int i = 0; i < erasedShuffle.size(); i++) {

            final double currentDistance = response.distance();
            if (currentDistance > maxDistance / 100 * 95) {
                LOGGER.info("we got 95% of maximum distance, don't tru to add more WayPoints. Current distance: {}",
                    currentDistance);
                return response;
            }
            WayPoint erasedPoint = erasedShuffle.get(i);
            currentWayPoints.add(erasedPoint);
            try {
                OsrmResponse newResponse = osrmAPI.generateTrip(currentWayPoints);
                var distanceDiff = newResponse.distance() - currentDistance;
                if (newResponse.distance() < maxDistance && (distanceDiff < maxDistance / 100 * 2)) {
                    response = newResponse;
                    LOGGER.info("added waypoint to the route, current distance: {}, new distance: {}, max distance: {}, {}/{}",
                        currentDistance, newResponse.distance(), maxDistance, i, erasedShuffle.size());
                    count++;
                    if (currentDistance > maxDistance / 100 * 95) {
                        LOGGER.info("got enough distance, so break adding waypoints");
                        break;
                    }
                    if (count == maxNodesAdd) {
                        LOGGER.info("Added enough waypoints: {}", response.wayPoints().size());
                        break;
                    }
                } else {
                    LOGGER.info("couldn't add waypoint, current distance: {}, new distance: {}, try next one: {}/{}",
                        currentDistance, newResponse.distance(), i, erasedShuffle.size());
                    currentWayPoints.remove(currentWayPoints.size() - 1);
                }
            } catch (HttpTimeoutException e) {
                LOGGER.info("Sleep for a while. got timeout from points: {}", currentWayPoints);
                try {
                    Thread.sleep(60 * 1000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }

        }
        return response;
    }

    @NotNull
    private static ArrayList<WayPoint> filterErasedPoints(List<WayPoint> erasedPoints, OsrmResponse response) {
        var erasedShuffle = new ArrayList<>(erasedPoints);
        Collections.shuffle(erasedShuffle);

        final ArrayList<WayPoint> filteredWaypoints = new ArrayList<>(erasedShuffle.stream()
            .filter(erasedPoint -> {
                var wayPoint = erasedPoint.latLon();
                final List<LatLon> routeCoordinates = response.coordinates();
                boolean hasClosePoint = false;
                for (LatLon routeCoordinate : routeCoordinates) {
                    if (wayPoint.isCloseInCity(routeCoordinate)) {
                        hasClosePoint = true;
                        break;
                    }
                }
                if (!hasClosePoint) {
                    LOGGER.info("filter waypoint because it's far away from the route: {}", wayPoint);
                }
                return !hasClosePoint;
            }).toList());
        LOGGER.info("filtered waypoints were: {} now: {}",
            erasedShuffle.size(), filteredWaypoints.size());
        return filteredWaypoints;
    }

    private boolean routeIsDuplicate(OsrmResponse response) {
//        final List<LatLon> coordinates = response.coordinates();
//        if (duplicate.hasDuplicateInFiles(coordinates)) {
//            return true;
//        }
        return false;
    }

    private List<WayPoint> filterWayPoints(double maxDistance, List<WayPoint> originalWayPoints, PointVisiter pointVisiter) {
        LOGGER.info("Start filtering waypoints");
        var filteredPoints = new ArrayList<>(originalWayPoints.stream()
            .filter(point -> !pointVisiter.isVisited(user, point))
            .toList());
        filteredPoints.add(0, originalWayPoints.get(0));

        List<Callable<OsrmResponse>> callables = new ArrayList<>();
        AtomicInteger count = new AtomicInteger(0);
        for (int i = 1; i < filteredPoints.size(); i++) {
            var wayPoint = filteredPoints.get(i);
            Callable<OsrmResponse> callable = () -> {
                var twoPoints = new ArrayList<WayPoint>();
                twoPoints.add(filteredPoints.get(0));
                twoPoints.add(wayPoint);
                try {
                    final OsrmResponse response = osrmAPI.generateRoute(twoPoints);
                    final int newCount = count.addAndGet(1);
                    LOGGER.info("filtering waypoints: {}/{}", newCount, filteredPoints.size());
                    return response;
                } catch (TooManyCoordinatesException e) {
                    throw new RuntimeException(e);
                }
            };
            callables.add(callable);
        }

        List<WayPoint> result = new ArrayList<>();
        result.add(filteredPoints.get(0));
        try {
            var futures = FILTER_NODES_POOL.invokeAll(callables);
            for (Future<OsrmResponse> future : futures) {
                var response = future.get();
                // should be 2, but we add 0.5 more for other nodes
                if (response.distance() * 1.5 < maxDistance) { // need the way back
                    result.add(response.wayPoints().get(1));
                    LOGGER.info("filterWayPoints, add current points, distance: {}", response.distance());
                } else {
                    LOGGER.info("filterWayPoints, remove current point, distance: {}", response.distance());
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        LOGGER.info("Removed: {} nodes. Got result: {} nodes",
            filteredPoints.size() - result.size(), result.size());
        return result;
    }

    private void addToCurrentPoints(List<WayPoint> currentList, List<WayPoint> erasedList) {
        if (erasedList.isEmpty()) {
            return;
        }
        var erasedWayPoint = getRandomAndRemoveFromList(erasedList);
        currentList.add(erasedWayPoint);
    }

    private WayPoint getRandomAndRemoveFromList(List<WayPoint> points) {
        var index = ThreadLocalRandom.current().nextInt(points.size());
        var res = points.get(index);
        points.remove(index);
        return res;
    }

    private void addToErasedPoints(List<WayPoint> currentList, List<WayPoint> erasedList) {
        if (currentList.size() == 1) {
            return;
        }
        var erasedWayPoint = getRandomAndRemoveFromListExceptFirst(currentList);
        erasedList.add(erasedWayPoint);
    }

    private WayPoint getRandomAndRemoveFromListExceptFirst(List<WayPoint> points) {
        assert points.size() > 1;
        var index = ThreadLocalRandom.current().nextInt(points.size() - 1) + 1;
        var res = points.get(index);
        points.remove(index);
        return res;
    }

    private boolean hasPointViaStartWaypoint(List<LatLon> points) {
        var startPoint = points.get(0);

        int startIndex = (int) (((double) points.size()) / 100 * 20);
        int finishIndex = (int) (((double) points.size()) / 100 * 80);
        assert startIndex > 0;
        assert finishIndex > 1;
        assert finishIndex < points.size();
        for (int i = startIndex; i <= finishIndex; i++) {
            final LatLon point = points.get(i);
            if (point.isCloseInCity(startPoint)) {
                return true;
            }
        }
        return false;
    }

    public OsrmAPI getTripAPI() {
        return osrmAPI;
    }

    private enum FindStats {
        TOO_BIG,
        TOO_SMALL,
        NOT_ENOUGH_NODES,
        HAS_START_POINT,
        HAS_A_CYCLE,
        DUPLICATE;

        private static final Map<FindStats, Integer> stats = new HashMap<>();

        public static synchronized void increment(FindStats stat) {
            stats.compute(stat, (k, v) -> {
                if (v == null) {
                    return 1;
                }
                return v + 1;
            });
        }

        public static synchronized void printStats() {
            final FindStats[] values = values();
            for (FindStats value : values) {
                LOGGER.info("{} {}", value, stats.get(value));
            }
        }
    }
}
