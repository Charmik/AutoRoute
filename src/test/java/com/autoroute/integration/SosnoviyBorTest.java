package com.autoroute.integration;

public class SosnoviyBorTest {
/*
    @Test
    public void FullIntegrationTest() {
        boolean success = false;
        // TODO: remove this hack when stubs will be implemented
        for (int test_number = 0; test_number < 3; test_number++) {
            try {

                List<WayPoint> wayPoints = new ArrayList<>();
                wayPoints.add(new WayPoint(1, new LatLon(59.908977, 29.068520), "Start"));

                wayPoints.add(new WayPoint(1, new LatLon(59.97586, 29.3327676), "Б-13"));
                wayPoints.add(new WayPoint(1, new LatLon(59.450327, 29.489648), "ЗАГС"));
                wayPoints.add(new WayPoint(1, new LatLon(59.6555221, 28.9885766), "Усадьба Блюментростови фон Герсдорфов"));


                var duplicate = new RouteDuplicateDetector();
                var response = new RouteDistanceAlgorithm(duplicate, "charm")
                    .buildRoute(150, 200, wayPoints, 500, new PointVisiter(), 1);
                if (response == null) {
                    continue;
                }
                final GPX gpx = GpxGenerator.generate(response.coordinates(), wayPoints);
                Assertions.assertEquals(1, gpx.getTracks().size());
                Assertions.assertEquals(4, gpx.getWayPoints().size());
                success = true;
                break;
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        Assertions.assertTrue(success);
    }
    */
}
