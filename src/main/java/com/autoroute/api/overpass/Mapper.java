package com.autoroute.api.overpass;

import com.autoroute.logistic.rodes.Graph;
import com.autoroute.logistic.rodes.Vertex;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class Mapper {

    private static final Logger LOGGER = LogManager.getLogger(Mapper.class);

    private static LongArraySet getOrCreateSet(Long2ObjectOpenHashMap<LongArraySet> m, long nodeId) {
        var neighbours = m.get(nodeId);
        if (neighbours == null) {
            neighbours = new LongArraySet();
        }
        return neighbours;
    }

    public static Graph mapToGraph(OverpassResponse r, int minDistance, int maxDistance) {
        List<Vertex> vertices = getVertices(r);
        LOGGER.info("Start build graph");
        return new Graph(vertices, minDistance, maxDistance);
    }

    @NotNull
    private static List<Vertex> getVertices(OverpassResponse r) {
        final List<Node> nodes = r.getNodes();
        final List<Way> ways = r.getWays();

        Long2ObjectOpenHashMap<LongArraySet> nodeIdToNeighbors = new Long2ObjectOpenHashMap<>();
        for (int i = 0; i < ways.size(); i++) {
            Way w = ways.get(i);
            final long[] nodeIds = w.nodesIds();

            for (int j = 0; j < nodeIds.length; j++) {
                final long nodeId = nodeIds[j];
                var neighbours = getOrCreateSet(nodeIdToNeighbors, nodeId);
                if (j > 0) {
                    assert nodeId != nodeIds[j - 1];
                    neighbours.add(nodeIds[j - 1]);
                }
                if (j < nodeIds.length - 1) {
                    assert nodeId != nodeIds[j + 1];
                    neighbours.add(nodeIds[j + 1]);
                }
                nodeIdToNeighbors.put(nodeId, neighbours);
            }
        }
        nodeIdToNeighbors.trim();
        LOGGER.info("Processed all ways");


        Long2ObjectOpenHashMap<Node> idToNode = new Long2ObjectOpenHashMap<>();
        for (Node node : nodes) {
            idToNode.put(node.id(), node);
        }
        idToNode.trim();

        Long2ObjectOpenHashMap<Vertex> idToVertex = new Long2ObjectOpenHashMap<>();
        int index = 0;
        for (var entry : nodeIdToNeighbors.long2ObjectEntrySet()) {
            final long nodeId = entry.getLongKey();
            Node nodeV = idToNode.get(nodeId);
            final LongArraySet neighbours = entry.getValue();
            Vertex v = idToVertex.get(nodeId);
            if (v == null) {
                v = new Vertex(index, nodeV.id(), nodeV.latLon());
                index++;
                idToVertex.put(nodeId, v);
            }
            for (long neighbour : neighbours) {
                Vertex u = idToVertex.get(neighbour);
                if (u == null) {
                    Node nodeU = idToNode.get(neighbour);
                    u = new Vertex(index, nodeU.id(), nodeU.latLon());
                    index++;
                    idToVertex.put(neighbour, u);
                }
                assert v.getId() != u.getId();
                v.addNeighbor(u);
                u.addNeighbor(v);
            }
        }
        idToVertex.trim();
        LOGGER.info("Processed all nodes");

        List<Vertex> l = new ArrayList<>(idToVertex.values());
        for (Vertex v : l) {
            assert v.getId() < l.size();
            assert !v.getNeighbors().contains(v);
        }
        return l;
    }
}
