package com.autoroute;

import com.autoroute.api.overpass.Box;
import com.autoroute.api.overpass.OverPassAPI;
import com.autoroute.logistic.PointVisiter;
import com.autoroute.logistic.RouteDistanceAlgorithm;
import com.autoroute.logistic.rodes.Cycle;
import com.autoroute.osm.LatLon;
import com.autoroute.osm.Tag;
import com.autoroute.osm.WayPoint;
import com.autoroute.osm.tags.TagsFileReader;
import com.autoroute.telegram.Bot;
import com.autoroute.telegram.db.Database;
import com.autoroute.telegram.db.Row;
import com.autoroute.telegram.db.Settings;
import com.autoroute.telegram.db.State;
import com.autoroute.utils.Utils;
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
            } catch (Throwable t) {
                LOGGER.error("exception in main: ", t);
            }
        }
    }

    private static void handleRouteRequest(Database db, Bot telegramBot, TagsFileReader tagsReader, Row dbRow) throws IOException {
        LOGGER.info("got a row: {}", dbRow);
        final LatLon startPoint = dbRow.startPoint();

        final String user = String.valueOf(dbRow.id());
        var pointVisiter = new PointVisiter();

        final RouteDistanceAlgorithm routeDistanceAlgorithm = new RouteDistanceAlgorithm(user);

        final int minDistance = dbRow.minDistance();
        final int maxDistance = dbRow.maxDistance();
        var routes = routeDistanceAlgorithm.buildRoutes(startPoint,
            minDistance, maxDistance, pointVisiter, 5);
        final long chatId = dbRow.chatId();
        if (routes.isEmpty()) {
            LOGGER.info("got routes = null for row: {}", dbRow);
            db.updateRow(dbRow.withState(State.FAILED_TO_PROCESS));
            telegramBot.sendMessage(chatId, "Seems like we couldn't build a route " +
                "with your criteria:( Please provide another distances or start point");
            return;
        }

        final Path tracksFolder = Utils.pathForRoute(startPoint, minDistance, maxDistance);
        Utils.deleteDirectory(tracksFolder.toFile());
        tracksFolder.toFile().mkdirs();
        for (int i = 0; i < routes.size(); i++) {
            var route = routes.get(i);
            final String fileName = (i + 1) + "_" + ((int) (Cycle.getCycleDistanceSlow(route.route()))) + "km.gpx";
            Utils.writeGPX(route, tracksFolder.resolve(fileName).toString());
        }
        final Path zipPath = tracksFolder.resolve("routes.zip");
        Utils.pack(tracksFolder, zipPath);
        telegramBot.sendFile(chatId, zipPath);


        db.updateRow(dbRow.withState(State.GOT_ALL_ROUTES));
        telegramBot.sendMessage(chatId,
            "Your routes are ready! You can use this site to look at your route: https://gpx.studio/.\n" +
                "You need to download generated .gpx file and load it on the site: Load GPX.");
        // telegramBot.sendFile(chatId, gpxPath);
    }

    private Settings readSqlSettings() {
        try {
            final String fileName = "sql.properties";
            InputStream iStream = this.getClass().getClassLoader()
                .getResourceAsStream(fileName);
            if (iStream == null) {
                throw new RuntimeException("File not found: " + fileName);
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

        Map<Tag, Integer> tagToCounterStats = new HashMap<>();
        StringBuilder debugQuireByIds = new StringBuilder("[out:json][timeout:120];\n");
        debugQuireByIds.append("(");
        for (var node : overpassResponse.getNodes()) {
            var tags = node.tags();
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
            debugQuireByIds.append("node(").append(node.id()).append(");");
            wayPoints.add(new WayPoint(node.id(), node.latLon(), node.getName()));


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