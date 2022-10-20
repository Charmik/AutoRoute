package com.autoroute.logistic.rodes;

import com.autoroute.api.trip.services.OsrmResponse;
import com.autoroute.logistic.PointVisiter;
import com.autoroute.logistic.RouteDistanceAlgorithm;
import com.autoroute.osm.LatLon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;

public class GraphMain {

    private static final Logger LOGGER = LogManager.getLogger(GraphMain.class);

    public static void main(String[] args) throws IOException {
        final long startTime = System.currentTimeMillis();
        LOGGER.info("Start process request");
        final int MIN_KM = 20;
        final int MAX_KM = 40;

//        final LatLon start = new LatLon(59.908977, 29.068520); // bor
        final LatLon start = new LatLon(34.700891, 33.098449); // cyprus

        Files.walk(Paths.get("o"), 10)
            .filter(e -> e.toString().endsWith(".gpx"))
            .forEach(e -> e.toFile().delete());

        final RouteDistanceAlgorithm alg = new RouteDistanceAlgorithm("charm");
        final OsrmResponse r = alg.buildRoute(start, MIN_KM, MAX_KM, Collections.emptyList(), new PointVisiter(), 1);
        final long finishTime = System.currentTimeMillis();
        LOGGER.info((finishTime - startTime) / 1000 + " seconds");
    }
}
