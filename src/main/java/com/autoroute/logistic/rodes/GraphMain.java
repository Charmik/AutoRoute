package com.autoroute.logistic.rodes;

import com.autoroute.logistic.PointVisiter;
import com.autoroute.logistic.RouteDistanceAlgorithm;
import com.autoroute.osm.LatLon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class GraphMain {

    private static final Logger LOGGER = LogManager.getLogger(GraphMain.class);

    private final static LatLon[] points = {
//        new LatLon(59.897299, 29.078159), // bor
//        new LatLon(34.711433, 33.131185), // cyprus
//        new LatLon(52.335352, 4.887436), // amsterdam
//        new LatLon(35.430590, -83.075770), // summer home
        new LatLon(54.851016, 83.100406), // novosib
    };

    public static void main(String[] args) throws IOException {
        final long startTime = System.currentTimeMillis();
        LOGGER.info("Start process request");
        final int MIN_KM = 30;
        final int MAX_KM = 100;

        for (LatLon start : points) {

            Files.walk(Paths.get("o"), 10)
                .filter(e -> e.toString().endsWith(".gpx"))
                .forEach(e -> e.toFile().delete());

            final RouteDistanceAlgorithm alg = new RouteDistanceAlgorithm(start, MAX_KM, "charm");
            final var r = alg.buildRoutes(start, MIN_KM, MAX_KM, new PointVisiter(), 1);
            final long finishTime = System.currentTimeMillis();
            LOGGER.info((finishTime - startTime) / 1000 + " seconds");
        }
        System.exit(0);
    }
}
