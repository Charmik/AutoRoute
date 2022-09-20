package com.autoroute;

import com.autoroute.osm.LatLon;
import com.autoroute.utils.Utils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

class UtilsTest {

    @Test
    void parseInteger() {
        Assertions.assertEquals(5, Utils.parseInteger("5"));
        assertNull(Utils.parseInteger("qwe"));
        assertNull(Utils.parseInteger("2 5"));
        assertNull(Utils.parseInteger("X5"));
    }

    @Test
    void percent() {
        final double delta = 0.01;
        Assertions.assertEquals(20d, Utils.percent(20, 100), delta);
        Assertions.assertEquals(10d, Utils.percent(20, 200), delta);
        Assertions.assertEquals(33.3333333d, Utils.percent(3, 9), delta);

        try {
            Assertions.assertEquals(20d, Utils.percent(-5, 100), delta);
            fail("should fail");
        } catch (IllegalArgumentException e) {
            Assertions.assertTrue(e.getMessage().contains("wrong arguments"));
        }

        try {
            Assertions.assertEquals(20d, Utils.percent(20, -5), delta);
            fail("should fail");
        } catch (IllegalArgumentException e) {
            Assertions.assertTrue(e.getMessage().contains("wrong arguments"));
        }

        try {
            Assertions.assertEquals(20d, Utils.percent(200, 100), delta);
            fail("should fail");
        } catch (IllegalArgumentException e) {
            Assertions.assertTrue(e.getMessage().contains("wrong arguments"));
        }
    }

    @Test
    public void pathForRouteTest() {
        var point = new LatLon(5.5, 10.6);
        int min = 100;
        int max = 200;
        String expected = "tracks/100_200_5.5_10.6";

        final Path resPath = Utils.pathForRoute(point, min, max);
        Assertions.assertEquals(expected, resPath.toString());
    }
}