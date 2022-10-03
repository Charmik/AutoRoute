package com.autoroute.logistic.rodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record Cycle(List<Vertex> route, Vertex start, Vertex finish) {

    private static final Cycle empty = new Cycle(Collections.emptyList(), null, null);

    public static Cycle empty() {
        return empty;
    }

    public List<Vertex> getCycleVertexes() {
        boolean cycleStarted = false;
        List<Vertex> vertices = new ArrayList<>();
        for (Vertex v : route) {
            if (v.equals(start)) {
                cycleStarted = true;
            }
            if (cycleStarted) {
                vertices.add(v);
            }
            if (v.equals(finish)) {
                break;
            }
        }
        return vertices;
    }
}
