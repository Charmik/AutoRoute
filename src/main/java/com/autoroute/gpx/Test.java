package com.autoroute.gpx;

import com.autoroute.logistic.rodes.Cycle;
import com.autoroute.logistic.rodes.Vertex;
import com.autoroute.logistic.LatLon;
import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Test {

    public static void main(String[] args) throws IOException {
        final List<Vertex> v1 = run("o/cycles/2_6.gpx");
        Cycle c1 = new Cycle(v1);
        final List<Vertex> v2 = run("o/cycles/2_7.gpx");
        Cycle c2 = new Cycle(v2);

        final boolean duplicate1 = Cycle.hasDuplicate(c2.getVertices(), List.of(c1));
        final boolean duplicate2 = Cycle.hasDuplicate(c1.getVertices(), List.of(c2));
        System.out.println("duplicate1: " + duplicate1);
        System.out.println("duplicate2: " + duplicate2);
    }

    private static List<Vertex> run(String name) throws IOException {
        final GPX gpx = GPX.read(Paths.get(name));
        final Track track = gpx.tracks().toList().get(0);
        final TrackSegment segment = track.getSegments().get(0);
        final List<WayPoint> points = segment.getPoints();
        System.out.println(points);
        List<Vertex> vertices = new ArrayList<>();

        for (int i = 0; i < points.size(); i++) {
            final WayPoint p = points.get(i);
            LatLon ll = new LatLon(p.getLatitude().doubleValue(), p.getLongitude().doubleValue());
            Vertex v = new Vertex(1, 1, ll, null);
            if (i > 0) {
                vertices.get(i - 1).addNeighbor(v);
                v.addNeighbor(vertices.get(i - 1));
            }
            vertices.add(v);
        }
        return vertices;
    }
}
