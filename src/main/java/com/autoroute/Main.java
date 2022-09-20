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
import com.autoroute.telegram.Bot;
import com.autoroute.telegram.db.Database;
import com.autoroute.telegram.db.Row;
import com.autoroute.telegram.db.Settings;
import com.autoroute.telegram.db.State;
import com.autoroute.utils.Utils;
import io.jenetics.jpx.GPX;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class Main {

    private static final Logger LOGGER = LogManager.getLogger(Main.class);
    private static final double DEFAULT_KM_PER_ONE_NODE = 20;

    private void run() {

        Settings sqlSettings = readSqlSettings();
        final Database db = new Database(sqlSettings);
        Bot telegramBot = Bot.startBot(db);

        for (; ; ) {
            try {
                var tagsReader = new TagsFileReader();
                tagsReader.readTags();

                // TODO: sort by datetime
                final List<Row> readyRows = db.getRowsByStateSortedByDate(State.SENT_DISTANCE);
                LOGGER.info("found: {} rows from db", readyRows.size());
                for (Row dbRow : readyRows) {
                    handleRouteRequest(db, telegramBot, tagsReader, dbRow);
                }
                if (readyRows.isEmpty()) {
                    LOGGER.info("didn't find any rows in db, sleeping...");
                    Thread.sleep(10 * 1000);
                }
            } catch (Exception e) {
                LOGGER.error("exception in main: ", e);
            } catch (OutOfMemoryError oom) {
                LOGGER.error("got OOM in main: ", oom);
                System.exit(2);
            }
        }
    }

    private static void handleRouteRequest(Database db, Bot telegramBot, TagsFileReader tagsReader, Row dbRow) throws IOException {
        double kmPerNode = DEFAULT_KM_PER_ONE_NODE;
        LOGGER.info("got a row: {}", dbRow);
        final LatLon startPoint = dbRow.startPoint();
        List<WayPoint> wayPoints = readNodes(startPoint, tagsReader, dbRow);
        if (wayPoints.size() > 500) {
            LOGGER.info("we have too many nodes: {} - use short list", wayPoints.size());
            tagsReader.readTags("short_list_tags.txt");
            wayPoints = readNodes(startPoint, tagsReader, dbRow);
        }

        final String user = String.valueOf(dbRow.id());
        var pointVisiter = new PointVisiter();
        var duplicate = new RouteDuplicateDetector();

        final RouteDistanceAlgorithm routeDistanceAlgorithm = new RouteDistanceAlgorithm(duplicate, user);

        final int minDistance = dbRow.minDistance();
        final int maxDistance = dbRow.maxDistance();
        var response = routeDistanceAlgorithm.buildRoute(
            minDistance, maxDistance, wayPoints, kmPerNode, pointVisiter, 5);
        final long chatId = dbRow.chatId();
        if (response == null) {
            LOGGER.info("got response = null for row: {}", dbRow);
            db.updateRow(dbRow.withState(State.FAILED_TO_PROCESS));
            telegramBot.sendMessage(chatId, "Seems like we couldn't build a route " +
                "with your criteria:( Please provide another distances or start point");
            return;
        }
        kmPerNode = response.kmPerOneNode();

        LOGGER.info("Start visit waypoints from the route");

        for (WayPoint wayPoint : response.wayPoints()) {
            pointVisiter.visit(user, wayPoint);
        }

        LOGGER.info("Start generate GPX for the route");
        final GPX gpx = GpxGenerator.generate(response.coordinates(), response.wayPoints());
        for (WayPoint wayPoint : response.wayPoints()) {
            LOGGER.info("https://www.openstreetmap.org/node/{}", wayPoint.id());
        }

        final Path tracksFolder = Utils.pathForRoute(startPoint, minDistance, maxDistance);
        tracksFolder.toFile().mkdirs();

        LOGGER.info("Start reading all tracks");
        var tracks = RouteDuplicateDetector.readTracks(startPoint, minDistance, maxDistance);
        var index = tracks.size() + 1;
        final Path gpxPath = tracksFolder.resolve(index + ".gpx");
        LOGGER.info("save a route as: {}", gpxPath);
        GPX.write(gpx, gpxPath);

        Row newRow = new Row(dbRow, State.GOT_ALL_ROUTES);
        db.updateRow(newRow);
        telegramBot.sendMessage(chatId,
            "Your routes are ready! You can use this site to look at your route: https://gpx.studio/.\n" +
                "You need to download generated .gpx file and load it on the site: Load GPX.\n" +
                "If you want more routes - You can use /repeat command. It will generate another route with the same location/distance.");
        telegramBot.sendFile(chatId, gpxPath);
    }

    private Settings readSqlSettings() {
        try {
            InputStream iStream = this.getClass().getClassLoader()
                .getResourceAsStream("sql.properties");
            if (iStream == null) {
                throw new RuntimeException("File not found");
            }
            Properties properties = new Properties();
            properties.load(iStream);

            final String url = properties.getProperty("url");
            final String user = properties.getProperty("user");
            final String password = properties.getProperty("password");
            return new Settings(url, user, password);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        LOGGER.info("Start Main");
        try {
            Main main = new Main();
            main.run();
        } finally {
            LOGGER.info("Finished main");
        }
    }

    @NotNull
    private static List<WayPoint> readNodes(LatLon startPoint, TagsFileReader tagsReader, Row readyRow) {
        double diffDegree = ((double) readyRow.maxDistance() / Constants.KM_IN_ONE_DEGREE) / 2;
        final Box box = new Box(
            startPoint.lat() - diffDegree,
            startPoint.lon() - diffDegree,
            startPoint.lat() + diffDegree,
            startPoint.lon() + diffDegree
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
        LOGGER.info("debugQuireByIds:\n{}", debugQuireByIds);
        LOGGER.info("found: {} nodes", wayPoints.size());
        LOGGER.info("Print Tags sorted by popularity");
        tagToCounterStats.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(LOGGER::info);
        return wayPoints;
    }
}