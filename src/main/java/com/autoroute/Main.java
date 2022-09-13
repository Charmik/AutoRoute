package com.autoroute;

import com.autoroute.api.overpass.Box;
import com.autoroute.api.overpass.OverPassAPI;
import com.autoroute.api.overpass.OverpassResponse;
import com.autoroute.gpx.GpxGenerator;
import com.autoroute.gpx.RouteDuplicateDetector;
import com.autoroute.logistic.PointVisiter;
import com.autoroute.logistic.RouteDistanceAlgorithm;
import com.autoroute.osm.LatLon;
import com.autoroute.osm.Tag;
import com.autoroute.osm.WayPoint;
import com.autoroute.tags.TagsFileReader;
import io.jenetics.jpx.GPX;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

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

    public static void main(String[] args) {
        LOGGER.info("Start Main");
//        LatLon startPoint = new LatLon(59.908977, 29.068520); // bor
//        LatLon startPoint = new LatLon(35.430590, -83.075770); // summer home
//        LatLon startPoint = new LatLon(34.687562, 32.961236); // CYPRUS
//        LatLon startPoint = new LatLon(53.585437, 49.069918); // yagodnoe
//        LatLon startPoint = new LatLon(55.610989, 37.603291); // chertanovo
        LatLon startPoint = new LatLon(36.378029, 33.927040); // Silifke

        for (int i = 0; i < 50; i++) {
            try {
                var tagsReader = new TagsFileReader();
                tagsReader.readTags();

                List<WayPoint> wayPoints = readNodes(startPoint, tagsReader);
                if (wayPoints.size() > 500) {
                    LOGGER.info("we have too many nodes: {} - use short list", wayPoints.size());
                    tagsReader.readTags("short_list_tags.txt");
                    wayPoints = readNodes(startPoint, tagsReader);
                }

                var pointVisiter = new PointVisiter();
                var duplicate = new RouteDuplicateDetector();

                final RouteDistanceAlgorithm routeDistanceAlgorithm = new RouteDistanceAlgorithm(duplicate);

                var response = routeDistanceAlgorithm.buildRoute(
                    MIN_KM, MAX_KM, wayPoints, pointVisiter, 5);
                routeDistanceAlgorithm.getTripAPI().flush();
                if (response == null) {
                    LOGGER.info("got response = null");
                    break;
                }

                LOGGER.info("Start visit waypoints from the route");
                for (WayPoint wayPoint : response.wayPoints()) {
                    pointVisiter.visit(Constants.DEFAULT_USER, wayPoint);
                }

                LOGGER.info("Start generate GPX for the route");
                final GPX gpx = GpxGenerator.generate(response.coordinates(), response.wayPoints());
                for (WayPoint wayPoint : response.wayPoints()) {
                    LOGGER.info("https://www.openstreetmap.org/node/" + wayPoint.id());
                }

                final Path tracksFolder = Paths.get("tracks").resolve(Paths.get(startPoint.toString()));
                tracksFolder.toFile().mkdirs();

                LOGGER.info("Start reading all tracks");
                var tracks = RouteDuplicateDetector.readTracks(startPoint);
                var index = tracks.size() + 1;
                final Path gpxPath = tracksFolder.resolve(index + ".gpx");
                LOGGER.info("save a route as: {}", gpxPath);
                GPX.write(gpx, gpxPath);
            } catch (Throwable t) {
                LOGGER.error("couldn't process route with exception: ", t);
            }
        }
        LOGGER.info("Finished main");
    }

    @NotNull
    private static List<WayPoint> readNodes(LatLon startPoint, TagsFileReader tagsReader) {
        final Box box = new Box(
            startPoint.lat() - DIFF_DEGREE,
            startPoint.lon() - DIFF_DEGREE,
            startPoint.lat() + DIFF_DEGREE,
            startPoint.lon() + DIFF_DEGREE
        );

        var overPassAPI = new OverPassAPI();
        final var overpassResponse = overPassAPI.getNodesInBoxByTags(box, tagsReader.getTags());

        List<WayPoint> wayPoints = new ArrayList<>();

        wayPoints.add(new WayPoint(-1, startPoint, "Start"));

        Map<Tag, Integer> tagToCounterStats = new HashMap<>();
        StringBuilder debugQuireByIds = new StringBuilder("[out:json][timeout:120];\n");
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
        LOGGER.info("Print Tags sorted by popularity");
        tagToCounterStats.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(LOGGER::info);
        return wayPoints;
    }
}