package com.autoroute.logistic.rodes.dijkstra;

import com.autoroute.logistic.rodes.Vertex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class DijkstraCache {

    private static final Logger LOGGER = LogManager.getLogger(DijkstraCache.class);
    private static final DijkstraCache CACHE;

    static {
        CACHE = new DijkstraCache();
    }

    private final Map<Pair, List<Vertex>> cache = new HashMap<>();
    private int hit = 0;
    private int miss = 0;

    private DijkstraCache() {
    }

    public static DijkstraCache getCache() {
        return CACHE;
    }

    public List<Vertex> get(Pair p) {
        var res = cache.get(p);
        if (res != null) {
            hit++;
            if (hit % 5000 == 0) {
                LOGGER.info("cache hit: {} miss: {}", hit, miss);
            }
        }
        return res;
    }

    public void put(Pair p, List<Vertex> list) {
        // TODO: check for size?
        cache.put(p, list);
        miss++;
        if (miss % 5000 == 0) {
            LOGGER.info("cache hit: {} miss: {}", hit, miss);
        }
    }

    public record Pair(long v, long u) {

    }

}


