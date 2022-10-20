package com.autoroute.logistic.rodes.dijkstra;

import com.autoroute.logistic.rodes.Vertex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DijkstraCache {

    private static final Logger LOGGER = LogManager.getLogger(DijkstraCache.class);

    private final Map<Pair, List<Vertex>> cache = new HashMap<>();
    private int hit = 0;
    private int miss = 0;

    public DijkstraCache() {
    }

    public List<Vertex> get(Pair p) {
        var res = cache.get(p);
        if (res != null) {
            hit++;
            if (hit % 500 == 0) {
                LOGGER.info("cache hit: {} miss: {}", hit, miss);
            }
        }
        return res;
    }

    public void put(Pair p, List<Vertex> list) {
        // TODO: check for size?
        cache.put(p, list);
        miss++;
        if (miss % 500 == 0) {
            LOGGER.info("cache hit: {} miss: {}", hit, miss);
        }
    }

    public record Pair(long v, long u) {

    }

}


