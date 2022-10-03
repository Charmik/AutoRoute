package com.autoroute.logistic.rodes;

import com.autoroute.Constants;
import com.autoroute.api.overpass.Box;
import com.autoroute.api.overpass.OverPassAPI;
import com.autoroute.gpx.GpxGenerator;
import com.autoroute.osm.LatLon;
import io.jenetics.jpx.GPX;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class GraphMain {

    private static final int MIN_D = 50;
    private static final int MAX_D = 100;

    public static void main(String[] args) throws IOException {
        final int MAX_KM = 50;
        final double DIFF_DEGREE = ((double) MAX_KM / Constants.KM_IN_ONE_DEGREE) / 2;

        final LatLon bor = new LatLon(59.908977, 29.068520);
        final Box box = new Box(
            bor.lat() - DIFF_DEGREE,
            bor.lon() - DIFF_DEGREE,
            bor.lat() + DIFF_DEGREE,
            bor.lon() + DIFF_DEGREE
        ); // bor

        final OverPassAPI overPassAPI = new OverPassAPI();

//        final List<OverpassResponse> nodes = overPassAPI.getNodesInBoxByTags(box, tagsReader.getTags());
//        System.out.println("nodes: " + nodes.size());
//        for (OverpassResponse node : nodes) {
//            System.out.println(node.latLon() + " " + node.tags());
//        }


        var s1 = System.currentTimeMillis();
        var vertices = readVertices();
        var time1 = (System.currentTimeMillis() - s1) / 1000;
        System.out.println("readVertices time: " + time1 + " seconds");

        final int wasSize = vertices.size();
        System.out.println("have: " + wasSize + " vertexes");

//        final List<OverpassResponse> roads =
//            overPassAPI.getRodes(new com.autoroute.osm.LatLon(59.908977, 29.068520), 100000);
//        System.out.println("size: " + roads.size());
//        final Vertex[] vertices = Mapper.mapToVertex(roads);
//        writeVertecesToFile(vertices);

        vertices = vertices.stream()
            .filter(v -> LatLon.distanceKM(v.getLatLon(), bor) < MAX_KM / 2).toList();

        Graph g = new Graph(vertices);
        var s2 = System.currentTimeMillis();
        double removeMaxDistance = 1;
        g.eliminatesNodes(removeMaxDistance);
        g.eliminatesNodesSlow(removeMaxDistance);

//        g.buildFast(0.5);
        g.buildSlow(5);
        g.mergeNeighbours(3, 0.10);
        GPX gpx = GpxGenerator.generate(g.getVertices());
        GPX.write(gpx, Paths.get("cycle_graph.gpx"));

        //        System.out.println("were size: " + wasSize);
//        System.out.println("now size: " + g.getVertices().size());

//        for (int i = 0; i < 10; i++) {
//            g.buildFast(7.5);
//        }

        int cycleCount = 0;
        for (; ; ) {
            var borVertex = g.findNearestVertex(bor);
            final Cycle cycle = g.dfs(borVertex);
            final List<Vertex> cycleRoute = cycle.route();
            double dist = 0;
            for (int i = 0; i < cycleRoute.size() - 1; i++) {
                dist += LatLon.distanceKM(cycleRoute.get(i).getLatLon(), cycleRoute.get(i + 1).getLatLon());
            }
            if (!cycleRoute.isEmpty()) {
                System.out.println("dist cycle: " + dist);
            }

            if (!cycleRoute.isEmpty()) {
                System.out.println("FOUND CYCLE DEPTH:\n");
//                for (int i = 0; i < cycle.size(); i++) {
//                    Vertex k = cycle.get(i);
//                    System.out.println(k.getLatLon().lat() + "," + k.getLatLon().lon() + ",red,circle,\"q" + i + "\"");
////                        System.out.println(k.getLatLon().lon() + "," + k.getLatLon().lat());
//                }
                gpx = GpxGenerator.generateRoute(cycleRoute);
                GPX.write(gpx, Paths.get("cycle" + (cycleCount++) + ".gpx"));
                break;
//                final List<Vertex> cycleVertexes = cycle.getCycleVertexes();
//                g.removeNearVertexes(cycleVertexes);
            }
        }


    }

    private static void writeVertecesToFile(Vertex[] vertices) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Vertex v : vertices) {
            sb
                .append(v.getId())
                .append(" ")
                .append(v.getIdentificator())
                .append(" ")
                .append(v.getLatLon().lat())
                .append(" ")
                .append(v.getLatLon().lon())
                .append("\n");
        }
        Files.writeString(Paths.get("graph.txt"), sb.toString());
        System.out.println("wrote graph to the file");
    }

    private static List<Vertex> readVertices() throws IOException {
        // graph_10000_3167.txt -> 186
//        List<Vertex> list = Files.readAllLines(Paths.get("tmp/graph_10000_3167.txt"))
        List<Vertex> list = Files.readAllLines(Paths.get("tmp/graph_50000_49847.txt"))
//        List<Vertex> list = Files.readAllLines(Paths.get("tmp/graph_100000_346811.txt"))
            .stream()
            .map(str -> {
                final String[] strs = str.split(" ");
                int id = Integer.parseInt(strs[0]);
                long identificator = Long.parseLong(strs[1]);
                double lat = Double.parseDouble(strs[2]);
                double lon = Double.parseDouble(strs[3]);
                var latlon = new LatLon(lat, lon);
                return new Vertex(id, identificator, latlon);
            })
            .toList();
        return list;
    }

}
