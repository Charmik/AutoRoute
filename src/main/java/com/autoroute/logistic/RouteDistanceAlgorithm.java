package com.autoroute.logistic;

import com.autoroute.Constants;
import com.autoroute.api.osrm.services.OsrmResponse;
import com.autoroute.api.osrm.services.TooManyCoordinatesException;
import com.autoroute.api.osrm.services.TripAPI;
import com.autoroute.gpx.Duplicate;
import com.autoroute.gpx.GpxGenerator;
import com.autoroute.osm.LatLon;
import com.autoroute.osm.WayPoint;
import io.jenetics.jpx.GPX;

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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RouteDistanceAlgorithm {

    private static final int MAX_ITERATIONS = 50_000;
    private static final double KM_PER_ONE_NODE = 20;

    private final TripAPI tripAPI;
    private final Duplicate duplicate;
    private int debugIndexCount = 0;

    public RouteDistanceAlgorithm(Duplicate duplicate) {
        this.tripAPI = new TripAPI();
        this.duplicate = duplicate;
    }

    /**
     * Build a route with the given distance restrictions via the given way points.
     *
     * @param minDistance       the minimum distance of the trip.
     * @param maxDistance       the maximum distance of the trip.
     * @param originalWayPoints [0] is a start point, we can't erase it.
     */
    public OsrmResponse buildRoute(double minDistance,
                                   double maxDistance,
                                   final List<WayPoint> originalWayPoints,
                                   PointVisiter pointVisiter) {
        if (originalWayPoints.size() == 1) {
            throw new IllegalArgumentException("can't build route without way points");
        }
        List<WayPoint> erasedPoints = new ArrayList<>();
        List<WayPoint> currentWayPoints = filterWayPoints(maxDistance, originalWayPoints, pointVisiter);

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            try {
                if (currentWayPoints.size() == 1) {
//                    System.out.println("Add a new waypoint because we have only start point");
                    if (erasedPoints.isEmpty()) {
                        System.out.println("don't have nodes to erase. Exit");
                        return null;
                    }
                    eraseAndAddRandomPoint(currentWayPoints, erasedPoints);
                }
                System.out.println("Start generate trip from: " +
                    currentWayPoints.size() + " removed: " + erasedPoints.size());
                OsrmResponse response = tripAPI.generateTrip(currentWayPoints, true);
                if (response.distance() < minDistance || currentWayPoints.size() == 1) {
                    System.out.println("got too small distance: " + response.distance() + ", add 1 node");
                    if (erasedPoints.isEmpty()) {
                        throw new IllegalArgumentException("can't build a trip with given waypoints");
                    }
                    eraseAndAddRandomPoint(currentWayPoints, erasedPoints);
                    FindStats.increment(FindStats.TOO_SMALL);
                } else if (response.distance() > maxDistance) {
                    int diffDistance = (int) (response.distance() - maxDistance);
                    int removeNodesCount = ((int) (diffDistance / KM_PER_ONE_NODE)) + 1;
                    if (removeNodesCount > currentWayPoints.size()) {
                        removeNodesCount = (currentWayPoints.size() / 2) + 1;
                    }
                    if (currentWayPoints.size() < 5) {
                        removeNodesCount = 1;
                    }
                    System.out.println("got too big distance: " + response.distance() +
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
                } else if (response.distance() / response.wayPoints().size() > KM_PER_ONE_NODE) {
                    System.out.println("distance: " + response.distance() +
                        " has only " + response.wayPoints().size() + " nodes. Try to add more nodes");
                    eraseAndAddRandomPoint(currentWayPoints, erasedPoints);
                    FindStats.increment(FindStats.NOT_ENOUGH_NODES);
                } else if (hasPointViaStartWaypoint(response.coordinates())) {
                    System.out.println("route has points via Start");
                    if (currentWayPoints.size() > 2) {
                        eraseAndAddRandomPoint(erasedPoints, currentWayPoints);
                    } else {
                        System.out.println("We have only 1 point, so route to there and back. don't remove this point");
                        eraseAndAddRandomPoint(currentWayPoints, erasedPoints);
                    }
                    FindStats.increment(FindStats.HAS_START_POINT);
//                } else if (hasACycle(response.coordinates())) {
//                    System.out.println("route has a Cycle");
//                    eraseAndAddRandomPoint(erasedPoints, currentWayPoints);
//                    FindStats.increment(FindStats.HAS_A_CYCLE);
                } else if (routeIsDuplicate(response)) {
                    System.out.println("route is a duplicate. Clear up everything");
                    while (currentWayPoints.size() > 1) {
                        eraseAndAddRandomPoint(erasedPoints, currentWayPoints);
                    }
                    FindStats.increment(FindStats.DUPLICATE);
                } else {
                    response = addWaypointsAsManyAsPossible(
                        maxDistance, currentWayPoints, erasedPoints, response);
                    System.out.println("Found a route with: " + response.wayPoints().size() + " waypoints!");
                    return response;
                }
//                saveDebugTrack(response);
                if (iteration % 10 == 0) {
                    FindStats.printStats();
                }
            } catch (HttpTimeoutException e) {
                System.out.println("Sleep for a while. got timeout from points: " + currentWayPoints);
                try {
                    Thread.sleep(60 * 1000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            } catch (TooManyCoordinatesException e) {
                System.out.println("got TooManyCoordinatesException with length: " + currentWayPoints.size());
                int erase = 1;
                erase += currentWayPoints.size() / 3;
                for (int i = 0; i < erase; i++) {
                    eraseAndAddRandomPoint(erasedPoints, currentWayPoints);
                }
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
        System.out.println("Found waypoint close to start. Erase random point. " + debugFileName);
    }

    private OsrmResponse addWaypointsAsManyAsPossible(double maxDistance,
                                                      List<WayPoint> currentWayPoints,
                                                      List<WayPoint> erasedPoints,
                                                      OsrmResponse response) throws TooManyCoordinatesException {
        int maxNodesAdd = (int) (2 * maxDistance / KM_PER_ONE_NODE);
        System.out.println("try to add waypoints to the route with distance: " +
            response.distance() + " maxNodes. Can add more: " + maxNodesAdd);

        var erasedShuffle = new ArrayList<>(erasedPoints);
        Collections.shuffle(erasedShuffle);
        int count = 0;
        for (WayPoint erasedPoint : erasedPoints) {

            currentWayPoints.add(erasedPoint);

            try {
                OsrmResponse newResponse = tripAPI.generateTrip(currentWayPoints, true);
                var distanceDiff = newResponse.distance() - response.distance();
                assert distanceDiff >= 0;
                if (newResponse.distance() < maxDistance && (distanceDiff < maxDistance / 100 * 2)) {
                    response = newResponse;
                    System.out.println("added waypoint to the route, new distance: " + newResponse.distance());
                    count++;
                    if (response.distance() > maxDistance / 100 * 95) {
                        System.out.println("got enough distance, so break adding waypoints");
                        break;
                    }
                    if (count == maxNodesAdd) {
                        System.out.println("Added enough waypoints: " + response.wayPoints().size());
                        break;
                    }
                } else {
                    currentWayPoints.remove(currentWayPoints.size() - 1);
                }
            } catch (HttpTimeoutException e) {
                System.out.println("Sleep for a while. got timeout from points: " + currentWayPoints);
                try {
                    Thread.sleep(60 * 1000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }

        }
        return response;
    }

    private boolean routeIsDuplicate(OsrmResponse response) {
//        final List<LatLon> coordinates = response.coordinates();
//        if (duplicate.hasDuplicateInFiles(coordinates)) {
//            return true;
//        }
        return false;
    }

    private List<WayPoint> filterWayPoints(double maxDistance, List<WayPoint> originalWayPoints, PointVisiter pointVisiter) {
        System.out.println("Start filtering waypoints");
        var filteredPoints = new ArrayList<>(originalWayPoints.stream()
            .skip(1)
            .filter(point -> !pointVisiter.isVisited(Constants.DEFAULT_USER, point))
            .toList());
        filteredPoints.add(0, originalWayPoints.get(0));

        final ExecutorService threadPool = Executors.newFixedThreadPool(5);
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

                    System.out.println(Thread.currentThread().getId() + " got trip " +
                        count.addAndGet(1) + "/" + filteredPoints.size());
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
            var futures = threadPool.invokeAll(callables);
            threadPool.shutdown();
            while (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                System.out.println("waiting for termination");
            }
            for (Future<OsrmResponse> future : futures) {
                var response = future.get();
                // should be 2, but we add 0.5 more for other nodes
                if (response.distance() * 2.5 < maxDistance) { // need the way back
                    result.add(response.wayPoints().get(1));
                    System.out.println(Thread.currentThread().getId() + " add current points, distance: " + response.distance());
                } else {
                    System.out.println(Thread.currentThread().getId() + " remove current point, distance: " + response.distance());
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Removed: " + (filteredPoints.size() - result.size()) + " nodes. Got: " + result.size() + " nodes");
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
            if (points.get(i).IsCloseInCity(startPoint)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasACycle(List<LatLon> points) {
        int startIndex = (int) (((double) points.size()) / 100 * 20);
        int finishIndex = (int) (((double) points.size()) / 100 * 80);

        int count = 0;
        for (int i = startIndex; i <= finishIndex; i++) {
            for (int j = startIndex; j <= finishIndex; j++) {
                if (Math.abs(i - j) < 500) {
                    continue;
                }
                if (points.get(i).IsClosePoint(points.get(j))) {
                    count++;
                    break;
                }
            }
        }
        System.out.println("found: " + count + " similar points from: " + (finishIndex - startIndex));
        // 15% of the route can go back
        if (count > (finishIndex - startIndex) / 100 * 15) {
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

        public static void increment(FindStats stat) {
            stats.compute(stat, (k, v) -> {
                if (v == null) {
                    return 1;
                }
                return v + 1;
            });
        }

        public static void printStats() {
            final FindStats[] values = values();
            for (FindStats value : values) {
                System.out.println(value + " " + stats.get(value));
            }
        }
    }
}
