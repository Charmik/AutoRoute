package com.autoroute.logistic;

import com.autoroute.Constants;
import com.autoroute.api.osrm.services.OsrmResponse;
import com.autoroute.api.osrm.services.TooManyCoordinatesException;
import com.autoroute.api.osrm.services.TripAPI;
import com.autoroute.gpx.GpxGenerator;
import com.autoroute.gpx.RouteDuplicateDetector;
import com.autoroute.osm.LatLon;
import com.autoroute.osm.WayPoint;
import io.jenetics.jpx.GPX;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class RouteDistanceAlgorithm {

    private static final Logger LOGGER = LogManager.getLogger(RouteDistanceAlgorithm.class);
    private static final int MAX_ITERATIONS = 1_000;

    // TODO: remove static
    private static final ExecutorService FILTER_NODES_POOL = Executors.newFixedThreadPool(5);
    private static final ExecutorService ALGORITHM_POOL = Executors.newFixedThreadPool(5);

    private final TripAPI tripAPI;
    private final RouteDuplicateDetector duplicate;
    private int debugIndexCount = 0;

    public RouteDistanceAlgorithm(RouteDuplicateDetector duplicate) {
        this.tripAPI = new TripAPI();
        this.duplicate = duplicate;
    }

    @Nullable
    public OsrmResponse buildRoute(double minDistance,
                                   double maxDistance,
                                   final List<WayPoint> originalWayPoints,
                                   double kmPerOneNode,
                                   PointVisiter pointVisiter,
                                   int threads) {
        LOGGER.info("Start buildRoute");
        CompletionService<OsrmResponse> completionService =
            new ExecutorCompletionService<>(ALGORITHM_POOL);

        AtomicBoolean completed = new AtomicBoolean(false);
        // don't need to do it every route
        List<WayPoint> currentWayPoints = filterWayPoints(maxDistance, originalWayPoints, pointVisiter);
        for (int i = 0; i < threads; i++) {
            var threadLocalWayPoints = new ArrayList<>(currentWayPoints);
            completionService.submit(() -> buildRoute(
                minDistance, maxDistance, kmPerOneNode, threadLocalWayPoints, completed));
        }

        try {
            for (int i = 0; i < threads; i++) {
                var response = completionService.take().get();
                LOGGER.info("thread: {} got response is null: {}", i, response == null);
                if (response != null) {
                    return response;
                }
            }
        } catch (InterruptedException e) {
            return null;
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    /**
     * Build a route with the given distance restrictions via the given way points.
     *
     * @param minDistance      the minimum distance of the trip.
     * @param maxDistance      the maximum distance of the trip.
     * @param currentWayPoints [0] is a start point, we can't erase it.
     */
    @Nullable
    private OsrmResponse buildRoute(double minDistance,
                                    double maxDistance,
                                    double kmPerOneNode,
                                    final List<WayPoint> currentWayPoints,
                                    AtomicBoolean completed) {
        if (currentWayPoints.size() == 1) {
            throw new IllegalArgumentException("can't build route without way points");
        }
        List<WayPoint> erasedPoints = new ArrayList<>();
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            if (iteration == MAX_ITERATIONS / 10 || iteration == MAX_ITERATIONS / 5 || iteration == MAX_ITERATIONS / 2) {
                kmPerOneNode *= 1.5;
                LOGGER.warn("couldn't build a route for {} iterations. decrease kmPerOneNode to: {},",
                    iteration, kmPerOneNode);
            }

            if (completed.get()) {
                return null;
            }
            try {
                if (currentWayPoints.size() == 1) {
//                    LOGGER.info("Add a new waypoint because we have only start point");
                    if (erasedPoints.isEmpty()) {
                        LOGGER.info("don't have nodes to erase. Exit");
                        return null;
                    }
                    eraseAndAddRandomPoint(currentWayPoints, erasedPoints);
                }
                LOGGER.info("Start generate trip from: {} points, removed: {}, iteration: {}",
                    currentWayPoints.size(), erasedPoints.size(), iteration);
                // probably we tried all routes
                final int pointsTogether = (currentWayPoints.size()) + erasedPoints.size();
                if (pointsTogether < 50) {
                    final long routeVariants = (1L << pointsTogether);
                    if (iteration > routeVariants) {
                        LOGGER.info("stop building because we tried: {} iterations by: {}. points: {}",
                            iteration, routeVariants, pointsTogether);
                        return null;
                    }
                }

                // can we use fast generateTrip without coordinates just for distance and when found a route - use full mode? Is it faster?
                OsrmResponse response = tripAPI.generateTrip(currentWayPoints, true);
                if (response.distance() < minDistance || currentWayPoints.size() == 1) {
                    LOGGER.info("got too small distance: " + response.distance() + ", add 1 node");
                    if (erasedPoints.isEmpty()) {
                        LOGGER.info("Can't build a route with given waypoints");
                        return null;
                    }
                    eraseAndAddRandomPoint(currentWayPoints, erasedPoints);
                    FindStats.increment(FindStats.TOO_SMALL);
                } else if (response.distance() > maxDistance) {
                    int diffDistance = (int) (response.distance() - maxDistance);
                    int removeNodesCount = ((int) (diffDistance / kmPerOneNode)) + 1;
                    if (removeNodesCount > currentWayPoints.size()) {
                        removeNodesCount = (currentWayPoints.size() / 2) + 1;
                    }
                    if (currentWayPoints.size() < 5) {
                        removeNodesCount = 1;
                    }
                    LOGGER.info("got too big distance: " + response.distance() +
                        ", diff: " + diffDistance + ", remove: " + removeNodesCount + " nodes");
//                    // ERASE WAYPOINTS WITH MAX DISTANCE FIRST
//                    for (int i = 0; i < removeNodesCount; i++) {
//                        int maxDistancePointIndex = 1;
//                        for (int j = 2; j < currentWayPoints.size(); j++) {
//                            if (currentWayPoints.get(j))
//                        }
//                    }

                    for (int i = 0; i < removeNodesCount; i++) {
                        eraseAndAddRandomPoint(erasedPoints, currentWayPoints);
                    }
                    FindStats.increment(FindStats.TOO_BIG);
                } else if (response.distance() / response.wayPoints().size() > kmPerOneNode) {
                    LOGGER.info("distance: " + response.distance() +
                        " has only " + response.wayPoints().size() + " nodes. Try to add more nodes");
                    eraseAndAddRandomPoint(currentWayPoints, erasedPoints);
                    FindStats.increment(FindStats.NOT_ENOUGH_NODES);
                } else if (hasPointViaStartWaypoint(response.coordinates())) {
                    LOGGER.info("route has points via Start");
                    if (currentWayPoints.size() > 2) {
                        eraseAndAddRandomPoint(erasedPoints, currentWayPoints);
                    } else {
                        LOGGER.info("We have only 1 point, so route to there and back. don't remove this point");
                        eraseAndAddRandomPoint(currentWayPoints, erasedPoints);
                    }
                    FindStats.increment(FindStats.HAS_START_POINT);
                } else if (hasACycle(response.coordinates())) {
                    LOGGER.info("route has a Cycle");
                    eraseAndAddRandomPoint(erasedPoints, currentWayPoints);
                    FindStats.increment(FindStats.HAS_A_CYCLE);
                } else if (routeIsDuplicate(response)) {
                    LOGGER.info("route is a duplicate. Clear up everything");
                    while (currentWayPoints.size() > 1) {
                        eraseAndAddRandomPoint(erasedPoints, currentWayPoints);
                    }
                    FindStats.increment(FindStats.DUPLICATE);
                } else {
                    if (!completed.compareAndSet(false, true)) {
                        return null;
                    }
                    response = addWaypointsAsManyAsPossible(
                        maxDistance, currentWayPoints, erasedPoints, kmPerOneNode, response);
                    LOGGER.info("Found a route with: " + response.wayPoints().size() + " waypoints!");
                    return new OsrmResponse(response, kmPerOneNode);
                }
//                saveDebugTrack(response);
                if (iteration % 10 == 0) {
                    FindStats.printStats();
                }
            } catch (HttpTimeoutException e) {
                LOGGER.info("Sleep for a while. got timeout from points: " + currentWayPoints);
                try {
                    Thread.sleep(60 * 1000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            } catch (TooManyCoordinatesException e) {
                int erase = 1;
                erase += currentWayPoints.size() / 2;
                for (int i = 0; i < erase; i++) {
                    eraseAndAddRandomPoint(erasedPoints, currentWayPoints);
                }
                LOGGER.info("got TooManyCoordinatesException with length: {} erase: {}"
                    , currentWayPoints.size(), erase);
            }
        }
        throw new IllegalStateException(
            "couldn't build a route with given wayPoints for: " + MAX_ITERATIONS + " iterations");
    }

    private void saveDebugTrack(OsrmResponse response) {
        var gpx = GpxGenerator.generate(response.coordinates(), response.wayPoints());
        final String debugFileName = "debug/" + ((debugIndexCount++) % 50) + ".gpx";
        final Path tmpPath = Paths.get(debugFileName);
        try {
            GPX.write(gpx, tmpPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        LOGGER.info("Found waypoint close to start. Erase random point. " + debugFileName);
    }

    // TODO: can be done in parallel
    private OsrmResponse addWaypointsAsManyAsPossible(double maxDistance,
                                                      List<WayPoint> currentWayPoints,
                                                      List<WayPoint> erasedPoints,
                                                      double kmPerOneNode,
                                                      OsrmResponse response) throws TooManyCoordinatesException {
        int maxNodesAdd = (int) (maxDistance / kmPerOneNode);
        LOGGER.info("try to add waypoints to the route with distance: " +
            response.distance() + " maxNodes. Can add more: " + maxNodesAdd);

        ArrayList<WayPoint> erasedShuffle = filterErasedPoints(erasedPoints, response);

        int count = 0;
        for (int i = 0; i < erasedShuffle.size(); i++) {
            WayPoint erasedPoint = erasedShuffle.get(i);
            currentWayPoints.add(erasedPoint);
            try {
                OsrmResponse newResponse = tripAPI.generateTrip(currentWayPoints, true);
                var distanceDiff = newResponse.distance() - response.distance();
                if (newResponse.distance() < maxDistance && (distanceDiff < maxDistance / 100 * 2)) {
                    response = newResponse;
                    LOGGER.info("added waypoint to the route, new distance: {}, {}/{}",
                        newResponse.distance(), i, erasedShuffle.size());
                    count++;
                    if (response.distance() > maxDistance / 100 * 95) {
                        LOGGER.info("got enough distance, so break adding waypoints");
                        break;
                    }
                    if (count == maxNodesAdd) {
                        LOGGER.info("Added enough waypoints: " + response.wayPoints().size());
                        break;
                    }
                } else {
                    LOGGER.info("couldn't add waypoint, try next one: {}/{}", i, erasedShuffle.size());
                    currentWayPoints.remove(currentWayPoints.size() - 1);
                }
            } catch (HttpTimeoutException e) {
                LOGGER.info("Sleep for a while. got timeout from points: " + currentWayPoints);
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
            .skip(1)
            .filter(point -> !pointVisiter.isVisited(Constants.DEFAULT_USER, point))
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
                    final OsrmResponse response = tripAPI.generateRoute(twoPoints);

                    final int newCount = count.addAndGet(1);
                    LOGGER.info("got trip " +
                        newCount + "/" + filteredPoints.size());
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
                    LOGGER.info("add current points, distance: " + response.distance());
                } else {
                    LOGGER.info("remove current point, distance: " + response.distance());
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        LOGGER.info("Removed: " + (filteredPoints.size() - result.size()) + " nodes. Got: " + result.size() + " nodes");
        tripAPI.flush();
        return result;
    }

    private void eraseAndAddRandomPoint(List<WayPoint> addList, List<WayPoint> removeList) {
        if (removeList.size() == 1) {
            return;
        }
        assert !removeList.isEmpty();
        var erasedWayPoint = getRandomAndRemoveFromList(removeList);
        addList.add(erasedWayPoint);
    }

    private WayPoint getRandomAndRemoveFromList(List<WayPoint> points) {
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
            if (points.get(i).isCloseInCity(startPoint)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasACycle(List<LatLon> points) {
        int startIndex = (int) (((double) points.size()) / 100 * 10);
        int finishIndex = (int) (((double) points.size()) / 100 * 90);

        int count = 0;
        int eliminate_points = 50;
        for (int i = startIndex; i <= finishIndex; i++) {
            for (int j = i + eliminate_points; j <= finishIndex; j++) {
                if (points.get(i).isClosePoint(points.get(j))) {
                    count++;
                    break;
                }
            }
        }
        LOGGER.info("found: " + count + " similar points from: " + (finishIndex - startIndex));
        // 10% of the route can go back
        if (count > (finishIndex - startIndex - eliminate_points) / 100 * 10) {
            return true;
        }
        return false;
    }

    public TripAPI getTripAPI() {
        return tripAPI;
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
                LOGGER.info(value + " " + stats.get(value));
            }
        }
    }

    public static void main(String[] args) throws IOException {
        final GPX gpx = GPX.read(Paths.get("tracks/36.378029_33.92704/2.gpx"));
        final List<io.jenetics.jpx.WayPoint> points = gpx.tracks().toList().get(0).getSegments().get(0).getPoints();

        final List<LatLon> latLons = points.stream()
            .map(point -> new LatLon(point.getLatitude().doubleValue(), point.getLongitude().doubleValue()))
            .toList();
        System.out.println("size: " + latLons.size());
        final RouteDistanceAlgorithm routeDistanceAlgorithm = new RouteDistanceAlgorithm(null);
        final boolean b = routeDistanceAlgorithm.hasACycle(latLons);
        System.out.println(b);
    }
}
