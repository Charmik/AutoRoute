package com.autoroute.logistic.rodes;

import java.util.List;

// route can contains vertex from compact & full graph. cycle contains vertexes only from compact graph
public record Route(List<Vertex> route, Cycle cycle) {



}
