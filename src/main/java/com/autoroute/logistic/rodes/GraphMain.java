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
        LOGGER.info("Start process request");
        final int MIN_KM = 50;
        final int MAX_KM = 80;

//        final LatLon start = new LatLon(59.908977, 29.068520); // bor
        final LatLon start = new LatLon(34.690139, 32.987961); // cyprus
//        final LatLon start = new LatLon(34.753686, 32.962196); // cyprus NOT CITY

        Files.walk(Paths.get("o"), 1)
            .filter(e -> e.toString().endsWith(".gpx"))
            .forEach(e -> e.toFile().delete());


        final RouteDistanceAlgorithm alg = new RouteDistanceAlgorithm("charm");
        final OsrmResponse r = alg.buildRoute(start, MIN_KM, MAX_KM, Collections.emptyList(), new PointVisiter(), 1);
    }
}
