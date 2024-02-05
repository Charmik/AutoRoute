package com.autoroute.osm.WIP;

import java.io.IOException;
import java.net.URL;
import java.util.Scanner;

public class OSMNodeRating {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: com.autoroute.osm.WIP.OSMNodeRating <node_url>");
            System.exit(1);
        }

        String nodeUrl = args[0];

        try {
            String nodeId = extractNodeId(nodeUrl);
            double rating = getNodeRating(nodeId);
            System.out.printf("Node %s has rating %.2f\n", nodeId, rating);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static String extractNodeId(String nodeUrl) throws Exception {
        URL url = new URL(nodeUrl);
        String path = url.getPath();
        String[] parts = path.split("/");
        if (parts.length < 2) {
            throw new Exception("Invalid node URL");
        }
        return parts[2];
    }

    private static double getNodeRating(String nodeId) throws IOException {
        String query = String.format("[out:json]; node(%s)[\"rating\"]; out;", nodeId);
        String url = "https://overpass-api.de/api/interpreter?data=" + query;
        Scanner scanner = new Scanner(new URL(url).openStream());
        String json = scanner.useDelimiter("\\A").next();
        scanner.close();
        // Extract rating tag from JSON response
        int ratingIndex = json.indexOf("\"rating\"");
        if (ratingIndex == -1) {
            throw new IOException("Rating not found for node " + nodeId);
        }
        int ratingEndIndex = json.indexOf(",", ratingIndex);
        String ratingString = json.substring(ratingIndex, ratingEndIndex);
        double rating = Double.parseDouble(ratingString.split(":")[1].trim());
        return rating;
    }
}
