package com.autoroute.logistic.rodes;

import com.autoroute.sight.Sight;

import java.util.Collections;
import java.util.List;
import java.util.Set;

// route can contains vertex from compact & full graph. cycle contains vertexes only from compact graph
public record Route(List<Vertex> route, Set<Sight> sights) {

    public Route(List<Vertex> route) {
        this(route, Collections.emptySet());
    }

    public int size() {
        return route.size();
    }

    public int getIndexByVertex(Vertex v) {
        for (int i = 0; i < route.size(); i++) {
            if (route.get(i).getIdentificator() == v.getIdentificator()) {
                return i;
            }
        }
        throw new IllegalStateException();
    }
}
