package com.autoroute.osm.WIP;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class AttractionFinder {

    public static void main(String[] args) throws Exception {

        // Parse input arguments
        double lat = Double.parseDouble(args[0]);
        double lon = Double.parseDouble(args[1]);
        double radius = Double.parseDouble(args[2]);

        // 34.711433 33.131185 1000
        lat = 34.711433;
        lon = 33.131185;
        radius = 1;

        // Build query URL
        String query = "https://nominatim.openstreetmap.org/search?format=json&q=";
        query += URLEncoder.encode("tourism", "UTF-8") + "&";
        query += "bounded=1&viewbox=" + (lon - radius) + "," + (lat - radius) + "," + (lon + radius) + "," + (lat + radius);

        System.out.println(query);

        // Send API request and parse response
        URL url = new URL(query);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
        String response = br.readLine();
        JSONArray attractions = new JSONArray(response);

        // Sort attractions by rating
//        attractions = sortAttractions(attractions);

        // Print results
        System.out.println("Attractions in area sorted by rating:");
        for (int i = 0; i < attractions.length(); i++) {
            JSONObject attraction = attractions.getJSONObject(i);
            String name = attraction.optString("display_name", "");
            double rating = attraction.optDouble("rating", 0.0);
            double place_rank = attraction.optDouble("place_rank", 0.0);
            System.out.println(name + " - " + rating + " - " + place_rank);
        }
    }

    private static JSONArray sortAttractions(JSONArray attractions) {
        JSONArray sorted = new JSONArray();
        for (int i = 0; i < attractions.length(); i++) {
            JSONObject attraction = attractions.getJSONObject(i);
            double rating = attraction.optDouble("rating", 0.0);
            int j = 0;
            while (j < sorted.length() && sorted.getJSONObject(j).optDouble("rating", 0.0) > rating) {
                j++;
            }
            sorted.put(j, attraction);
        }
        return sorted;
    }
}
