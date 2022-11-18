package com.autoroute.api.overpass;

import com.autoroute.logistic.rodes.Graph;
import com.autoroute.logistic.rodes.Vertex;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
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
        return mapToGraph(r, minDistance, maxDistance, false);
    }

    public static Graph mapToGraph(OverpassResponse r, int minDistance, int maxDistance, boolean clearResponse) {
        List<Vertex> vertices = getVertices(r, clearResponse);
        LOGGER.info("Start build graph");
        return new Graph(vertices, minDistance, maxDistance);
    }

    @NotNull
    private static List<Vertex> getVertices(OverpassResponse r, boolean clearResponse) {
        final List<Node> nodes = r.getNodes();
        final List<Way> ways = r.getWays();
        LOGGER.info("generate Vertexes, node: {}, ways: {}", nodes.size(), ways.size());

        LOGGER.info("Start finding neighbors");
        Long2ObjectOpenHashMap<LongArraySet> nodeIdToNeighbors = getNodeIdToNeighbors(ways);
        if (clearResponse) { // to escape OOM
            ways.clear();
        }
        LOGGER.info("Start finding id to Node map");
        Long2ObjectOpenHashMap<Node> idToNode = getIdToNode(nodes);
        if (clearResponse) { // to escape OOM
            nodes.clear();
        }
        LOGGER.info("Start creating vertices for the graph");
        Long2ObjectOpenHashMap<Vertex> idToVertex = getIdToVertex(nodeIdToNeighbors, idToNode);

        List<Vertex> l = new ArrayList<>(idToVertex.values());
        for (Vertex v : l) {
            assert v.getId() < l.size();
            assert !v.getNeighbors().contains(v);
        }
        return l;
    }

    @NotNull
    private static Long2ObjectOpenHashMap<Vertex> getIdToVertex(Long2ObjectOpenHashMap<LongArraySet> nodeIdToNeighbors, Long2ObjectOpenHashMap<Node> idToNode) {
        boolean isBig = nodeIdToNeighbors.size() > 500_000;
        Long2ObjectOpenHashMap<Vertex> idToVertex = new Long2ObjectOpenHashMap<>();
        int index = 0;
        LOGGER.info("graph is big: {}", isBig);
        if (isBig) {
            // to escape OOM
            while (!nodeIdToNeighbors.isEmpty()) {
                final var entry = nodeIdToNeighbors.long2ObjectEntrySet().iterator().next();
                index = createVertexForNode(idToNode, idToVertex, index, entry);
                nodeIdToNeighbors.remove(entry.getLongKey());
                if (index % 10000 == 0) {
                    nodeIdToNeighbors.trim();
                }
            }
        } else {
            for (var entry : nodeIdToNeighbors.long2ObjectEntrySet()) {
                index = createVertexForNode(idToNode, idToVertex, index, entry);
            }
        }
        idToVertex.trim();
        return idToVertex;
    }

    private static int createVertexForNode(
            Long2ObjectOpenHashMap<Node> idToNode,
            Long2ObjectOpenHashMap<Vertex> idToVertex,
            int index,
            Long2ObjectMap.Entry<LongArraySet> entry) {
        final long nodeId = entry.getLongKey();
        Node nodeV = idToNode.get(nodeId);
        if (nodeV == null) { // nodes & ways are not equals, it means this node is too far away.
            return index;
        }
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
                if (nodeU == null) { // nodes & ways are not equals, it means this node is too far away.
                    continue;
                }
                u = new Vertex(index, nodeU.id(), nodeU.latLon());
                index++;
                idToVertex.put(neighbour, u);
            }
            assert v.getId() != u.getId();
            v.addNeighbor(u);
            u.addNeighbor(v);
        }
        return index;
    }

    @NotNull
    private static Long2ObjectOpenHashMap<Node> getIdToNode(List<Node> nodes) {
        Long2ObjectOpenHashMap<Node> idToNode = new Long2ObjectOpenHashMap<>();
        for (Node node : nodes) {
            idToNode.put(node.id(), node);
        }
        idToNode.trim();
        return idToNode;
    }

    @NotNull
    private static Long2ObjectOpenHashMap<LongArraySet> getNodeIdToNeighbors(List<Way> ways) {
        Long2ObjectOpenHashMap<LongArraySet> nodeIdToNeighbors = new Long2ObjectOpenHashMap<>();
        for (int i = 0; i < ways.size(); i++) {
            Way w = ways.get(i);
            final long[] nodeIds = w.nodesIds();

            for (int j = 0; j < nodeIds.length; j++) {
                final long nodeId = nodeIds[j];
                var neighbours = getOrCreateSet(nodeIdToNeighbors, nodeId);
                if (j > 0 && nodeIds[j] != nodeIds[j - 1]) { // can be duplicates in OSM
                    neighbours.add(nodeIds[j - 1]);
                }
                if (j < nodeIds.length - 1 && nodeIds[j] != nodeIds[j + 1]) { // can be duplicates in OSM
                    neighbours.add(nodeIds[j + 1]);
                }
                nodeIdToNeighbors.put(nodeId, neighbours);
            }
        }
        nodeIdToNeighbors.trim();
        return nodeIdToNeighbors;
    }
}
