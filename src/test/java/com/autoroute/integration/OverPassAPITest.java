package com.autoroute.integration;

import com.autoroute.api.overpass.Box;
import com.autoroute.api.overpass.Node;
import com.autoroute.api.overpass.OverPassAPI;
import com.autoroute.osm.LatLon;
import com.autoroute.osm.Tag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

class OverPassAPITest {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void getThreeNodesInSosnoviyBor() {
        var api = new OverPassAPI(new LatLon(59.369783, 28.577752), 200);
        final Box box = new Box(59.369783, 28.577752, 59.982578, 29.842246);

        final Set<Tag> tags = Set.of(new Tag("historic", "castle"), new Tag("historic", "cannon"));
        final var overpassResponse = api.getNodesInBoxByTags(box, tags);

        final List<Node> nodes = overpassResponse.getNodes();

        Assertions.assertEquals(2, nodes.size());
        var sortedByName = nodes.stream()
            .sorted(Comparator.comparing(Node::getName))
            .toList();

        Assertions.assertEquals("Б-13", sortedByName.get(0).getName());
        Assertions.assertEquals(59.9, sortedByName.get(0).latLon().lat(), 0.1);
        Assertions.assertEquals(29.3, sortedByName.get(0).latLon().lon(), 0.1);
        Assertions.assertEquals("Усадьба Блюментростови фон Герсдорфов", sortedByName.get(1).getName());
    }
}