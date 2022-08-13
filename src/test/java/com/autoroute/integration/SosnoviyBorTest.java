package com.autoroute.integration;

import com.autoroute.api.osrm.services.TripAPI;
import com.autoroute.api.overpass.Box;
import com.autoroute.api.overpass.OverPassAPI;
import com.autoroute.api.overpass.OverpassResponse;
import com.autoroute.gpx.GpxGenerator;
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
                final List<Tag> tags = List.of(new Tag("historic", "castle"), new Tag("historic", "cannon"));
                var overPassAPI = new OverPassAPI();
                final var overpassResponse = overPassAPI.GetNodesInBoxByTags(box, tags);
                List<WayPoint> wayPoints = new ArrayList<>();
                StringBuilder stringCoordinates = new StringBuilder("29.068520,59.908977;"); // SosnoviyBor - home
                for (int i = 0; i < overpassResponse.size(); i++) {
                    OverpassResponse response = overpassResponse.get(i);
                    wayPoints.add(new WayPoint(response.latLon(), response.name()));
                    stringCoordinates.append(response.latLon().lon()).append(",");
                    stringCoordinates.append(response.latLon().lat());
                    if (i < overpassResponse.size() - 1) {
                        stringCoordinates.append(";");
                    }
                }

                var trip = new TripAPI();
                var json = trip.generateTrip(stringCoordinates.toString(), true);
                var code = json.getString("code");
                Assertions.assertEquals("Ok", code);

                var tripsArray = json.getJSONArray("trips");
                Assertions.assertEquals(1, tripsArray.length());

                List<LatLon> coordinates = new ArrayList<>();
                var arrayOfPoints = tripsArray
                    .getJSONObject(0)
                    .getJSONObject("geometry")
                    .getJSONArray("coordinates");
                for (int i = 0; i < arrayOfPoints.length(); i++) {
                    var lon = arrayOfPoints.getJSONArray(i).getDouble(0);
                    var lat = arrayOfPoints.getJSONArray(i).getDouble(1);
                    coordinates.add(new LatLon(lat, lon));
                }
                final GPX gpx = GpxGenerator.generate(coordinates, wayPoints);
                Assertions.assertEquals(1, gpx.getTracks().size());
                Assertions.assertEquals(3, gpx.getWayPoints().size());
                success = true;
                break;
            } catch (Exception ignored) {
            }
        }
        Assertions.assertTrue(success);
    }
}
