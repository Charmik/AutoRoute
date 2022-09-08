package com.autoroute.gpx;

import com.autoroute.osm.LatLon;
import io.jenetics.jpx.GPX;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Duplicate {

    private static final Logger LOGGER = LogManager.getLogger(Duplicate.class);

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
                if (point1.IsClosePoint(point2)) {
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
        List<Path> files = readTracks(startPoint);
        for (Path path : files) {
            final GPX fileGPX = readRoute(path);

            List<LatLon> points2 = gpxToCoordinates(fileGPX);
            if (isDuplicate(points1, points2) || isDuplicate(points2, points1)) {
                return true;
            }
        }
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
        List<Path> files = readTracks(startPoint);
        var points1 = gpxToCoordinates(gpx);
        for (Path path : files) {
            var points2 = gpxToCoordinates(readRoute(path));
            if (isDuplicate(points1, points2) || isDuplicate(points2, points1)) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    public static List<Path> readTracks(LatLon startPoint) {
        try {
            return Files.walk(Paths.get("tracks/").resolve(startPoint.toString()), 1)
                .filter(e -> e.toString().endsWith(".gpx"))
                .toList();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static void main(String[] args) throws IOException {
        final Duplicate duplicate = new Duplicate();

        var files = Files.walk(Paths.get("tracks/"), 1).filter(e -> e.toString().endsWith(".gpx")).toList();
        for (int i = 0; i < files.size(); i++) {
            for (int j = i + 1; j < files.size(); j++) {
                var path1 = files.get(i);
                var path2 = files.get(j);
                var isDuplicate = duplicate.isDuplicate(
                    gpxToCoordinates(duplicate.readRoute(path1)),
                    gpxToCoordinates(duplicate.readRoute(path2)));
                if (isDuplicate) {
                    LOGGER.info("path1: " + path1 + " path2: " + path2);
                }
            }
        }

//        var gpx = duplicate.readRoute(Paths.get("tracks").resolve(Paths.get("0.gpx")));
//        duplicate.isDuplicate(gpx, gpx);
    }

}
