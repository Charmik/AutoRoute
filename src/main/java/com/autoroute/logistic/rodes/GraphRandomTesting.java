package com.autoroute.logistic.rodes;

import com.autoroute.logistic.PointVisiter;
import com.autoroute.logistic.RouteDistanceAlgorithm;
import com.autoroute.logistic.LatLon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class GraphRandomTesting {

    private static final Logger LOGGER = LogManager.getLogger(GraphRandomTesting.class);

    public static void main(String[] args) throws IOException {
        LOGGER.info("Start process request");

        final List<String> lines = Files.readAllLines(Paths.get("citiesLatlon.txt"));
        for (String line : lines) {
            line = line.replaceAll("\t", " ");
            final String[] split = line.split(" ");
            double lat = Double.parseDouble(split[0]);
            double lon = Double.parseDouble(split[1]);

//            int minDistance = random.nextInt(150);
//            int minDistance = random.nextInt(150);
            int minDistance = 100;
            int maxDistance = 300;
            if (minDistance > maxDistance) {
                int t = minDistance;
                minDistance = maxDistance;
                maxDistance = t;
            }
            var start = new LatLon(lat, lon);
            LOGGER.info("start point: {}", start);
            final RouteDistanceAlgorithm alg = new RouteDistanceAlgorithm(start, maxDistance, "charm");
            final var r = alg.buildRoutes(start, minDistance, maxDistance, new PointVisiter(), 1);
            System.out.println(r.size());
        }

//        for(;;) {
//            int minDistance = random.nextInt(150);
//            int maxDistance = random.nextInt(150);
//            if (minDistance > maxDistance) {
//                int t = minDistance;
//                minDistance = maxDistance;
//                maxDistance = t;
//            }
//            var lat = random.nextDouble(100);
//            var lon = random.nextDouble(100);
//            var start = new LatLon(lat, lon);
//            LOGGER.info("start point: {}", start);
//            final RouteDistanceAlgorithm alg = new RouteDistanceAlgorithm(start, maxDistance, "charm");
//            final var r = alg.buildRoutes(start, minDistance, maxDistance, new PointVisiter(), 1);
//        }
    }
}
