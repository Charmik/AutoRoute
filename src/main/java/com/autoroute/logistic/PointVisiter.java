package com.autoroute.logistic;

import com.autoroute.osm.WayPoint;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class PointVisiter {

    static final String FILE_PATH = "config/visit/";

    public void visit(String user, WayPoint newWaypoint) {
        final Path dirPath = Paths.get(FILE_PATH);
        dirPath.toFile().mkdirs();
        var file = dirPath.resolve(Paths.get(user + ".txt")).toFile();
        try {
            if (!file.exists()) {
                boolean created = file.createNewFile();
                if (!created) {
                    throw new RuntimeException("couldn't create a file: " + file.getAbsolutePath());
                }
            }
            boolean visitedAlready = isVisited(file, newWaypoint);
            if (!visitedAlready) {
                String writeLine = newWaypoint.id() + " " +
                    newWaypoint.latLon().lat() + " " +
                    newWaypoint.latLon().lon() + "\n";
                Files.writeString(file.toPath(), writeLine, StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isVisited(String user, WayPoint waypoint) {
        var file = Paths.get(FILE_PATH).resolve(Paths.get(user + ".txt")).toFile();
        if (!file.exists()) {
            return false;
        }
        try {
            return isVisited(file, waypoint);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isVisited(File file, WayPoint newWaypoint) throws IOException {
        return Files.readAllLines(file.toPath())
            .stream()
            .map(line -> {
                var splitLine = line.split(" ");
                if (splitLine.length != 3) {
                    throw new IllegalStateException("line in file is wrong: " + line);
                }
                return new WayPoint(Long.parseLong(splitLine[0]),
                    new LatLon(Double.parseDouble(splitLine[1]), Double.parseDouble(splitLine[2])), "");
            })
            .anyMatch(wayPoint -> {
                return wayPoint.id() == newWaypoint.id() &&
                    wayPoint.latLon().equals(newWaypoint.latLon());
            });
    }
}
