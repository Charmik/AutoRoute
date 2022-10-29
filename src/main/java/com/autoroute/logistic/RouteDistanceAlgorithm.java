package com.autoroute.logistic;

import com.autoroute.Constants;
import com.autoroute.api.overpass.Box;
import com.autoroute.api.overpass.OverPassAPI;
import com.autoroute.api.overpass.OverpassResponse;
import com.autoroute.api.trip.services.OsrmAPI;
import com.autoroute.api.trip.services.OsrmResponse;
import com.autoroute.api.trip.services.TooManyCoordinatesException;
import com.autoroute.logistic.rodes.Cycle;
import com.autoroute.logistic.rodes.Graph;
import com.autoroute.logistic.rodes.GraphBuilder;
import com.autoroute.logistic.rodes.Route;
import com.autoroute.logistic.rodes.Vertex;
import com.autoroute.logistic.rodes.dijkstra.DijkstraAlgorithm;
import com.autoroute.logistic.rodes.dijkstra.DijkstraCache;
import com.autoroute.osm.LatLon;
import com.autoroute.osm.WayPoint;
import com.autoroute.osm.tags.SightMapper;
import com.autoroute.osm.tags.TagsFileReader;
import com.autoroute.sight.Sight;
import com.autoroute.sight.SightAdder;
import com.autoroute.utils.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    private static final ExecutorService OSM_POOL = Executors.newFixedThreadPool(CORES);

    private final OsrmAPI osrmAPI;
    private final OverPassAPI overPassAPI;
    private final String user; // TODO: delete this field and move logic inside PointVisitor

    public RouteDistanceAlgorithm(LatLon start, int maxDistanceKM, String user) {
        this.user = user;
        this.osrmAPI = new OsrmAPI();
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
//         Utils.writeVertecesToFile(rodes);
//         final OverpassResponse rodes = Utils.readVertices();
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
        final Future<OverpassResponse> nodesFuture = getNodesAsync(start, maxDistance);

        final Graph fullGraph = GraphBuilder.buildFullGraph(rodes, start, minDistance, maxDistance);
        LOGGER.info("Start generateRoutes");
        List<Route> routes = generateRoutes(rodes, start, minDistance, maxDistance, fullGraph);

        var goodSights = getSights(nodesFuture, start, maxDistance);

        List<Route> routesWithSights = new ArrayList<>();
        for (int i = 0; i < routes.size(); i++) {
            var vertices = routes.get(i);
            final Route newRoute = SightAdder.addSights(vertices, goodSights, fullGraph);
            if (!newRoute.sights().isEmpty()) {
                routesWithSights.add(newRoute);
            }
            LOGGER.info("processed: {}/{} routes with: {} sights", i, routes.size(), goodSights.size());
        }
        LOGGER.info("found: {} routes with good sights", routesWithSights.size());

        int routeCount = 0;
        for (Route route : routesWithSights) {
            routeCount++;
            Utils.writeDebugGPX(route,
                routeCount + "_" + (int) (Cycle.getCycleDistanceSlow(route.route())));
        }
        LOGGER.info("were: {} routes, with sights found: {}", routes.size(), routesWithSights.size());
        return routesWithSights;
    }

    @NotNull
    private static List<Route> generateRoutes(OverpassResponse rodes, LatLon start, int minDistance, int maxDistance, Graph fullGraph) {
        final Vertex startVertexFullGraph = fullGraph.findNearestVertex(start);
        Graph compactGraph = GraphBuilder.buildGraph(rodes, start,
            startVertexFullGraph.getIdentificator(), minDistance, maxDistance);

        var dijkstra = new DijkstraAlgorithm(fullGraph, startVertexFullGraph);
        fullGraph.calculateDistanceForNeighbours();
        dijkstra.run();
        fullGraph.buildIdentificatorToVertexMap();

        compactGraph.setFullGraph(fullGraph);
        compactGraph.calculateDistanceForNeighbours();

        var startVertexCompactGraph = compactGraph.findNearestVertex(start);
        assert startVertexFullGraph.getIdentificator() == startVertexCompactGraph.getIdentificator();
        compactGraph.calculateSuperVertices();
        return generateRoutesFromGraph(compactGraph, startVertexCompactGraph, dijkstra);
    }

    private List<Sight> getSights(Future<OverpassResponse> nodesFuture, LatLon start, int maxDistance) {
        LOGGER.info("waiting nodes from async query");
        OverpassResponse overpassResponse;
        try {
            overpassResponse = nodesFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.warn("exception in getting nodes", e);
            return Collections.emptyList();
        }
        LOGGER.info("found: {} original sights", overpassResponse.getNodes().size());
        if (overpassResponse.getNodes().isEmpty()) {
            for (int i = 0; i < 5; i++) {
                LOGGER.info("didn't find any sights, try again: {}", i);
                nodesFuture = getNodesAsync(start, maxDistance);
                try {
                    overpassResponse = nodesFuture.get();
                } catch (InterruptedException | ExecutionException e) {
                    LOGGER.warn("exception in getting nodes", e);
                }
                if (!overpassResponse.getNodes().isEmpty()) {
                    break;
                }
            }
        }
        var sights = SightMapper.getSightsFromNodes(overpassResponse.getNodes());
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
    private Future<OverpassResponse> getNodesAsync(LatLon start, int maxDistance) {
        var tagsReader = new TagsFileReader();
        tagsReader.readTags();
        double diffDegree = ((double) maxDistance / Constants.KM_IN_ONE_DEGREE) / 2;
        final Box box = new Box(
            start.lat() - diffDegree,
            start.lon() - diffDegree,
            start.lat() + diffDegree,
            start.lon() + diffDegree
        );
        return OSM_POOL.submit(() -> overPassAPI.getNodesInBoxByTags(box, tagsReader.getTags()));
    }

    // TODO: make parallel, 1 dfs for every thread. duplicates in merge-thread once again?
    private static List<Route> generateRoutesFromGraph(Graph g,
                                                       Vertex startVertex,
                                                       DijkstraAlgorithm dijkstra) {
        LOGGER.info("Final graph has: {} vertices", g.getVertices().size());

        int tries = 0;
        final List<Cycle> cycles = new ArrayList<>();
        g.calculateDistanceForNeighbours();
        g.buildIdentificatorToVertexMap();
        int newSize;
        List<Route> routes = new ArrayList<>();

        final DijkstraCache dijkstraCache = new DijkstraCache();
        long lastTimeFoundNewRouteTimestamp = System.currentTimeMillis();
        do {
            int oldSize = cycles.size();
            g.findAllCycles(startVertex, cycles, dijkstra, dijkstraCache);

            newSize = cycles.size();
            if (cycles.size() > oldSize) {
                for (int i = oldSize; i < cycles.size(); i++) {
                    var cycle = cycles.get(i);
                    LOGGER.info("wrote a cycle: {} with: {} vertexes", i, cycle.size());

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
                    }
                    assert closestVertex != null;
                    assert indexClosestVertex != -1;
                    final List<Vertex> routeToCycle = dijkstra.getRouteFromFullGraph(closestVertex);
                    assert routeToCycle.get(routeToCycle.size() - 1).getIdentificator() == closestVertex.getIdentificator();

                    final List<Vertex> fullRoute = new ArrayList<>(routeToCycle);
                    int j = (indexClosestVertex + 1) % cycle.size();
                    while (j != indexClosestVertex) {
                        fullRoute.add(cycle.getVertices().get(j));
                        j = (j + 1) % cycle.size();
                    }
                    // TODO: try to find another way back home if possible if not - take the same way.
                    Collections.reverse(routeToCycle);
                    fullRoute.addAll(routeToCycle);
                    routes.add(new Route(fullRoute));
                    // TODO: put distance in Route class which we return
                    Utils.writeDebugGPX(fullRoute, "routes/" + i + "_" + (int) Cycle.getCycleDistanceSlow(fullRoute));
                    lastTimeFoundNewRouteTimestamp = System.currentTimeMillis();
                }
                tries = 0;
                continue;
            }
            tries++;
            final long now = System.currentTimeMillis();
            final int MAX_FINDING_TIME = 1 * 60 * 1000;
            if (now - lastTimeFoundNewRouteTimestamp > MAX_FINDING_TIME) {
                LOGGER.info("couldn't find a new route for more then: {} seconds", MAX_FINDING_TIME / 1000);
                break;
            }
            if (tries % 100 == 0) {
                LOGGER.info("build cycles tries: {}", tries);
            }
            // TODO: should depends on the distance. gives more tries for longer routes
        } while (tries != 25000 && newSize < 100);

        LOGGER.info("findAllCycles finished, found: {} cycles", cycles.size());
        return routes;
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
