package com.autoroute.gpx;

import com.autoroute.logistic.rodes.Cycle;
import com.autoroute.logistic.rodes.Vertex;
import com.autoroute.osm.LatLon;
import com.autoroute.utils.Utils;
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
        run("o/route_19.gpx");
        run("o/route_20.gpx");


    }

    private static void run(String name) throws IOException {
        final GPX gpx = GPX.read(Paths.get(name));
        final Track track = gpx.tracks().toList().get(0);
        final TrackSegment segment = track.getSegments().get(0);
        final List<WayPoint> points = segment.getPoints();
        System.out.println(points);
        List<Vertex> vertices = new ArrayList<>();

        for (int i = 0; i < points.size(); i++) {
            final WayPoint p = points.get(i);
            LatLon ll = new LatLon(p.getLatitude().doubleValue(), p.getLongitude().doubleValue());
            Vertex v = new Vertex(1, 1, ll);
            if (i > 0) {
                vertices.get(i - 1).addNeighbor(v);
                v.addNeighbor(vertices.get(i - 1));
            }
            vertices.add(v);
        }
        Utils.writeGPX(vertices, "QQQ", 666);
        final Cycle cycle = new Cycle(vertices);
        System.out.println(cycle.isGood());
    }
}
