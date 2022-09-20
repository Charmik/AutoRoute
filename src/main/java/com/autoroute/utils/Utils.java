package com.autoroute.utils;

import com.autoroute.osm.LatLon;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Utils {

    public static Integer parseInteger(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static double percent(double part, double max) {
        if (part < 0 || max < 0 || part > max) {
            throw new IllegalArgumentException("wrong arguments for percentage: " + part + " " + max);
        }
        return part / (max / 100);
    }

    public static Path pathForRoute(LatLon startPoint, int minDistance, int maxDistance) {
        String str = minDistance + "_" + maxDistance + "_" + startPoint;
        return Paths.get("tracks").resolve(str);
    }
}
