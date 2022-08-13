package com.autoroute.logistic;

import com.autoroute.api.osrm.services.OsrmResponse;
import com.autoroute.api.osrm.services.TripAPI;
import com.autoroute.osm.WayPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RouteDistanceAlgorithm {

    private static final int MAX_ITERATIONS = 15;
    TripAPI tripAPI;

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
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            var response = tripAPI.generateTrip(currentWayPoints, true);
            if (response.distance() < minDistance) {
                if (erasedPoints.isEmpty()) {
                    throw new IllegalArgumentException("can't build a trip with given waypoints");
                }
                var newWayPoint = getRandomAndRemoveFromList(erasedPoints);
                currentWayPoints.add(newWayPoint);
            } else if (response.distance() > maxDistance) {
                var erasedWayPoint = getRandomAndRemoveFromList(currentWayPoints);
                erasedPoints.add(erasedWayPoint);
            } else {
                return response;
            }
        }
        throw new IllegalStateException(
            "couldn't build a route with given wayPoints for: " + MAX_ITERATIONS + " iterations");
    }

    private WayPoint getRandomAndRemoveFromList(List<WayPoint> points) {
        var index = ThreadLocalRandom.current().nextInt(points.size() - 1) + 1;
        var res = points.get(index);
        points.remove(index);
        return res;
    }
}
