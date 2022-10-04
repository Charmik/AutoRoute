package com.autoroute.osm;

import com.autoroute.Constants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LatLonTest {

    @Test
    void ClosePoints() {
        LatLon p1 = new LatLon(0, 0);
        LatLon p2 = new LatLon(0.0001, 0);
        Assertions.assertTrue(p1.isCloseInCity(p2));
        Assertions.assertTrue(p1.isClosePoint(p2));
    }

    @Test
    void isClosePointNegative() {
        LatLon p1 = new LatLon(0, 0);
        LatLon p2 = new LatLon(5, 0);
        Assertions.assertFalse(p1.isCloseInCity(p2));
        Assertions.assertFalse(p1.isClosePoint(p2));
    }

    @Test
    void isClosePointDifferent() {
        LatLon p1 = new LatLon(0, 0);
        LatLon p2 = new LatLon(0.01, 0);
        Assertions.assertTrue(p1.isCloseInCity(p2));
        Assertions.assertFalse(p1.isClosePoint(p2));
    }

    @Test
    void fastDistance() {
        LatLon p1 = new LatLon(2, 3);
        LatLon p2 = new LatLon(10, 12);
        final double d = LatLon.fastDistance(p1, p2);
        Assertions.assertEquals(145, d, 0.1);
    }

    @Test
    void distanceKM() {
        LatLon p1 = new LatLon(0, 0);
        LatLon p2 = new LatLon(1, 0);
        LatLon p3 = new LatLon(0, 1);
        final double d1 = LatLon.distanceKM(p1, p2);
        final double d2 = LatLon.distanceKM(p1, p3);
        Assertions.assertEquals(Constants.KM_IN_ONE_DEGREE, d1, 1);
        Assertions.assertEquals(Constants.KM_IN_ONE_DEGREE, d2, 1);
    }
}