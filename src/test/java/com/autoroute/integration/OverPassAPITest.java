package com.autoroute.integration;

import com.autoroute.Constants;
import com.autoroute.api.overpass.Box;
import com.autoroute.api.overpass.OverPassAPI;
import com.autoroute.osm.LatLon;
import com.autoroute.osm.Tag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Set;
import java.util.concurrent.TimeUnit;

class OverPassAPITest {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void getThreeNodesInSosnoviyBor() {
        boolean success = false;
        // dirty hack to test API. it fails sometimes because of timeout. Fix when stub-API will be implemented
        for (int i = 0; i < 3; i++) {
            try {
                var api = new OverPassAPI();

                double diffDegree = ((double) 150 / Constants.KM_IN_ONE_DEGREE) / 2;
                final LatLon startPoint = new LatLon(34.690139, 32.987961);
                final Box box = new Box(
                    startPoint.lat() - diffDegree,
                    startPoint.lon() - diffDegree,
                    startPoint.lat() + diffDegree,
                    startPoint.lon() + diffDegree
                );


                final Set<Tag> tags = Set.of(new Tag("historic", "castle"), new Tag("historic", "cannon"));
                final var overpassResponse = api.getNodesInBoxByTags(box, tags);
                /*
                Assertions.assertEquals(3, overpassResponse.size());
                var sortedByName = overpassResponse.stream()
                    .sorted(Comparator.comparing(OverpassResponse::getName))
                    .toList();

                Assertions.assertEquals("Б-13", sortedByName.get(0).getName());
                Assertions.assertEquals(59.9, sortedByName.get(0).latLon().lat(), 0.1);
                Assertions.assertEquals(29.3, sortedByName.get(0).latLon().lon(), 0.1);

                Assertions.assertEquals("ЗАГС", sortedByName.get(1).getName());
                Assertions.assertEquals("Усадьба Блюментростови фон Герсдорфов", sortedByName.get(2).getName());
                success = true;
                 */
            } catch (Throwable ignored) {
            }
        }
        Assertions.assertTrue(success);
    }
}