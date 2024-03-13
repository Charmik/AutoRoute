package com.autoroute.sight;

import com.autoroute.logistic.LatLon;

public record Sight(long id, LatLon latLon, String name, int rating) {

}
