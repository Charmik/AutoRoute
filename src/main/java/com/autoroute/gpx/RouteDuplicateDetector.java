package com.autoroute.gpx;

import com.autoroute.osm.LatLon;
import com.autoroute.utils.Utils;
import io.jenetics.jpx.GPX;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// TODO: add user everywhere because duplicates depends on the user
public class RouteDuplicateDetector {

    private static final Logger LOGGER = LogManager.getLogger(RouteDuplicateDetector.class);

    public GPX readRoute(Path path) {
        try {
            return GPX.read(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    boolean isDuplicate(List<LatLon> points1, List<LatLon> points2) {
        int count = 0;

        for (var point1 : points1) {
            for (var point2 : points2) {
                if (point1.isClosePoint(point2)) {
                    count++;
                    break;
                }
            }
        }
        if (count > points1.size() / 100 * 85) {
            return true;
        }
        return false;
    }

    // TODO: add user parameter
    public boolean hasDuplicateInFiles(List<LatLon> points1, LatLon startPoint) {
        /*
        List<Path> files = readTracks(startPoint, dbRow.minDistance(), dbRow.maxDistance());
        for (Path path : files) {
            final GPX fileGPX = readRoute(path);

            List<LatLon> points2 = gpxToCoordinates(fileGPX);
            if (isDuplicate(points1, points2) || isDuplicate(points2, points1)) {
                return true;
            }
        }
         */
        return false;
    }

    @NotNull
    private static List<LatLon> gpxToCoordinates(GPX fileGPX) {
        assert fileGPX.getTracks().size() == 1;
        var track1 = fileGPX.getTracks().get(0);
        assert track1.getSegments().size() == 1;

        var trackSegment1 = track1.getSegments().get(0);
        return trackSegment1.getPoints().stream()
            .map(LatLon::castFromWayPoint)
            .toList();
    }

    // TODO: add user parameter
    public boolean hasDuplicateInFiles(GPX gpx, LatLon startPoint) {
        /*
        List<Path> files = readTracks(startPoint, dbRow.minDistance(), dbRow.maxDistance());
        var points1 = gpxToCoordinates(gpx);
        for (Path path : files) {
            var points2 = gpxToCoordinates(readRoute(path));
            if (isDuplicate(points1, points2) || isDuplicate(points2, points1)) {
                return true;
            }
        }
        */
        return false;
    }

    @NotNull
    public static List<Path> readTracks(LatLon startPoint, int minDistance, int maxDistance) {
        try {
            return Files.walk(Utils.pathForRoute(startPoint, minDistance, maxDistance), 1)
                .filter(e -> e.toString().endsWith(".gpx"))
                .toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
