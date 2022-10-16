package com.autoroute.logistic.rodes;

class GraphTest {
/*
    private int id = 0;

    @BeforeEach
    void setUp() {
        id = 0;
    }

    private Vertex create(double lat, double lon) {
        id++;
        return new Vertex(id, id, new LatLon(lat, lon));
    }

    @Test
    void dfs() {
    }

    @Test
    void findNearestVertex() {
        List<Vertex> l = new ArrayList<>();
        l.add(create(0, 0));
        l.add(create(10, 10));
        l.add(create(100, 100));
        final Graph g = new Graph(l, MIN_KM, MAX_KM);

        final Vertex v1 = g.findNearestVertex(new LatLon(1, 1));
        Assertions.assertEquals(l.get(0), v1);

        final Vertex v2 = g.findNearestVertex(new LatLon(15, 15));
        Assertions.assertEquals(l.get(1), v2);

        final Vertex v3 = g.findNearestVertex(new LatLon(110, 95));
        Assertions.assertEquals(l.get(2), v3);
    }

    @Test
    void buildFast() {
    }

    @Test
    void eliminatesNodes() {
    }

    @Test
    void iterateOverPotentialNeighbors() {
    }

    @Test
    void eliminatesNodesSlow() {
    }

    @Test
    void buildSlow() {
        List<Vertex> l = new ArrayList<>();
        l.add(create(0, 0));
        l.add(create(0, 1));
        l.add(create(0, 25));
        l.add(create(0, 26));
        l.add(create(0, 50));
        l.add(create(0, 51));

        var d = LatLon.distanceKM(l.get(0).getLatLon(), l.get(2).getLatLon()) / 2;

        final Graph g = new Graph(l, MIN_KM, MAX_KM);
        g.buildSlow(d * 3);
        final List<Vertex> vertices = g.getVertices();
        var v1 = vertices.stream().filter(v -> v.getId() == 1).findAny().get();
        var v2 = vertices.stream().filter(v -> v.getId() == 2).findAny().get();
        var v3 = vertices.stream().filter(v -> v.getId() == 3).findAny().get();
        var v4 = vertices.stream().filter(v -> v.getId() == 4).findAny().get();
        var v5 = vertices.stream().filter(v -> v.getId() == 5).findAny().get();
        var v6 = vertices.stream().filter(v -> v.getId() == 6).findAny().get();

        Assertions.assertEquals(3, v1.getNeighbors().size());
        Assertions.assertEquals(3, v2.getNeighbors().size());
        Assertions.assertEquals(5, v3.getNeighbors().size());
        Assertions.assertEquals(5, v4.getNeighbors().size());
        Assertions.assertEquals(3, v5.getNeighbors().size());
        Assertions.assertEquals(3, v6.getNeighbors().size());

        Assertions.assertTrue(v1.getNeighbors().contains(v2));
        Assertions.assertTrue(v1.getNeighbors().contains(v3));
        Assertions.assertTrue(v1.getNeighbors().contains(v4));
        Assertions.assertTrue(v2.getNeighbors().contains(v1));
        Assertions.assertTrue(v2.getNeighbors().contains(v3));
        Assertions.assertTrue(v2.getNeighbors().contains(v4));

        Assertions.assertTrue(v3.getNeighbors().contains(v1));
        Assertions.assertTrue(v3.getNeighbors().contains(v2));
        Assertions.assertTrue(v3.getNeighbors().contains(v4));
        Assertions.assertTrue(v3.getNeighbors().contains(v5));
        Assertions.assertTrue(v3.getNeighbors().contains(v6));

        Assertions.assertTrue(v4.getNeighbors().contains(v1));
        Assertions.assertTrue(v4.getNeighbors().contains(v2));
        Assertions.assertTrue(v4.getNeighbors().contains(v3));
        Assertions.assertTrue(v4.getNeighbors().contains(v5));
        Assertions.assertTrue(v4.getNeighbors().contains(v6));

        Assertions.assertTrue(v5.getNeighbors().contains(v3));
        Assertions.assertTrue(v5.getNeighbors().contains(v4));
        Assertions.assertTrue(v5.getNeighbors().contains(v6));
        Assertions.assertTrue(v6.getNeighbors().contains(v3));
        Assertions.assertTrue(v6.getNeighbors().contains(v4));
        Assertions.assertTrue(v6.getNeighbors().contains(v5));

    }

    @Test
    void mergeNeighboursSeparateClusters() {

        List<Vertex> l = new ArrayList<>();
        l.add(create(0, 0));
        l.add(create(1, 1));
        l.add(create(50, 50));
        l.add(create(51, 51));

        var d = LatLon.distanceKM(l.get(1).getLatLon(), l.get(2).getLatLon()) / 2;

        final Graph g = new Graph(l, MIN_KM, MAX_KM);
        g.buildSlow(d);
        g.mergeNeighbours(1, d);

        final List<Vertex> vertices = g.getVertices();
        Assertions.assertEquals(2, vertices.size());
        Assertions.assertEquals(4, l.size());
        for (Vertex v : vertices) {
            for (Vertex u : l) {
                if (v.getLatLon().equals(u.getLatLon())) {
                    l.remove(u);
                    break;
                }
            }
        }
        Assertions.assertEquals(2, l.size());
    }

    @Test
    void mergeEdges() {
        List<Vertex> l = new ArrayList<>();
        l.add(create(0, 0));
        l.add(create(0, 1));
        l.add(create(0, 25));
        l.add(create(0, 26));
        l.add(create(0, 50));
        l.add(create(0, 51));

        // we need to split distance by 3, because every vertex has 3 neighbours
        var d = (LatLon.distanceKM(l.get(0).getLatLon(), l.get(2).getLatLon()) / 2) / 3;
        final Graph g = new Graph(l, MIN_KM, MAX_KM);
        g.buildSlow(d * 3);
        Assertions.assertEquals(6, g.getVertices().size());
        g.mergeNeighbours(1, d);

        final List<Vertex> vertices = g.getVertices();
        Assertions.assertEquals(3, vertices.size());
        Assertions.assertEquals(6, l.size());
        var v2 = vertices.stream().filter(v -> v.getIdentificator() == 1).findAny().get();
        Assertions.assertEquals(l.get(0), v2);
        var v4 = vertices.stream().filter(v -> v.getIdentificator() == 3).findAny().get();
        Assertions.assertEquals(l.get(2), v4);
        var v6 = vertices.stream().filter(v -> v.getIdentificator() == 5).findAny().get();
        Assertions.assertEquals(l.get(4), v6);
    }

 */
}