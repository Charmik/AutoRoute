package com.autoroute.logistic.rodes.dijkstra;

import com.autoroute.logistic.rodes.Graph;
import com.autoroute.logistic.rodes.Vertex;
import com.autoroute.osm.LatLon;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

public class DijkstraAlgorithm {

    private static final Logger LOGGER = LogManager.getLogger(DijkstraAlgorithm.class);

    private final Graph fullGraph;
    private final Long2DoubleOpenHashMap distances;
    private final Long2ObjectOpenHashMap<Vertex> prev;
    private final DijkstraCache dijkstraCache;
    private final Vertex startVertex;

    public DijkstraAlgorithm(Graph fullGraph, Vertex startVertex) {
        assert fullGraph.isFullGraph();
        this.fullGraph = fullGraph;
        this.distances = new Long2DoubleOpenHashMap();
        this.prev = new Long2ObjectOpenHashMap<>();
        this.dijkstraCache = DijkstraCache.getCache();
        this.startVertex = startVertex;
    }

    public void run() {
        run(null);
    }

    public void run(@Nullable Vertex finish) {
        if (finish != null) {
            DijkstraCache.Pair p = new DijkstraCache.Pair(startVertex.getIdentificator(), finish.getIdentificator());
            if (dijkstraCache.get(p) != null) {
                return;
            }
        }
        distances.put(startVertex.getIdentificator(), 0d);

        TreeSet<DijNode> queue = new TreeSet<>();
        queue.add(new DijNode(startVertex.getIdentificator(),
            distances.get(startVertex.getIdentificator()),
            distances.get(startVertex.getIdentificator())));

        while (!queue.isEmpty()) {
            if (!dijkstraIteration(finish, queue)) {
                break;
            }
        }
        if (finish == null) {
            for (Long2DoubleMap.Entry entry : distances.long2DoubleEntrySet()) {
                assert entry.getDoubleValue() != Double.MAX_VALUE;
            }
        }
    }

    private boolean dijkstraIteration(@Nullable Vertex finish, TreeSet<DijNode> queue) {
        final DijNode node = queue.pollFirst();
        assert node != null;
        Vertex v = fullGraph.findByIdentificator(node.identificator);

        for (Vertex u : v.getNeighbors()) {
            assert u.containsNeighbor(v);
            double distanceToU = distances.get(u.getIdentificator());
            if (distanceToU == 0 && u.getIdentificator() != startVertex.getIdentificator()) {
                distanceToU = Integer.MAX_VALUE;
            }
            final DijNode uNode = new DijNode(u.getIdentificator(), distanceToU, calculateCost(finish, u));
            final double distanceToV = distances.get(v.getIdentificator());
            double d = distanceToV + v.getDistance(u);
            if (d < distanceToU) {
                queue.remove(uNode);
                distances.put(u.getIdentificator(), d);
                if (prev.get(v.getIdentificator()) != null) {
                    // to be sure we don't have cycle prevs
                    assert prev.get(v.getIdentificator()).getIdentificator() != u.getIdentificator();
                }
                prev.put(u.getIdentificator(), v);
                queue.add(new DijNode(u.getIdentificator(), d, calculateCost(finish, u)));
                if (finish != null && u.getIdentificator() == finish.getIdentificator()) {
                    return false;
                }
            }
        }
        return true;
    }

    private double calculateCost(@Nullable Vertex finish, Vertex u) {
        double cost = distances.get(u.getIdentificator());
        if (finish != null) {
            cost += LatLon.fastDistance(u.getLatLon(), finish.getLatLon());
        }
        return cost;
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
        DijkstraCache.Pair p = new DijkstraCache.Pair(startVertex.getIdentificator(), u.getIdentificator());
        List<Vertex> cacheResult = dijkstraCache.get(p);
        if (cacheResult != null) {
            assert cacheResult.get(0).getIdentificator() == startVertex.getIdentificator();
            assert cacheResult.get(cacheResult.size() - 1).getIdentificator() == u.getIdentificator();
            return new ArrayList<>(cacheResult);
        }

        final Vertex newU = fullGraph.findByIdentificator(u.getIdentificator());
        assert u.getIdentificator() == newU.getIdentificator();
        u = newU;

        List<Vertex> route = new ArrayList<>();
        Vertex k = u;
        while (true) {
            route.add(k);
            assert this.prev != null;
            Vertex nextVertex = prev.get(k.getIdentificator());
            if (nextVertex == null) {
                break;
            }
            assert nextVertex.getNeighbors().contains(k);
            assert k.getNeighbors().contains(nextVertex);
            k = nextVertex;
        }
        Collections.reverse(route);
        assert route.get(0).getIdentificator() == startVertex.getIdentificator();
        assert route.get(route.size() - 1).getIdentificator() == u.getIdentificator();

        dijkstraCache.put(p, Collections.unmodifiableList(route));
        return new ArrayList<>(route);
    }

    private record DijNode(long identificator, double distance, double heuristicCost) implements Comparable<DijNode> {

        @Override
        public int compareTo(@NotNull DijNode o) {
            if (heuristicCost == o.heuristicCost) {
                return Long.compare(identificator, o.identificator);
            }
            return Double.compare(heuristicCost, o.heuristicCost);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DijNode dijNode = (DijNode) o;

            return identificator == dijNode.identificator;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(identificator);
        }
    }
}
