package com.autoroute.logistic.rodes;

import com.autoroute.logistic.rodes.dijkstra.DijkstraAlgorithm;
import com.autoroute.osm.LatLon;

import java.util.ArrayList;
import java.util.List;

public class Test {

    public static void main(String[] args) {
        List<Vertex> l = new ArrayList<>();
        final Vertex v1 = new Vertex(0, 0, new LatLon(1, 1));
        final Vertex v2 = new Vertex(1, 1, new LatLon(2, 2));
        final Vertex v3 = new Vertex(2, 2, new LatLon(3, 3));
        final Vertex v4 = new Vertex(3, 3, new LatLon(4, 4));
        add(v1, v2);
        add(v2, v3);
        add(v3, v4);
        add(v1, v4);

//        final Vertex v5 = new Vertex(1, 1, new LatLon(1, 1));
//        final Vertex v6 = new Vertex(1, 1, new LatLon(1, 1));
        l.add(v1);
        l.add(v2);
        l.add(v3);
        l.add(v4);

        Graph g = new Graph(l, 10, 100);
        List<Cycle> cycles = new ArrayList<>();
        final DijkstraAlgorithm alg = new DijkstraAlgorithm(g, v1);
        alg.run();
        g.findAllCycles(v1, cycles, alg);
        System.out.println(cycles);
    }

    public static void add(Vertex v1, Vertex v2) {
        v1.addNeighbor(v2);
        v2.addNeighbor(v1);
    }
}
