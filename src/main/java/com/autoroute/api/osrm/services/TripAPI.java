package com.autoroute.api.osrm.services;

import com.autoroute.osm.LatLon;
import com.autoroute.osm.WayPoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * @see <a href="http://project-osrm.org/docs/v5.7.0/api/#trip-service">orsm trip API documentation</a>
 */
public class TripAPI {

    private static final Logger LOGGER = LogManager.getLogger(TripAPI.class);

    private final HttpClient client;
    private final Cache cache;

    public TripAPI() {
        this.client = HttpClient.newHttpClient();
        this.cache = new Cache();
        this.cache.loadCache();
    }

    public OsrmResponse generateRoute(List<WayPoint> wayPoints) throws TooManyCoordinatesException, HttpTimeoutException {
        return callAPI("routes", wayPoints, "route", "driving", "false");
    }

    /**
     * Solves the Traveling Salesman Problem using the road network and input wayPoints.
     *
     * @param wayPoints The list containing wayPoints in the trip.
     * @return a OsrmResponse.
     */
    public OsrmResponse generateTrip(List<WayPoint> wayPoints, boolean roundTrip) throws TooManyCoordinatesException, HttpTimeoutException {
        return callAPI("trips", wayPoints, "trip", "bike", "full",
            "roundtrip=" + roundTrip, "source=first");
    }

    @NotNull
    private OsrmResponse callAPI(String name, List<WayPoint> wayPoints, String service, String profile,
                                 String overview, String... additionalParams) throws TooManyCoordinatesException, HttpTimeoutException {

        var stringCoordinates = buildStringCoordinates(wayPoints);
        String url = String.format("http://router.project-osrm.org/" + service + "/v1/" + profile +
                "/%s?geometries=geojson&overview=" + overview,
            stringCoordinates);
        for (String additionalParam : additionalParams) {
            url += "&" + additionalParam;
        }
        final OsrmResponse cacheResult = cache.getOrNull(url);
        if (cacheResult != null) {
            return new OsrmResponse(cacheResult.code(), cacheResult.distance(), cacheResult.coordinates(),
                wayPoints);
        }
        LOGGER.info("url: " + url);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(url))
                .header("accept", "application/json")
                .GET()
                .timeout(Duration.of(120, ChronoUnit.SECONDS))
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            final String body = response.body();
            try {
                var json = new JSONObject(body);
                final OsrmResponse result = getResponse(wayPoints, json, name);
                if (wayPoints.size() < 4) {
                    cache.write(url, result);
                }
                return result;
            } catch (JSONException e) {
                LOGGER.info("JSON error, request was: " + request);
                throw new TooManyCoordinatesException("couldn't parse JSON: " + body, e);
            }
        } catch (HttpTimeoutException e) {
            throw e;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Couldn't create URI from url: " + url, e);
        } catch (IOException e) {
            if (e instanceof HttpTimeoutException) {
                throw (HttpTimeoutException) e;
            }
            throw new RuntimeException("couldn't read result from request", e);
        } catch (InterruptedException e) {
            throw new RuntimeException("request was interrupted", e);
        }
    }

    @NotNull
    private static String buildStringCoordinates(List<WayPoint> points) {
        StringBuilder stringCoordinates = new StringBuilder();
        stringCoordinates.append(points.get(0).latLon().lon())
            .append(",")
            .append(points.get(0).latLon().lat()).append(";");
        for (int i = 1; i < points.size(); i++) {
            var point = points.get(i);
            stringCoordinates.append(point.latLon().lon()).append(",");
            stringCoordinates.append(point.latLon().lat());
            if (i < points.size() - 1) {
                stringCoordinates.append(";");
            }
        }
        return stringCoordinates.toString();
    }

    private OsrmResponse getResponse(List<WayPoint> wayPoints, JSONObject json, String name) {
        var code = json.getString("code");
        var tripsArray = json.getJSONArray(name);
        List<LatLon> coordinates = new ArrayList<>();
        final JSONObject trip = tripsArray
            .getJSONObject(0);
        var distance_km = trip.getDouble("distance") / 1000;
        if ("trips".equals(name)) {
            var arrayOfPoints = trip
                .getJSONObject("geometry")
                .getJSONArray("coordinates");
            for (int i = 0; i < arrayOfPoints.length(); i++) {
                var lon = arrayOfPoints.getJSONArray(i).getDouble(0);
                var lat = arrayOfPoints.getJSONArray(i).getDouble(1);
                coordinates.add(new LatLon(lat, lon));
            }
        }
        return new OsrmResponse(code, distance_km, coordinates, wayPoints);
    }

    public void flush() {
        cache.flush();
    }
}
