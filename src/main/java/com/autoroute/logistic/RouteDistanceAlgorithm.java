package com.autoroute.logistic;

import com.autoroute.api.osrm.services.OsrmResponse;
import com.autoroute.api.osrm.services.TooManyCoordinatesException;
import com.autoroute.api.osrm.services.TripAPI;
import com.autoroute.osm.WayPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RouteDistanceAlgorithm {

    private static final int MAX_ITERATIONS = 100;
    private static final double NODES_PER_10_KM = 0.5;

    private final TripAPI tripAPI;

    public RouteDistanceAlgorithm() {
        this.tripAPI = new TripAPI();
    }

    /**
     * Build a route with the given distance restrictions via the given way points.
     *
     * @param minDistance       the minimum distance of the trip.
     * @param maxDistance       the maximum distance of the trip.
     * @param originalWayPoints [0] is a start point, we can't erase it.
     */
    public OsrmResponse buildRoute(double minDistance, double maxDistance, final List<WayPoint> originalWayPoints) {
        List<WayPoint> erasedPoints = new ArrayList<>();
        List<WayPoint> currentWayPoints = new ArrayList<>(originalWayPoints);
        int iteration = 0;
        while (iteration < MAX_ITERATIONS) {
            try {
                if (currentWayPoints.size() == 1) {
                    System.out.println("Add a new waypoint because we have only start point");
                    eraseAndAddRandomPoint(currentWayPoints, erasedPoints);
                }
                OsrmResponse response = tripAPI.generateTrip(currentWayPoints, true);
                if (response.distance() < minDistance || currentWayPoints.size() == 1) {
                    System.out.println("got too small distance: " + response.distance() + ", add 1 node");
                    if (erasedPoints.isEmpty()) {
                        throw new IllegalArgumentException("can't build a trip with given waypoints");
                    }
                    eraseAndAddRandomPoint(currentWayPoints, erasedPoints);
                } else if (response.distance() > maxDistance) {

                    int diffDistance = (int) (response.distance() - maxDistance);
                    final int removeNodesCount = ((int) (diffDistance / 10 * NODES_PER_10_KM)) + 1;
                    System.out.println("got too big distance: " + response.distance() + ", remove: "
                        + removeNodesCount + " nodes");
                    for (int i = 0; i < removeNodesCount; i++) { // 10 km for one point
                        eraseAndAddRandomPoint(erasedPoints, currentWayPoints);
                    }
                } else {
                    if (response.distance() / response.wayPoints().size() > 10d / NODES_PER_10_KM) {
                        System.out.println("distance: " + response.distance() +
                            " has only " + response.wayPoints().size() + " nodes");
                        eraseAndAddRandomPoint(erasedPoints, currentWayPoints);
                        continue;
                    }
                    return response;
                }
            } catch (TooManyCoordinatesException e) {
                System.out.println("got TooManyCoordinatesException with length: " + currentWayPoints.size());
                int erase = 1;
                erase += currentWayPoints.size() / 3;
                for (int i = 0; i < erase; i++) {
                    eraseAndAddRandomPoint(erasedPoints, currentWayPoints);
                }
                iteration--; // don't use try for TooManyCoordinatesException
            }
            iteration++;
        }
        throw new IllegalStateException(
            "couldn't build a route with given wayPoints for: " + MAX_ITERATIONS + " iterations");
    }

    private void eraseAndAddRandomPoint(List<WayPoint> addList, List<WayPoint> removeList) {
        if (removeList.size() == 1) {
            return;
        }
        var erasedWayPoint = getRandomAndRemoveFromList(removeList);
        addList.add(erasedWayPoint);
    }

    private WayPoint getRandomAndRemoveFromList(List<WayPoint> points) {
        var index = ThreadLocalRandom.current().nextInt(points.size() - 1) + 1;
        var res = points.get(index);
        points.remove(index);
        return res;
    }
}
