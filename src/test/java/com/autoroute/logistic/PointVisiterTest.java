package com.autoroute.logistic;

import com.autoroute.osm.WayPoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

class PointVisiterTest {

    private static final String testUser = "test";
    private static final Path testPath =
        Paths.get(PointVisiter.FILE_PATH).resolve(Paths.get(testUser + ".txt"));

    @BeforeEach
    public void init() {
        if (testPath.toFile().exists()) {
            boolean deleted = testPath.toFile().delete();
            if (!deleted) {
                throw new RuntimeException();
            }
        }
    }

    @Test
    void visitWaypoinsTest() {
        var pointVisiter = new PointVisiter();
        var wayPoints = List.of(
            new WayPoint(1, new LatLon(1.1, 2.2), "w1"),
            new WayPoint(2, new LatLon(1.1, 2.2), "w2"),
            new WayPoint(3, new LatLon(2.2, 2.2), "w3"),
            new WayPoint(4, new LatLon(3.3, 3.3), "w4")
        );
        for (WayPoint wayPoint : wayPoints) {
            pointVisiter.visit(testUser, wayPoint);
        }
        for (WayPoint wayPoint : wayPoints) {
            Assertions.assertTrue(pointVisiter.isVisited(testUser, wayPoint));
        }
        Assertions.assertFalse(pointVisiter.isVisited(testUser,
            new WayPoint(5, new LatLon(3.3, 3.3), "w4")));
        Assertions.assertFalse(pointVisiter.isVisited(testUser,
            new WayPoint(4, new LatLon(3.4, 3.3), "w4")));
        Assertions.assertFalse(pointVisiter.isVisited(testUser,
            new WayPoint(4, new LatLon(3.3, 3.4), "w4")));
    }
}