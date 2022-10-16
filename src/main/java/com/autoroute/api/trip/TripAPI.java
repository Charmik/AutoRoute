package com.autoroute.api.trip;

import com.autoroute.api.trip.services.OsrmResponse;
import com.autoroute.api.trip.services.TooManyCoordinatesException;
import com.autoroute.osm.WayPoint;

import java.net.http.HttpTimeoutException;
import java.util.List;

public interface TripAPI {

    OsrmResponse generateRoute(List<WayPoint> wayPoints)
        throws TooManyCoordinatesException, HttpTimeoutException;

    OsrmResponse generateTrip(List<WayPoint> wayPoints)
        throws TooManyCoordinatesException, HttpTimeoutException;

}
