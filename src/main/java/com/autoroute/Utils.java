package com.autoroute;

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
}
