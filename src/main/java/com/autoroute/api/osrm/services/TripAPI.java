package com.autoroute.api.osrm.services;

import com.autoroute.osm.LatLon;
import com.autoroute.osm.WayPoint;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * @see <a href="http://project-osrm.org/docs/v5.7.0/api/#trip-service">orsm trip API documentation</a>
 */
public class TripAPI {

    private final HttpClient client;

    public TripAPI() {
        this.client = HttpClient.newHttpClient();
    }

    /**
     * Solves the Traveling Salesman Problem using the road network and input points.
     *
     * @param points The list containing points in the trip.
     * @return A JSON object containing the response code, an array of waypoint objects, and an array of route objects.
     */
    public OsrmResponse generateTrip(List<WayPoint> points, boolean roundTrip) {
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
        String url = String.format("http://router.project-osrm.org/trip/v1/car/%s?geometries=geojson&overview=full&roundtrip=%s&source=first",
            stringCoordinates, roundTrip);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(url))
                .header("accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            var json = new JSONObject(response.body());
            return getResponseFromJson(json);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Couldn't create URI from url: " + url, e);
        } catch (IOException e) {
            throw new RuntimeException("couldn't read result from request", e);
        } catch (InterruptedException e) {
            throw new RuntimeException("request was interrupted", e);
        }
    }

    private OsrmResponse getResponseFromJson(JSONObject json) {
        try {
            Files.write(Paths.get("out.json"), json.toString().getBytes());
        } catch (IOException e) {
        }

        var code = json.getString("code");
        var tripsArray = json.getJSONArray("trips");
        List<LatLon> coordinates = new ArrayList<>();
        final JSONObject trip = tripsArray
            .getJSONObject(0);
        var distance_km = trip.getDouble("distance") / 1000;
        var arrayOfPoints = trip
            .getJSONObject("geometry")
            .getJSONArray("coordinates");
        for (int i = 0; i < arrayOfPoints.length(); i++) {
            var lon = arrayOfPoints.getJSONArray(i).getDouble(0);
            var lat = arrayOfPoints.getJSONArray(i).getDouble(1);
            coordinates.add(new LatLon(lat, lon));
        }
        return new OsrmResponse(code, distance_km, coordinates);
    }
}
