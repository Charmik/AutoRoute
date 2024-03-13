package com.autoroute.api.overpass;

import com.autoroute.logistic.rodes.Vertex;
import com.autoroute.logistic.LatLon;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapperTest {

    static class Params implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                Arguments.of(true),
                Arguments.of(false)
            );
        }
    }

    @ParameterizedTest
    @ArgumentsSource(Params.class)
    void testNeighbors(boolean isBig) {
        OverpassResponse r = buildTwoNeigborsNodes();

        List<Vertex> vertices = sortById(Mapper.getVertices(r, false, isBig));
        Vertex v1 = vertices.get(0);
        Vertex v2 = vertices.get(1);

        assertEquals(v1.getNeighbors().size(), 1);
        assertEquals(v2.getNeighbors().size(), 1);

        assertTrue(v1.containsNeighbor(v2));
        assertTrue(v2.containsNeighbor(v1));

        assertEquals(0, v1.getIdentificator());
        assertEquals(1, v2.getIdentificator());
    }

    @ParameterizedTest
    @ArgumentsSource(Params.class)
    void testRemoveNeighbor(boolean isBig) {
        OverpassResponse r = buildTwoNeigborsNodes(true);

        List<Vertex> vertices = sortById(Mapper.getVertices(r, false, isBig));
        Vertex v1 = vertices.get(0);
        Vertex v2 = vertices.get(1);
        Vertex v3 = vertices.get(2);

        assertEquals(v1.getNeighbors().size(), 2);
        assertEquals(v2.getNeighbors().size(), 2);
        assertEquals(v3.getNeighbors().size(), 2);

        v1.removeNeighbor(v2);
        assertEquals(v1.getNeighbors().size(), 1);
        assertEquals(v2.getNeighbors().size(), 2);
        assertEquals(v3.getNeighbors().size(), 2);

        v1.removeNeighbor(v3);
        assertEquals(v1.getNeighbors().size(), 0);
        assertEquals(v2.getNeighbors().size(), 2);
        assertEquals(v3.getNeighbors().size(), 2);
    }

    @ParameterizedTest
    @ArgumentsSource(Params.class)
    void testNotExistedNodeIdInWayDontBreak(boolean isBig) {
        OverpassResponse r = buildTwoNeigborsNodes();
        r.add(new Way(5, new long[]{666}, null));

        List<Vertex> vertices = sortById(Mapper.getVertices(r, false, isBig));
        Vertex v1 = vertices.get(0);
        Vertex v2 = vertices.get(1);

        assertEquals(v1.getNeighbors().size(), 1);
        assertEquals(v2.getNeighbors().size(), 1);

        assertTrue(v1.containsNeighbor(v2));
        assertTrue(v2.containsNeighbor(v1));

        assertEquals(0, v1.getIdentificator());
        assertEquals(1, v2.getIdentificator());
    }

    @ParameterizedTest
    @ArgumentsSource(Params.class)
    void testNotClearResponse(boolean isBig) {
        OverpassResponse r = buildTwoNeigborsNodes();

        List<Vertex> vertices = sortById(Mapper.getVertices(r, false, isBig));
        Vertex v1 = vertices.get(0);
        Vertex v2 = vertices.get(1);

        assertEquals(v1.getNeighbors().size(), 1);
        assertEquals(v2.getNeighbors().size(), 1);

        assertEquals(r.getWays().size(), 1);
        assertEquals(r.getNodes().size(), 2);
    }

    @ParameterizedTest
    @ArgumentsSource(Params.class)
    void testClearResponse(boolean isBig) {
        OverpassResponse r = buildTwoNeigborsNodes();

        List<Vertex> vertices = sortById(Mapper.getVertices(r, true, isBig));
        Vertex v1 = vertices.get(0);
        Vertex v2 = vertices.get(1);

        assertEquals(v1.getNeighbors().size(), 1);
        assertEquals(v2.getNeighbors().size(), 1);

        assertTrue(r.getWays().isEmpty());
        assertTrue(r.getNodes().isEmpty());
    }

    private List<Vertex> sortById(List<Vertex> vertices) {
        return vertices
            .stream()
            .sorted(Comparator.comparingLong(Vertex::getIdentificator))
            .toList();
    }

    private static OverpassResponse buildTwoNeigborsNodes() {
        return buildTwoNeigborsNodes(false);
    }

    private static OverpassResponse buildTwoNeigborsNodes(boolean addThirdNode) {
        OverpassResponse r = new OverpassResponse();
        r.add(new Node(0, new String[0], new String[0], new LatLon(0, 0)));
        r.add(new Node(1, new String[0], new String[0], new LatLon(1, 1)));
        if (addThirdNode) {
            r.add(new Node(2, new String[0], new String[0], new LatLon(2, 2)));
        }
        if (addThirdNode) {
            r.add(new Way(0, new long[]{0, 1}, null));
            r.add(new Way(1, new long[]{1, 2}, null));
            r.add(new Way(2, new long[]{0, 2}, null));
        } else {
            r.add(new Way(0, new long[]{0, 1}, null));
        }
        return r;
    }
}