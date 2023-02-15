package com.autoroute.logistic.rodes.dijkstra;

import com.autoroute.logistic.rodes.Graph;
import com.autoroute.logistic.rodes.Vertex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DijkstraCache {

    private static final Logger LOGGER = LogManager.getLogger(DijkstraCache.class);
    private static DijkstraCache CACHE;

    public static void createCache(Graph fullGraph) {
        CACHE = new DijkstraCache(fullGraph);
    }

    // instead of List<Vertex> for memory saving
    private final Map<Pair, int[]> cache = new HashMap<>();
    private final Graph fullGraph;
    private int hit = 0;
    private int miss = 0;

    private DijkstraCache(Graph fullGraph) {
        this.fullGraph = fullGraph;
    }

    public static DijkstraCache getCache() {
        return CACHE;
    }

    public List<Vertex> get(Pair p) {
        var cacheResult = cache.get(p);
        if (cacheResult != null) {
            hit++;
            if (hit % 50000 == 0) {
                LOGGER.info("cache hit: {} miss: {}", hit, miss);
            }
            List<Vertex> l = new ArrayList<>(cacheResult.length);
            for (int id : cacheResult) {
                Vertex v = fullGraph.getVertexById(id);
                l.add(v);
            }
            return l;
        } else {
            return null;
        }
    }

    public void put(Pair p, List<Vertex> list) {
        int[] arrayOfIds = list.stream()
            .mapToInt(Vertex::getId)
            .toArray();
        cache.put(p, arrayOfIds);
        miss++;
        if (miss % 50000 == 0) {
            LOGGER.info("cache hit: {} miss: {}", hit, miss);
        }
    }

    public record Pair(long v, long u) {

    }

}


