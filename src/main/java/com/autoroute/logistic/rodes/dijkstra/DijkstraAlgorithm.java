package com.autoroute.logistic.rodes.dijkstra;

import com.autoroute.logistic.rodes.Graph;
import com.autoroute.logistic.rodes.Vertex;
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
    private final Long2DoubleOpenHashMap distances;
    private Vertex[] prev = null;
    private final Vertex startVertex;

    public DijkstraAlgorithm(Graph g, Vertex startVertex) {
        this.g = g;
        this.startVertex = startVertex;
        this.distances = new Long2DoubleOpenHashMap(g.size());
    }

    public void run() {
        run(null);
    }

    public void run(@Nullable Vertex finish) {
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
            dijkstraIteration(finish, queue);
        }
        if (finish == null) {
            for (Long2DoubleMap.Entry entry : distances.long2DoubleEntrySet()) {
                assert entry.getDoubleValue() != Double.MAX_VALUE;
            }
        }
    }

    private void dijkstraIteration(@Nullable Vertex finish, TreeSet<DijNode> queue) {
        final DijNode node = queue.pollFirst();
        assert node != null;
        Vertex v = g.getVertices().get(node.id());

        for (Vertex u : v.getNeighbors()) {
            assert u.containsNeighbor(v);
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
                if (finish != null && u.getIdentificator() == finish.getIdentificator()) {
                    queue.clear();
                    break;
                }
            }
        }
    }

    public void assertStartVertex(Vertex v) {
        assert v.getIdentificator() == startVertex.getIdentificator();
    }

    public double getDistance(Vertex u) {
        assert distances != null;
        assert prev != null;
        return distances.get(u.getIdentificator());
    }

    public List<Vertex> getRouteFromFullGraph(Vertex u) {
        assert g.getVertices().contains(u);
        final Vertex newU = g.findNearestVertex(u.getLatLon());
        assert u.getIdentificator() == newU.getIdentificator();
        u = newU;

        List<Vertex> route = new ArrayList<>();
        Vertex k = u;
        while (true) {
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

    private record DijNode(int id, double distance) implements Comparable<DijNode> {

        @Override
        public int compareTo(@NotNull DijNode o) {
            if (distance == o.distance) {
                return Integer.compare(id, o.id);
            }
            return Double.compare(distance, o.distance);
        }
    }
}
