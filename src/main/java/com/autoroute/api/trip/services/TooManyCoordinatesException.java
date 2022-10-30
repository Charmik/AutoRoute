package com.autoroute.api.trip.services;

public class TooManyCoordinatesException extends Exception {

    public TooManyCoordinatesException(String message) {
        super(message);
    }

}
