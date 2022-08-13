package com.autoroute.integration;

import com.autoroute.api.overpass.Box;
import com.autoroute.api.overpass.OverPassAPI;
import com.autoroute.api.overpass.OverpassResponse;
import com.autoroute.osm.Tag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

class OverPassAPITest {

    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    void getThreeNodesInSosnoviyBor() {
        boolean success = false;
        // dirty hack to test API. it fails sometimes because of timeout. Fix when stub-API will be implemented
        for (int i = 0; i < 3; i++) {
            try {
                var api = new OverPassAPI();
                final Box box = new Box(59.369783, 28.577752, 59.982578, 29.842246);
                final List<Tag> tags = List.of(new Tag("historic", "castle"), new Tag("historic", "cannon"));
                final var overpassResponse = api.GetNodesInBoxByTags(box, tags);

                Assertions.assertEquals(3, overpassResponse.size());
                var sortedByName = overpassResponse.stream()
                    .sorted(Comparator.comparing(OverpassResponse::name))
                    .toList();

                Assertions.assertEquals("Б-13", sortedByName.get(0).name());
                Assertions.assertEquals(59.9, sortedByName.get(0).latLon().lat(), 0.1);
                Assertions.assertEquals(29.3, sortedByName.get(0).latLon().lon(), 0.1);

                Assertions.assertEquals("ЗАГС", sortedByName.get(1).name());
                Assertions.assertEquals("Усадьба Блюментростови фон Герсдорфов", sortedByName.get(2).name());
                success = true;
            } catch (Throwable ignored) {
            }
        }
        Assertions.assertTrue(success);
    }
}