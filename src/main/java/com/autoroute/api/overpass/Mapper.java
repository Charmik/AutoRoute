package com.autoroute.api.overpass;

import com.autoroute.logistic.rodes.Vertex;

import java.util.List;

public class Mapper {

    public static Vertex[] mapToVertex(List<OverpassResponse> responses) {
        Vertex[] arr = new Vertex[responses.size()];
        for (int i = 0; i < responses.size(); i++) {
            arr[i] = new Vertex(
                i,
                responses.get(i).id(),
                responses.get(i).latLon());
        }
        return arr;
    }

}
