package com.autoroute.api.trip.services;

import com.autoroute.osm.LatLon;
import com.autoroute.osm.WayPoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class CacheTest {

    private Cache cache;

    @BeforeEach
    void setUp() throws IOException {
        final Path file = Files.createTempFile("test", "file");
        Assertions.assertTrue(file.toFile().delete());
        this.cache = new Cache(file, 128);
    }

    private OsrmResponse createResponse(int distance) {
        List<LatLon> coordinates = new ArrayList<>();
        coordinates.add(new LatLon(15, 16));
        coordinates.add(new LatLon(25, 33));
        List<WayPoint> wayPoints = new ArrayList<>();
        final WayPoint w1 = new WayPoint(1, new LatLon(100, 200), "waypoint1");
        wayPoints.add(w1);
        final WayPoint w2 = new WayPoint(2, new LatLon(1000, 2000), "waypoint2");
        wayPoints.add(w2);
        return new OsrmResponse(distance, coordinates, wayPoints, 15d);
    }

    @Test
    public void simpleTest() {
        var r1 = createResponse(0);
        var r2 = createResponse(1);
        cache.put("k1", r1);
        cache.put("k2", r2);
        Assertions.assertEquals(r1, cache.get("k1"));
        Assertions.assertEquals(r2, cache.get("k2"));
    }

    @Test
    public void checkLRUOrder() {
        for (int i = 0; i < 300; i++) {
            OsrmResponse r1 = createResponse(i);
            final String str = "key" + i;
            cache.put(str, r1);
        }
        boolean foundNull = false;
        for (int i = 300; i >= 0; i--) {
            final String str = "something" + i;
            final OsrmResponse res = cache.get(str);
            if (foundNull) {
                Assertions.assertNull(res);
            }
            if (res == null) {
                foundNull = true;
            }
        }
    }
}