package com.autoroute.logistic.rodes;

import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

public class DijkstraAlgorithm {

    private final Graph g;
    Long2DoubleOpenHashMap distances = new Long2DoubleOpenHashMap();
    private Vertex[] prev = null;
    private final Vertex startVertex;

    public DijkstraAlgorithm(Graph g, Vertex startVertex) {
        this.g = g;
        this.startVertex = startVertex;
    }

    public void run() {
        run(null);
    }

    public void run(@Nullable Vertex finish) {
        g.calculateDistanceForNeighbours();
        final List<Vertex> vertices = g.getVertices();

        this.prev = new Vertex[vertices.size()];

        for (Vertex v : vertices) {
            distances.put(v.getIdentificator(), Double.MAX_VALUE);
        }
        distances.put(startVertex.getIdentificator(), 0d);

        TreeSet<DijNode> queue = new TreeSet<>();
        for (Vertex v : vertices) {
            queue.add(new DijNode(v.getId(), distances.get(v.getIdentificator())));
        }

        while (!queue.isEmpty()) {
            final DijNode node = queue.pollFirst();
            Vertex v = vertices.get(node.getId());

            for (Vertex u : v.getNeighbors()) {
                assert u.getNeighbors().contains(v);
                final DijNode uNode = new DijNode(u.getId(), distances.get(u.getIdentificator()));
                if (!queue.contains(uNode)) {
                    continue;
                }
                double d = distances.get(v.getIdentificator()) + v.getDistance(u);
                if (d < distances.get(u.getIdentificator())) {
                    queue.remove(uNode);
                    distances.put(u.getIdentificator(), d);
                    prev[u.getId()] = v;
                    queue.add(new DijNode(u.getId(), d));
//                    if (finish != null && u.getIdentificator() == finish.getIdentificator()) {
//                        queue.clear();
//                        break;
//                    }
                }
            }
        }
        for (Long2DoubleMap.Entry entry : distances.long2DoubleEntrySet()) {
            assert entry.getDoubleValue() != Double.MAX_VALUE;
        }
    }

    public void assertStartVertex(Vertex v) {
        assert v.getIdentificator() == startVertex.getIdentificator();
    }

    public double getDistance(Vertex u) {
        assert distances != null;
        assert prev != null;
        final double distance = distances.get(u.getIdentificator());
        assert distance != 0;
        return distance;
    }

    public List<Vertex> getRouteFromFullGraph(Vertex u) {
        u = g.findNearestVertex(u.getLatLon());
        assert g.getVertices().contains(u);
        // TODO: do we need this for?
//        for (Vertex vertex : g.getVertices()) {
//            if (vertex.getIdentificator() == u.getIdentificator()) {
//                u = vertex;
//            }
//        }
        List<Vertex> route = new ArrayList<>();
        Vertex k = u;
        while (k != null) {
            route.add(k);
            Vertex nextVertex = prev[k.getId()];
            if (nextVertex == null) {
                break;
            }
            assert nextVertex.getNeighbors().contains(k);
            assert k.getNeighbors().contains(nextVertex);
            k = nextVertex;
        }
        Collections.reverse(route);
        assert route.get(0).getIdentificator() == startVertex.getIdentificator();

        return route;
    }

    private static class DijNode implements Comparable<DijNode> {
        private final int id;
        private final double distance;

        private DijNode(int id, double distance) {
            this.id = id;
            this.distance = distance;
        }

        public int getId() {
            return id;
        }

        public double getDistance() {
            return distance;
        }

        @Override
        public int compareTo(@NotNull DijNode o) {
            if (distance == o.distance) {
                return Integer.compare(id, o.id);
            }
            return Double.compare(distance, o.distance);
        }
    }
}
