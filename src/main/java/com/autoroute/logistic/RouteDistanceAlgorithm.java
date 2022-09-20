package com.autoroute.logistic;

import com.autoroute.api.trip.services.OsrmAPI;
import com.autoroute.api.trip.services.OsrmResponse;
import com.autoroute.api.trip.services.TooManyCoordinatesException;
import com.autoroute.gpx.GpxGenerator;
import com.autoroute.gpx.RouteDuplicateDetector;
import com.autoroute.osm.LatLon;
import com.autoroute.osm.WayPoint;
import com.autoroute.utils.Utils;
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
    private static final int MAX_ITERATIONS = 1000;
    private static final int ITERATION_DIVIDER = MAX_ITERATIONS / 100;

    // TODO: moved ThreadPool from here
    private static final int CORES = Runtime.getRuntime().availableProcessors();
    private static final ExecutorService FILTER_NODES_POOL = Executors.newFixedThreadPool(CORES);
    private static final ExecutorService ALGORITHM_POOL = Executors.newFixedThreadPool(CORES);

    private final OsrmAPI osrmAPI;
    private final RouteDuplicateDetector duplicate;
    private final String user; // TODO: delete this field and move logic inside PointVisitor
    private final AlgorithmIterationStats iterationStats;
    private int debugIndexCount = 0;

    public RouteDistanceAlgorithm(RouteDuplicateDetector duplicate, String user) {
        this.user = user;
        this.osrmAPI = new OsrmAPI();
        this.duplicate = duplicate;
        this.iterationStats = new AlgorithmIterationStats();
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
        iterationStats.startProcessing();
        for (int i = 0; i < threads; i++) {
            var threadLocalWayPoints = new ArrayList<>(currentWayPoints);
            completionService.submit(() -> buildRoute(
                minDistance, maxDistance, kmPerOneNode, threadLocalWayPoints, completed));
        }

        try {
            for (int i = 0; i < threads; i++) {
                var response = completionService.take().get();
                LOGGER.info("thread: {} got response is null: {}",
                    i, response == null);
                if (response != null) {
                    return response;
                }
            }
        } catch (InterruptedException e) {
            return null;
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            iterationStats.endProcessing();
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
                                    double originalKmPerOneNode,
                                    final List<WayPoint> currentWayPoints,
                                    AtomicBoolean completed) {

        if (currentWayPoints.isEmpty() || currentWayPoints.size() == 1) {
            throw new IllegalArgumentException("can't build route without way points");
        }
        var firstElementDebug = currentWayPoints.get(0);
        double kmPerOneNode = originalKmPerOneNode;
        List<WayPoint> erasedPoints = new ArrayList<>();
        while (currentWayPoints.size() > 1) {
            erasedPoints.add(currentWayPoints.get(currentWayPoints.size() - 1));
            currentWayPoints.remove(currentWayPoints.size() - 1);
        }
        int iteration = 0;
        while (iteration < MAX_ITERATIONS) {
            iteration++;
            long startIterationTime = System.currentTimeMillis();
            assert firstElementDebug.equals(currentWayPoints.get(0)); // operations should never change first element
            // every 1/100 iteration we increase kmPerOneNode by X%
            assert MAX_ITERATIONS >= 100;

            if (Utils.percent(iteration, MAX_ITERATIONS) > 50 &&
                iteration % (MAX_ITERATIONS / (MAX_ITERATIONS / ITERATION_DIVIDER)) == 0) {
                // TODO: add test that increasing should be enough to get max distance
                kmPerOneNode *= 1.03;
                kmPerOneNode = Math.min(kmPerOneNode, maxDistance);
                LOGGER.info("couldn't build a route for {} iterations. increase kmPerOneNode to: {},",
                    iteration, kmPerOneNode);
            }

            if (completed.get()) {
                return null;
            }
            try {
                while (currentWayPoints.size() == 1 || currentWayPoints.size() < minDistance / kmPerOneNode) {
                    if (erasedPoints.isEmpty()) {
                        LOGGER.info("don't have nodes to erase. Exit");
                        return null;
                    }
                    addToCurrentPoints(currentWayPoints, erasedPoints);
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
                OsrmResponse response = osrmAPI.generateTrip(currentWayPoints);
                LOGGER.info("got response distance: {}", response.distance());
                if (response.distance() < minDistance) {
                    LOGGER.info("got too small distance: {}, add 1 node", response.distance());
                    if (erasedPoints.isEmpty()) {
                        LOGGER.info("Can't build a route with given waypoints");
                        return null;
                    }
                    addToCurrentPoints(currentWayPoints, erasedPoints);
                    FindStats.increment(FindStats.TOO_SMALL);
                } else if (response.distance() > maxDistance) {
                    int diffDistance = (int) (response.distance() - maxDistance);
                    int removeNodesCount = ((int) (diffDistance / kmPerOneNode * 3)) + 1;
                    if (removeNodesCount > currentWayPoints.size()) {
                        removeNodesCount = (currentWayPoints.size() / 2) + 1;
                    }
                    if (currentWayPoints.size() < 5) {
                        removeNodesCount = 1;
                    }
                    LOGGER.info("got too big distance: {}, diff: {}, remove: {} nodes",
                        response.distance(), diffDistance, removeNodesCount);
//                    // ERASE WAYPOINTS WITH MAX DISTANCE FIRST
//                    for (int i = 0; i < removeNodesCount; i++) {
//                        int maxDistancePointIndex = 1;
//                        for (int j = 2; j < currentWayPoints.size(); j++) {
//                            if (currentWayPoints.get(j))
//                        }
//                    }

                    for (int i = 0; i < removeNodesCount; i++) {
                        addToErasedPoints(currentWayPoints, erasedPoints);
                    }
                    FindStats.increment(FindStats.TOO_BIG);
                } else if (response.distance() / response.wayPoints().size() > kmPerOneNode) {
                    LOGGER.info("distance: {} has only {} nodes. Try to add more nodes",
                        response.distance(), response.wayPoints().size());
                    addToCurrentPoints(currentWayPoints, erasedPoints);
                    FindStats.increment(FindStats.NOT_ENOUGH_NODES);
                } else if (hasPointViaStartWaypoint(response.coordinates())) {
                    LOGGER.info("route has points via Start, try to erase a point");
                    // TODO: more longer path can be not via Start point. Can we just add points here sometimes?
                    addToErasedPoints(currentWayPoints, erasedPoints);
                    FindStats.increment(FindStats.HAS_START_POINT);
                    // don't check cycle if we made a lot if iterations already
                } else if (Utils.percent(iteration, MAX_ITERATIONS) < 80 && hasACycle(response.coordinates())) {
                    LOGGER.info("route has a Cycle");
                    addToErasedPoints(currentWayPoints, erasedPoints);
                    FindStats.increment(FindStats.HAS_A_CYCLE);
                } else if (routeIsDuplicate(response)) {
                    LOGGER.info("route is a duplicate. Clear up everything");
                    while (currentWayPoints.size() > 1) {
                        addToErasedPoints(currentWayPoints, erasedPoints);
                    }
                    FindStats.increment(FindStats.DUPLICATE);
                } else {
                    if (!completed.compareAndSet(false, true)) {
                        return null;
                    }
                    response = addWaypointsAsManyAsPossible(
                        maxDistance, currentWayPoints, erasedPoints, originalKmPerOneNode, response);
                    LOGGER.info("Found a route with: {} waypoints!", response.wayPoints().size());
                    return response.withKmPerOneNode(kmPerOneNode);
                }
                iterationStats.pushTiming(System.currentTimeMillis() - startIterationTime);
                if (iteration % 50 == 0) {
                    FindStats.printStats();
                }
                iterationStats.tryLogStats();
                //saveDebugTrack(response);
            } catch (HttpTimeoutException e) {
                LOGGER.info("Sleep for a while. got timeout from points: {}", currentWayPoints);
                try {
                    Thread.sleep(60 * 1000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            } catch (TooManyCoordinatesException e) {
                int erase = 1;
                erase += currentWayPoints.size() / 2;
                for (int i = 0; i < erase; i++) {
                    addToCurrentPoints(erasedPoints, currentWayPoints);
                }
                LOGGER.info("got TooManyCoordinatesException with length: {} erase: {}"
                    , currentWayPoints.size(), erase);
            }
        }
        return null;
    }

    void saveDebugTrack(OsrmResponse response) {
        Paths.get("debug").toFile().mkdirs();
        var gpx = GpxGenerator.generate(response.coordinates(), response.wayPoints());
        final String debugFileName = "debug/" + ((debugIndexCount++) % 50) + ".gpx";
        final Path tmpPath = Paths.get(debugFileName);
        try {
            GPX.write(gpx, tmpPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        LOGGER.info("Found waypoint close to start. Erase random point. {}", debugFileName);
    }

    // TODO: can be done in parallel
    private OsrmResponse addWaypointsAsManyAsPossible(double maxDistance,
                                                      List<WayPoint> currentWayPoints,
                                                      List<WayPoint> erasedPoints,
                                                      double kmPerOneNode,
                                                      OsrmResponse response) throws TooManyCoordinatesException {
        int maxNodesAdd = (int) (maxDistance / kmPerOneNode);
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
            .skip(1)
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
                    final OsrmResponse response = osrmAPI.generateTripBetweenTwoPoints(twoPoints);
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
        LOGGER.info("found: {} similar points from: {}", count, finishIndex - startIndex);
        // 10% of the route can go back
        if (count > (finishIndex - startIndex - eliminate_points) / 100 * 10) {
            return true;
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
