package com.autoroute;

import com.autoroute.api.overpass.Box;
import com.autoroute.api.overpass.OverPassAPI;
import com.autoroute.api.overpass.OverpassResponse;
import com.autoroute.gpx.Duplicate;
import com.autoroute.gpx.GpxGenerator;
import com.autoroute.logistic.PointVisiter;
import com.autoroute.logistic.RouteDistanceAlgorithm;
import com.autoroute.osm.LatLon;
import com.autoroute.osm.Tag;
import com.autoroute.osm.WayPoint;
import com.autoroute.tags.TagsFileReader;
import io.jenetics.jpx.GPX;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    private static final int MIN_KM = 50;
    private static final int MAX_KM = 100;
    private static final double DIFF_DEGREE = ((double) MAX_KM / Constants.KM_IN_ONE_DEGREE) / 2;

    public static void main(String[] args) throws IOException {
        LOGGER.info("Start Main");

        for (int i = 0; i < 50; i++) {

            var tagsReader = new TagsFileReader();
            tagsReader.readTags();

//            final LatLon startPoint = new LatLon(59.908977, 29.068520); // bor
//            final LatLon startPoint = new LatLon(35.430590, -83.075770); // summer home
            final LatLon startPoint = new LatLon(34.687562, 32.961236); // CYPRUS
//            final LatLon startPoint = new LatLon(53.585437, 49.069918); // yagodnoe


            final Box box = new Box(
                startPoint.lat() - DIFF_DEGREE,
                startPoint.lon() - DIFF_DEGREE,
                startPoint.lat() + DIFF_DEGREE,
                startPoint.lon() + DIFF_DEGREE
            );

            var overPassAPI = new OverPassAPI();
            final var overpassResponse = overPassAPI.GetNodesInBoxByTags(box, tagsReader.getTags());

            List<WayPoint> wayPoints = new ArrayList<>();

            wayPoints.add(new WayPoint(-1, startPoint, "Start")); // bor

            Map<Tag, Integer> tagToCounterStats = new HashMap<>();
            StringBuilder debugQuireByIds = new StringBuilder("[out:json][timeout:25];\n");
            debugQuireByIds.append("(");
            for (OverpassResponse response : overpassResponse) {
                var tags = response.tags();
                // filter only drinking water
                if (tags.containsKey("natural")) {
                    if ("spring".equals(tags.get("natural"))) {
                        boolean is_drinking =
                            tags.containsKey("drinking_water") && "yes".equals(tags.get("drinking_water"));
                        if (!is_drinking) {
                            continue;
                        }
                    }
                }
                debugQuireByIds.append("node(").append(response.id()).append(");");
                wayPoints.add(new WayPoint(response.id(), response.latLon(), response.getName()));


                for (Map.Entry<String, String> entry : tags.entrySet()) {
                    Tag tag = new Tag(entry.getKey(), entry.getValue());
                    if (tagsReader.getTags().contains(tag)) {
                        tagToCounterStats.compute(tag, (k, v) -> {
                            if (v == null) {
                                return 1;
                            } else {
                                return v + 1;
                            }
                        });
                    }
                }
            }
            debugQuireByIds.append(");\nout;");
            LOGGER.info("----------------------------");
            LOGGER.info("----------------------------");
            LOGGER.info("debugQuireByIds:\n" + debugQuireByIds);
            LOGGER.info("found: " + wayPoints.size() + " nodes");
            tagToCounterStats.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(System.out::println);

            var pointVisiter = new PointVisiter();
            var duplicate = new Duplicate();

            final RouteDistanceAlgorithm routeDistanceAlgorithm = new RouteDistanceAlgorithm(duplicate);

            var response = routeDistanceAlgorithm.buildRoute(MIN_KM, MAX_KM, wayPoints, pointVisiter, 5);
            routeDistanceAlgorithm.getTripAPI().flush();
            if (response == null) {
                break;
            }

            for (WayPoint wayPoint : response.wayPoints()) {
                pointVisiter.visit(Constants.DEFAULT_USER, wayPoint);
            }

            final GPX gpx = GpxGenerator.generate(response.coordinates(), response.wayPoints());
            for (WayPoint wayPoint : response.wayPoints()) {
                LOGGER.info("https://www.openstreetmap.org/node/" + wayPoint.id());
            }

            final Path tracksFolder = Paths.get("tracks").resolve(Paths.get(startPoint.toString()));
            tracksFolder.toFile().mkdirs();

            var tracks = Duplicate.readTracks(startPoint);
            var index = tracks.size() + 1;
            final Path gpxPath = tracksFolder.resolve(index + ".gpx");
            GPX.write(gpx, gpxPath);
        }
    }
}