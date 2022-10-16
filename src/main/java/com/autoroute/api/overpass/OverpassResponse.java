package com.autoroute.api.overpass;

import java.util.ArrayList;
import java.util.List;

public class OverpassResponse {

    private final List<Node> nodes;
    private final List<Way> ways;

    public OverpassResponse() {
        this.nodes = new ArrayList<>();
        this.ways = new ArrayList<>();
    }

    public void add(Node node) {
        nodes.add(node);
    }

    public void add(Way way) {
        ways.add(way);
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public List<Way> getWays() {
        return ways;
    }
}

