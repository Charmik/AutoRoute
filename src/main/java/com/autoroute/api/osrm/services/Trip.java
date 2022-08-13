package com.autoroute.api.osrm.services;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * @see <a href="http://project-osrm.org/docs/v5.7.0/api/#trip-service">orsm trip API documentation</a>
 */
public class Trip {

    private final HttpClient client;

    public Trip() {
        this.client = HttpClient.newHttpClient();
    }

    /**
     * Solves the Traveling Salesman Problem using the road network and input points.
     *
     * @param coordinates The string containing comma separated lon/lat. Multiple coordinate pairs are separated by a semicolon.
     * @return A JSON object containing the response code, an array of waypoint objects, and an array of route objects.
     */
    public JSONObject generateTrip(String coordinates, boolean roundTrip) {
        String url = String.format("http://router.project-osrm.org/trip/v1/car/%s?geometries=geojson&overview=full&roundtrip=%s",
            coordinates, roundTrip);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(url))
                .header("accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new JSONObject(response.body());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Couldn't create URI from url: " + url, e);
        } catch (IOException e) {
            throw new RuntimeException("couldn't read result from request", e);
        } catch (InterruptedException e) {
            throw new RuntimeException("request was interrupted", e);
        }
    }
}
