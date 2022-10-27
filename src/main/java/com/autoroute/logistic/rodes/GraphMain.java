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

    public static void main(String[] args) throws IOException {
        final long startTime = System.currentTimeMillis();
        LOGGER.info("Start process request");
        final int MIN_KM = 50;
        final int MAX_KM = 100;

//        final LatLon start = new LatLon(59.897299, 29.078159); // bor
//        final LatLon start = new LatLon(34.711433, 33.131185); // cyprus
        final LatLon start = new LatLon(52.335352, 4.887436); // amsterdam
//        final LatLon start = new LatLon(35.430590, -83.075770); // summer home
//        final LatLon start = new LatLon(35.430590, -83.075770); // summer home

        Files.walk(Paths.get("o"), 10)
            .filter(e -> e.toString().endsWith(".gpx"))
            .forEach(e -> e.toFile().delete());

        final RouteDistanceAlgorithm alg = new RouteDistanceAlgorithm("charm");
        final var r = alg.buildRoutes(start, MIN_KM, MAX_KM, new PointVisiter(), 1);
        final long finishTime = System.currentTimeMillis();
        LOGGER.info((finishTime - startTime) / 1000 + " seconds");
    }
}
