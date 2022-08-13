package com.autoroute.integration;

import com.autoroute.api.overpass.Box;
import com.autoroute.api.overpass.OverPassAPI;
import com.autoroute.api.overpass.OverpassResponse;
import com.autoroute.gpx.GpxGenerator;
import com.autoroute.logistic.RouteDistanceAlgorithm;
import com.autoroute.osm.LatLon;
import com.autoroute.osm.Tag;
import com.autoroute.osm.WayPoint;
import io.jenetics.jpx.GPX;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class SosnoviyBorTest {

    @Test
    public void FullIntegrationTest() {
        boolean success = false;
        // TODO: remove this hack when stubs will be implemented
        for (int test_number = 0; test_number < 3; test_number++) {
            try {
                final Box box = new Box(59.369783, 28.577752, 59.982578, 29.842246);
                final List<Tag> tags = List.of(
                    new Tag("historic", "castle"),
                    new Tag("historic", "cannon")
                );
                var overPassAPI = new OverPassAPI();
                final var overpassResponse = overPassAPI.GetNodesInBoxByTags(box, tags);
                List<WayPoint> wayPoints = new ArrayList<>();
                wayPoints.add(new WayPoint(new LatLon(59.908977, 29.068520), "Start"));
                for (OverpassResponse response : overpassResponse) {
                    wayPoints.add(new WayPoint(response.latLon(), response.name()));
                }

                var response = new RouteDistanceAlgorithm()
                    .buildRoute(150, 200, wayPoints);

                final GPX gpx = GpxGenerator.generate(response.coordinates(), wayPoints);
                Assertions.assertEquals(1, gpx.getTracks().size());
                Assertions.assertEquals(4, gpx.getWayPoints().size());
                success = true;
                break;
            } catch (Exception ignored) {
            }
        }
        Assertions.assertTrue(success);
    }
}
