package com.autoroute.api.osrm.services;

import org.json.JSONException;

public class TooManyCoordinatesException extends Exception {
    public TooManyCoordinatesException(String s, JSONException e) {
        super(s, e);
    }
}
