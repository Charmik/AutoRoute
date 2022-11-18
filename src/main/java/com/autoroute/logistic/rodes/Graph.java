package com.autoroute.logistic.rodes;

import com.autoroute.logistic.LogisticUtils;
import com.autoroute.logistic.rodes.dijkstra.DijkstraAlgorithm;
import com.autoroute.logistic.rodes.dijkstra.DijkstraCache;
import com.autoroute.osm.LatLon;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class Graph {

    private static final Logger LOGGER = LogManager.getLogger(Graph.class);

    private List<Vertex> vertices;
    Long2ObjectOpenHashMap<Vertex> identificatorToVertex = null;
    private List<Vertex> superVertices = null;
    private final int maxKM;
    private final int minKM;
    private final Random random = new Random(42);
    private Graph fullGraph = null;

    public Graph(List<Vertex> vertices, int minKM, int maxKM) {
        assert !vertices.isEmpty();
        this.vertices = new ArrayList<>(vertices);
        this.minKM = minKM;
        this.maxKM = maxKM;
        LOGGER.info("created graph with: {} vertices", vertices.size());
        fastUpdateIds();
    }

    public void checkGraph(long identificatorStartVertex) {
        for (Vertex v : vertices) {
            assert !v.getNeighbors().contains(v);
            for (Vertex u : v.getNeighbors()) {
                assert u.getId() < vertices.size();
                assert v.getNeighbors().contains(u);
                assert u.getNeighbors().contains(v);
                assert !u.getNeighbors().contains(u);
            }
        }
        assert vertices.stream().filter(e -> e.getIdentificator() == identificatorStartVertex).toList().size() == 1;
    }

    public void addEdgesFromStartPoint(Vertex startVertex, double minDistance) {
        for (Vertex v : vertices) {
            if (v.getIdentificator() == startVertex.getIdentificator()) {
                continue;
            }
            final double d = LatLon.distanceKM(startVertex.getLatLon(), v.getLatLon());
            if (d < minDistance) {
                v.addNeighbor(startVertex);
                startVertex.addNeighbor(v);
                checkGraph(startVertex.getIdentificator());
            }
        }
    }

    public void removeNotVisitedVertexes(Vertex start) {
        boolean[] visit = new boolean[vertices.size()];
        bfsVisit(start, visit);
        List<Vertex> newVertices = new ArrayList<>();
        for (final Vertex v : vertices) {
            if (!visit[v.getId()]) {
                for (Vertex k : v.getNeighbors()) {
                    k.removeNeighbor(v);
                }
            } else {
                newVertices.add(v);
            }
        }
        vertices = newVertices;
        updateIds();
    }

    private void bfsVisit(Vertex start, boolean[] visit) {
        visit[start.getId()] = true;
        ArrayList<Vertex> queue = new ArrayList<>(vertices.size()); // instead of Queue for perf
        queue.add(start);
        int pointer = 0;

        while (pointer < queue.size()) {
            final Vertex v = queue.get(pointer);
            final List<Vertex> neighbors = v.getNeighbors();
            for (int i = 0; i < neighbors.size(); i++) {
                var u = neighbors.get(i);
                if (!visit[u.getId()]) {
                    visit[u.getId()] = true;
                    queue.add(u);
                }
            }
            pointer++;
        }
    }

    private double distance(Vertex v, Vertex u) {
        return LatLon.distanceKM(v.getLatLon(), u.getLatLon());
    }

    public void calculateDistanceForNeighbours() {
        for (Vertex v : vertices) {
            v.calculateDistance();
        }
    }

    // TODO: move it out from Graph to some CycleAlgorithm which works with the graph.
    public void findAllCycles(Vertex startVertex, List<Cycle> result, DijkstraAlgorithm dijkstra, DijkstraCache dijkstraCache) {
        Vertex[] prev = new Vertex[vertices.size()];
        Arrays.fill(prev, null);
        boolean[] visited = new boolean[vertices.size()];
        Arrays.fill(visited, false);
        visited[startVertex.getId()] = true;

        LinkedList<Vertex> stack = new LinkedList<>(); // TODO: replace to ArrayList for perf
        stack.add(startVertex);

        while (!stack.isEmpty()) { // dfs without recursion
            final Vertex v = stack.removeFirst();
            visited[v.getId()] = true;

            final List<Vertex> neighbors = v.getNeighbors();
            Collections.shuffle(neighbors, random);

            for (int i = 0; i < neighbors.size(); i++) {
                Vertex u = neighbors.get(i);
                if (!visited[u.getId()]) {
                    prev[u.getId()] = v;
                    stack.addFirst(u);
                } else if (prev[v.getId()].getId() != u.getId()) {
                    tryAddNewCycle(startVertex, result, prev, v, u, dijkstra, dijkstraCache);
                }
            }
        }
    }

    private void tryAddNewCycle(Vertex startVertex, List<Cycle> result,
                                Vertex[] prev, Vertex v, Vertex u, DijkstraAlgorithm dijkstra, DijkstraCache dijkstraCache) {

        var cycle = getCycle(prev, v, u);
        if (cycle == null) {
            return;
        }

        if (cycle.tryAddCycle(fullGraph, startVertex, result, dijkstra, minKM, maxKM, dijkstraCache)) {

        } else {
            // TODO: get some result from tryAddCycle. if distance too big - don't go deeper in dfs?
        }
    }

    @Nullable
    private static Cycle getCycle(Vertex[] prev, Vertex from, Vertex to) {
        List<Vertex> cycle = new ArrayList<>();
        cycle.add(to);
        Vertex k = from;
        while (k != null && k.getId() != to.getId()) {
            cycle.add(k);
            k = prev[k.getId()];
        }
        if (k == null) {
            return null;
        }
        cycle.add(to);
        return new Cycle(cycle);
    }

    public Vertex findNearestVertex(LatLon latLon) {
        return LogisticUtils.findNearestVertex(latLon, vertices);
    }

    public void removeEdges(long identificatorStartVertex) {
        for (int i = 0; i < vertices.size(); i++) {
            var v = vertices.get(i);
            if (i % 5000 == 0) {
                LOGGER.info("removeEdges: {}/{}", i, vertices.size());
            }
            List<Vertex> neighbors = new ArrayList<>(v.getNeighbors());
            if (neighbors.size() < 3 || v.getIdentificator() == identificatorStartVertex) {
                continue;
            }
            neighbors.sort((x, y) -> {
                var d1 = LatLon.fastDistance(v.getLatLon(), x.getLatLon());
                var d2 = LatLon.fastDistance(v.getLatLon(), y.getLatLon());
                return Double.compare(d2, d1);
            });

            for (Vertex u : neighbors) {
                if (u.getNeighbors().size() < 3 || u.getIdentificator() == identificatorStartVertex) {
                    continue;
                }
                final int depthLimit = 5;
                var distancesBeforeDelete = bfsDistance(v, depthLimit + 1);
                assert distancesBeforeDelete.getDistance()[u.getId()] == 1;
                assert distancesBeforeDelete.getParents()[u.getId()].equals(v);

                v.removeNeighbor(u);
                u.removeNeighbor(v);

                var newDistancesFromV = bfsDistance(v, depthLimit);
                var d = newDistancesFromV.getDistance()[u.getId()];

                List<Vertex> path = new ArrayList<>();
                Vertex k = u;
                while (k != null && !k.equals(v)) {
                    path.add(k);
                    k = newDistancesFromV.parents[k.getId()];
                }
                path.add(v);
                Collections.reverse(path);


                if (d == -1 || d > depthLimit
                ) {
                    v.addNeighbor(u);
                    u.addNeighbor(v);
                }
            }
        }
    }

    public void setFullGraph(Graph fullGraph) {
        assert this.fullGraph == null;
        this.fullGraph = fullGraph;
    }

    static class BfsResult {
        int[] distance;
        Vertex[] parents;

        public BfsResult(int[] distance, Vertex[] parents) {
            this.distance = distance;
            this.parents = parents;
        }

        public int[] getDistance() {
            return distance;
        }

        public Vertex[] getParents() {
            return parents;
        }
    }

    private BfsResult bfsDistance(Vertex start, int depthLimit) {
        int[] distance = new int[vertices.size()];
        Vertex[] parents = new Vertex[vertices.size()];
        Arrays.fill(distance, -1);
        distance[start.getId()] = 0;
        int pointer = 0;
        ArrayList<Vertex> queue = new ArrayList<>(vertices.size()); // instead of Queue for perf
        queue.add(start);
        while (pointer < queue.size()) {
            final Vertex v = queue.get(pointer);
            final List<Vertex> neighbors = v.getNeighbors();
            for (int i = 0; i < neighbors.size(); i++) {
                var u = neighbors.get(i);
                if (distance[u.getId()] == -1) {
                    final int newDistance = distance[v.getId()] + 1;
                    if (newDistance >= depthLimit) {
                        return new BfsResult(distance, parents);
                    }
                    distance[u.getId()] = newDistance;
                    parents[u.getId()] = v;
                    queue.add(u);
                }
            }
            pointer++;
        }
        return new BfsResult(distance, parents);
    }


    public void removeSingleEdgeVertexes(long identificatorStartVertex) {
        boolean[] deleted = new boolean[vertices.size()];
        boolean progress;
//        do {
        progress = false;
        for (Vertex v : vertices) {
            if (v.getIdentificator() == identificatorStartVertex) {
                continue;
            }
            if (v.getNeighbors().isEmpty()) {
                deleted[v.getId()] = true;
                progress = true;
            } else if (v.getNeighbors().size() == 1) {
                v.getNeighbors().iterator().next().removeNeighbor(v);
                deleted[v.getId()] = true;
                progress = true;
            }
        }
        List<Vertex> newVertices = new ArrayList<>(vertices.size() / 2);
        for (int i = 0; i < vertices.size(); i++) {
            Vertex v = vertices.get(i);
            if (!deleted[v.getId()]) {
                newVertices.add(v);
            }
        }
        vertices = newVertices;
        updateIds();
        LOGGER.info("made iteration in removeSingleEdgeVertexes");
//        } while (progress);
    }

    public void calculateSuperVertices() {
        superVertices = vertices.stream()
                .filter(Vertex::isSuperVertex)
                .toList();
    }

    public List<Vertex> getVertices() {
        return vertices;
    }

    public void buildIdentificatorToVertexMap() {
        assert identificatorToVertex == null;
        identificatorToVertex = new Long2ObjectOpenHashMap<>(vertices.size());
        for (Vertex v : vertices) {
            identificatorToVertex.put(v.getIdentificator(), v);
        }
    }

    public Vertex findByIdentificator(long identificator) {
        assert identificatorToVertex != null;
        final Vertex v = identificatorToVertex.get(identificator);
        assert v != null;
        return v;
    }

    public int size() {
        return vertices.size();
    }

    private void fastUpdateIds() {
        for (int i = 0; i < vertices.size(); i++) {
            vertices.get(i).setId(i);
        }
    }

    private void updateIds() {
        for (int i = 0; i < vertices.size(); i++) {
            final Vertex v = vertices.get(i);
            Vertex newV = new Vertex(v);
            newV.setId(i);
            for (Vertex neighbor : v.getNeighbors()) {
                assert neighbor.getId() != v.getId();
                if (neighbor.removeNeighbor(v)) {
                    assert neighbor.getId() != newV.getId();
                    neighbor.addNeighbor(newV);
                }
            }
            v.setId(i);
            vertices.set(i, newV);
        }
    }

    // TODO: make parallel
    public void removeCloseVertexes(double distance, long identificatorStartVertex) {
        int tries = 0;
        int iteration = 0;
        while (true) {
            iteration++;
            Vertex bestVertex = null;
            int countMaxNeighbours = 0;

            Vertex randomVertex = vertices.get(random.nextInt(vertices.size()));
            final LatLon randomLatLon = randomVertex.getLatLon();

            Vertex[] verticesArr = vertices.toArray(new Vertex[0]);
            Arrays.parallelSort(verticesArr, (o1, o2) -> {
                double distance1 = LatLon.fastDistance(o1.getLatLon(), randomLatLon);
                double distance2 = LatLon.fastDistance(o2.getLatLon(), randomLatLon);
                return Double.compare(distance1, distance2);
            });
            List<Vertex> sortVertices = Arrays.asList(verticesArr);

            for (int m = 0; m < sortVertices.size(); m++) {
                int count = 0;
                int i = m - 1;
                int j = m + 1;
                final Vertex midV = sortVertices.get(m);
                while (i >= 0
                        && distance(sortVertices.get(i), midV) < distance) {
                    count++;
                    i--;
                }
                while (j < sortVertices.size()
                        && distance(sortVertices.get(j), midV) < distance) {
                    count++;
                    j++;
                }
                if (count > countMaxNeighbours && midV.getIdentificator() != identificatorStartVertex) {
                    countMaxNeighbours = count;
                    bestVertex = midV;
                }
            }
            if (bestVertex == null || countMaxNeighbours < 75) {
                // TODO: make less? log, maybe 5 is enough?
                if (tries == 50) {
                    break;
                } else {
                    tries++;
                    continue;
                }
            }
            assert bestVertex.getIdentificator() != identificatorStartVertex;
            tries = 0;
            // LOGGER.info("delete neighbours: {} now have: {}", countMaxNeighbours, vertices.size());
            final Vertex v = bestVertex;
            v.setSuperVertex();
            Set<Vertex> closeVertexes = new HashSet<>();
            for (int j = 0; j < vertices.size(); j++) {
                Vertex u = vertices.get(j);
                if (v.getId() == u.getId() || u.getIdentificator() == identificatorStartVertex) {
                    continue;
                }
                if (distance(v, u) < distance) {
                    closeVertexes.add(u);
                }
            }
//            assert closeVertexes.size() == countMaxNeighbours; // doesn't work with sort algorithm
            for (Vertex u : closeVertexes) {
                for (Vertex neighbor : u.getNeighbors()) {
                    if (v.getId() == neighbor.getId()) {
                        continue;
                    }
                    neighbor.removeNeighbor(u);
                    neighbor.addNeighbor(v);
                    v.addNeighbor(neighbor);
                }
            }
            deleteVerticesFromGraph(closeVertexes);
            if (iteration % 50 == 0) {
                LOGGER.info("graph removed some vertexes, now: {}", vertices.size());
            }
        }
    }

    private void deleteVerticesFromGraph(Collection<Vertex> deleteVertices) {
        for (Vertex v : vertices) {
            List<Vertex> deleteForV = new ArrayList<>();
            for (Vertex u : v.getNeighbors()) {
                if (deleteVertices.contains(u)) {
                    deleteForV.add(u);
                }
            }
            v.getNeighbors().removeAll(deleteForV);
        }
        vertices.removeAll(deleteVertices);
        updateIds();
    }

}
