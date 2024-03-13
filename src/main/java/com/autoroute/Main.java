package com.autoroute;

import com.autoroute.logistic.LogisticUtils;
import com.autoroute.logistic.PointVisiter;
import com.autoroute.logistic.RouteDistanceAlgorithm;
import com.autoroute.osm.LatLon;
import com.autoroute.telegram.Bot;
import com.autoroute.telegram.db.Database;
import com.autoroute.telegram.db.Row;
import com.autoroute.telegram.db.Settings;
import com.autoroute.telegram.db.State;
import com.autoroute.utils.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

public class Main {

    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    private void run() {

        Settings sqlSettings = readSqlSettings();
        final Database db = new Database(sqlSettings);
        Bot telegramBot = Bot.startBot(db);

        int count = 0;
        for (; ; ) {
            try {
                final List<Row> readyRows = db.getRowsByStateSortedByDate(State.SENT_DISTANCE);
                if (!readyRows.isEmpty()) {
                    LOGGER.info("found: {} rows from db", readyRows.size());
                }
                for (Row dbRow : readyRows) {
                    handleRouteRequest(db, telegramBot, dbRow);
                }
                if (readyRows.isEmpty()) {
                    if (count % 1000 == 0) {
                        LOGGER.info("didn't find any rows in db, sleeping...");
                    }
                    count++;
                    Thread.sleep(10 * 1000);
                }
            } catch (Throwable t) {
                LOGGER.error("exception in main: ", t);
                // TODO: skip request for future process no eliminate infite loop over "bad" request. write to log/telegram about it.
            }
        }
    }

    private static void handleRouteRequest(Database db, Bot telegramBot, Row dbRow) throws IOException {
        LOGGER.info("got a row: {}", dbRow);
        final LatLon startPoint = dbRow.startPoint();

        final String user = String.valueOf(dbRow.id());
        var pointVisiter = new PointVisiter();

        final int minDistance = dbRow.minDistance();
        final int maxDistance = dbRow.maxDistance();
        final RouteDistanceAlgorithm routeDistanceAlgorithm = new RouteDistanceAlgorithm(startPoint, maxDistance, user);

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
            final String fileName = (i + 1) + "_" + ((int) (LogisticUtils.getCycleDistanceSlow(route.route()))) + "km";
            Utils.writeGPX(route, tracksFolder.resolve(fileName).toString());
        }
        final Path zipPath = tracksFolder.resolve("routes.zip");
        Utils.pack(tracksFolder, zipPath);
        telegramBot.sendFile(chatId, zipPath);


        db.updateRow(dbRow.withState(State.GOT_ALL_ROUTES));
        telegramBot.sendMessage(chatId,
            "Your routes are ready! You can use this site to look at your route: https://gpx.studio/.\n" +
                "You need to download generated .gpx file and load it on the site: Load GPX.");
        LOGGER.info("processed request");
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
}