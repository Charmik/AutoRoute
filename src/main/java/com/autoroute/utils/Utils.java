package com.autoroute.utils;

import com.autoroute.api.overpass.Node;
import com.autoroute.api.overpass.OverpassResponse;
import com.autoroute.api.overpass.Way;
import com.autoroute.gpx.GpxGenerator;
import com.autoroute.logistic.rodes.Vertex;
import com.autoroute.osm.LatLon;
import io.jenetics.jpx.GPX;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

public class Utils {

    public static Integer parseInteger(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static double percent(double part, double max) {
        if (part < 0 || max < 0 || part > max) {
            throw new IllegalArgumentException("wrong arguments for percentage: " + part + " " + max);
        }
        return part / (max / 100);
    }

    public static Path pathForRoute(LatLon startPoint, int minDistance, int maxDistance) {
        String str = minDistance + "_" + maxDistance + "_" + startPoint;
        return Paths.get("tracks").resolve(str);
    }

    public static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }

    public static void writeGPX(List<Vertex> vertices, int index) {

        try {
            GPX.write(GpxGenerator.generate(vertices),
                Paths.get("o/ZZZ_" + index + ".gpx"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeGPX(List<Vertex> vertices, String prefix, int index) {
        final GPX cycleGPX = GpxGenerator.generateRoute(vertices);
        try {
            GPX.write(cycleGPX,
                Paths.get("o/" + prefix + index + ".gpx"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeVertecesToFile(OverpassResponse r) {
        StringBuilder sb = new StringBuilder();
        final List<Node> nodes = r.getNodes();
        sb.append(nodes.size()).append("\n");
        for (Node n : nodes) {
            sb
                .append(n.id())
                .append(" ")
                .append(n.latLon().lat())
                .append(" ")
                .append(n.latLon().lon())
                .append("\n");
        }
        final List<Way> ways = r.getWays();
        sb.append(ways.size()).append("\n");
        for (Way way : ways) {
            sb
                .append(way.hashCode())
                .append(" ");
            for (long x : way.nodesIds()) {
                sb.append(x).append(" ");
            }
            sb.append("\n");
        }
        try {
            Files.writeString(Paths.get("graph.txt"), sb.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("wrote graph to the file");
    }

    public static OverpassResponse readVertices() {
        OverpassResponse r = new OverpassResponse();
//        final List<String> lines = Files.readAllLines(Paths.get("tmp/bor_15.txt"));
//        final List<String> lines = Files.readAllLines(Paths.get("tmp/bor_150.txt"));
        final List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get("tmp/limassol_150.txt"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int nodesCount = Integer.parseInt(lines.get(0));
        for (int i = 1; i <= nodesCount; i++) {
            String str = lines.get(i);
            final String[] strs = str.split(" ");
            assert strs.length == 3;
            long id = Long.parseLong(strs[0]);
            double lat = Double.parseDouble(strs[1]);
            double lon = Double.parseDouble(strs[2]);
            var latlon = new LatLon(lat, lon);
            r.add(new Node(id, Collections.emptyMap(), latlon));
        }

        int waysCount = Integer.parseInt(lines.get(nodesCount + 1));
        for (int i = nodesCount + 2; i < lines.size(); i++) {
            String str = lines.get(i);
            final String[] strs = str.split(" ");
            long id = Long.parseLong(strs[0]);
            long[] ids = new long[strs.length - 1];
            for (int j = 1; j < strs.length; j++) {
                ids[j - 1] = Long.parseLong(strs[j]);
            }
            final Way way = new Way(id, ids, Collections.emptyMap());
            r.add(way);
        }
        assert r.getWays().size() == waysCount;
        return r;
    }
}
