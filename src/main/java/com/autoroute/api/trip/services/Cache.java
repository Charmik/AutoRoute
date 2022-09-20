package com.autoroute.api.trip.services;

import com.autoroute.osm.LatLon;
import com.autoroute.osm.WayPoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Cache {

    private static final Logger LOGGER = LogManager.getLogger(Cache.class);
    private static final Path CACHE_FILE = Paths.get("data/cache/cache.db");
    private static final int CACHE_MAX_SIZE = 1 << 10;

    public static final Cache CACHE = new Cache(); // should be shared between threads

    private final DB db;
    private final HTreeMap<String, OsrmResponse> cache;
    private final AtomicInteger counter;

    Cache() {
        this(CACHE_FILE, CACHE_MAX_SIZE);
    }

    Cache(Path dbPath, int size) {
        this.db = DBMaker
            .fileDB(dbPath.toAbsolutePath().toString())
            .transactionEnable()
            .fileMmapEnable()
            .make();

        this.cache = db.hashMap("cache")
            .keySerializer(Serializer.STRING)
            .valueSerializer(new OsrmResponseSerializer())
            .expireMaxSize(size)
            .expireAfterCreate()
            .createOrOpen();
        LOGGER.info("created cache with size: {}", cache.size());
        this.counter = new AtomicInteger();
    }

    synchronized OsrmResponse get(String request) {
        return cache.get(request);
    }

    synchronized void put(String request, OsrmResponse response) {
        cache.put(request, response);
        if (counter.incrementAndGet() % 50 == 0) {
            var start = System.currentTimeMillis();
            LOGGER.info("commit db to disk");
            db.commit();
            var time = (System.currentTimeMillis() - start) / 1000;
            LOGGER.info("committed db to disk for: {} seconds", time);
        }
    }

    static class OsrmResponseSerializer implements Serializer<OsrmResponse>, Serializable {

        @Override
        public void serialize(@NotNull DataOutput2 dataOutput2, @NotNull OsrmResponse r) throws IOException {
            double distance = r.distance();
            List<LatLon> coordinates = r.coordinates();
            List<WayPoint> waypoints = r.wayPoints();
            final Double kmPerOneNode = r.kmPerOneNode();

            dataOutput2.writeDouble(distance);

            dataOutput2.writeInt(coordinates.size());
            for (LatLon coordinate : coordinates) {
                dataOutput2.writeDouble(coordinate.lat());
                dataOutput2.writeDouble(coordinate.lon());
            }

            dataOutput2.writeInt(waypoints.size());
            for (WayPoint wayPoint : waypoints) {
                dataOutput2.writeLong(wayPoint.id());
                dataOutput2.writeDouble(wayPoint.latLon().lat());
                dataOutput2.writeDouble(wayPoint.latLon().lon());
                dataOutput2.writeUTF(wayPoint.name());
            }
            dataOutput2.writeDouble(kmPerOneNode);
        }

        @Override
        public OsrmResponse deserialize(@NotNull DataInput2 in, int n) throws IOException {
            final double distance = in.readDouble();

            final int coordinatesSize = in.readInt();
            List<LatLon> coordinates = new ArrayList<>(coordinatesSize);
            for (int i = 0; i < coordinatesSize; i++) {
                coordinates.add(new LatLon(in.readDouble(), in.readDouble()));
            }

            final int waypointsSize = in.readInt();
            List<WayPoint> waypoints = new ArrayList<>(waypointsSize);
            for (int i = 0; i < waypointsSize; i++) {
                waypoints.add(new WayPoint(
                    in.readLong(),
                    new LatLon(in.readDouble(), in.readDouble()),
                    in.readUTF()));
            }
            double kmPerOneNode = in.readDouble();
            return new OsrmResponse(distance, coordinates, waypoints, kmPerOneNode);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        DB db = DBMaker
            .fileDB("data/cache/cache.db")
            .transactionEnable()
            .fileMmapEnable()
            .make();

        Serializer<OsrmResponse> serializer = new OsrmResponseSerializer();

        final HTreeMap<String, OsrmResponse> map = db.hashMap("cache")
            .keySerializer(Serializer.STRING)
            .valueSerializer(serializer)
            .expireMaxSize(1 << 10)
            .expireAfterGet()
            .createOrOpen();
        printMap(map);

        for (int i = 0; i < 150; i++) {
            List<LatLon> coordinates = new ArrayList<>();
            coordinates.add(new LatLon(15, 16));
            coordinates.add(new LatLon(25, 33));
            List<WayPoint> wayPoints = new ArrayList<>();
            final WayPoint w1 = new WayPoint(1, new LatLon(100, 200), "waypoint1");
            wayPoints.add(w1);
            final WayPoint w2 = new WayPoint(2, new LatLon(1000, 2000), "waypoint2");
            wayPoints.add(w2);
            OsrmResponse r1 = new OsrmResponse(15, coordinates, wayPoints, 15d);
            final String str = "something" + i;
            map.put(str, r1);
        }
        for (int i = 0; i < 150; i++) {
            final String str = "something" + i;
            map.get(str);
        }
        Thread.sleep(1000);
        printMap(map);


        db.commit();
        System.exit(12);

//        db.close();
    }

    private static void printMap(HTreeMap<String, OsrmResponse> map) {
        System.out.println("size: " + map.size());
        System.out.println("-------------");
    }
}
