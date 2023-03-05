package com.autoroute.api.overpass;

public record Box(double x1, double y1, double x2, double y2) {

    @Override
    public String toString() {
        return x1 + " " + y1 + " " + x2 + " " + y2;
    }
}
