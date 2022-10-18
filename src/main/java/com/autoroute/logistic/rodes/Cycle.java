package com.autoroute.logistic.rodes;

import com.autoroute.osm.LatLon;
import com.autoroute.utils.Utils;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record Cycle(List<Vertex> vertices) {

    private static final Logger LOGGER = LogManager.getLogger(Cycle.class);

    private static int COUNT = 0;

    public boolean tryAddCycle(Graph g, Graph fullGraph, Vertex startVertex, List<Cycle> result,
                               DijkstraAlgorithm dijkstra, int minKM, int maxKM) {
        final double distanceToCycle = minDistanceToCycle(startVertex, dijkstra);
        final double cycleDistance = getCycleDistance(vertices);
        double routeDistance = distanceToCycle * 2 + cycleDistance;
        final int superVertexes = countSuperVertexes();

        if (cycleDistance > 40 && cycleDistance < 50 && distanceToCycle < 3) {
            Utils.writeGPX(vertices, "ZZZ", result.size());
            LOGGER.info("was found cycle: {}", result.size());
        }

        if (1 == 1
            && (routeDistance > minKM && routeDistance < maxKM)
            && (cycleDistance > minKM / 2)
            && (cycleDistance > routeDistance / 100 * 30)
            && !isInCity(countSuperVertexes())
            // TODO: && don't cross yourself
        ) {
//            LOGGER.info("index: {} length: {}", result.size(), vertices.size());
            removeExternalCycles(cycleDistance);
            if (isInCity(countSuperVertexes())) {
                return false;
            }
            // TODO: create Routa class. save small cycle too - check it for duplicate, not from full graph.
            if (!hasDuplicate(g, result) && !getReversedVertices().hasDuplicate(g, result)) {
                Utils.writeGPX(vertices, "cycles/1_", result.size());
                LOGGER.info("index: {}, distanceToCycle: {}, cycleDistance: {}, routeDistance: {}, superVertexes: {} cycle_length: {}",
                    result.size(), distanceToCycle, cycleDistance, routeDistance, superVertexes, size());

//                cycle.isGood();
//                cycle.hasAnInternalCycle();

                replaceSuperVertexesInPath(fullGraph);
                List<Vertex> fullGraphVertexes = new ArrayList<>();
                for (Vertex v : vertices) {
                    fullGraphVertexes.add(fullGraph.findByIdentificator(v.getIdentificator()));
                }

                Cycle fullCycle = new Cycle(fullGraphVertexes);

                Utils.writeGPX(fullCycle.vertices, "cycles/2_", result.size());
                fullCycle.removeExternalCycles(cycleDistance);
                Utils.writeGPX(fullCycle.vertices, "cycles/3_", result.size());
                result.add(fullCycle);
                return true;
            }
        }
        return false;
    }

    private boolean isInCity(int superVertexes) {
        boolean inCity = false;
        if ((vertices.size() < 150 && superVertexes > ((double) vertices.size() / 100 * 10)
            || superVertexes > ((double) vertices.size() / 100 * 15))) {
            inCity = true;
        }
        return inCity;
    }

    // TODO:REMOVE STATICS
    private static Map<Pair, List<Vertex>> cache = new HashMap<>();
    static int hit = 0;
    static int miss = 0;

    record Pair(long v, long u) {

    }

    private void replaceSuperVertexesInPath(Graph fullGraph) {
        assert size() > 3;
        boolean progress = true;
        while (progress) {
            progress = false;
            while (vertices.get(0).isSuperVertex() || vertices.get(vertices.size() - 1).isSuperVertex()) {
                var v = vertices.get(vertices.size() - 1);
                var u = vertices.get(0);
                final DijkstraAlgorithm alg = new DijkstraAlgorithm(fullGraph, v);
                alg.run(u);
                var vToUPath = alg.getRouteFromFullGraph(u);
                assert v.getIdentificator() == vToUPath.get(0).getIdentificator();
                assert u.getIdentificator() == vToUPath.get(vToUPath.size() - 1).getIdentificator();
                var newU = vToUPath.get(vToUPath.size() - 1);
                vToUPath.remove(vToUPath.size() - 1);
                vertices.remove(0);
                vertices.remove(vertices.size() - 1);
                vertices.addAll(vToUPath);
                vertices.add(0, newU);
            }

            int diff = 100;
            for (int i = 1; i < size() - 1; i++) {
                var superVertex = vertices.get(i);
                if (superVertex.isSuperVertex()) {
                    int startIndex = i - 1;
//                    if (i > diff) {
//                        startIndex -= diff;
//                    }
                    int finishIndex = i + 1;
                    while (!vertices.get(finishIndex).isSuperVertex() && finishIndex < vertices.size() - 1 && finishIndex < i + 1 + diff) {
                        finishIndex++;
                    }
//                    LOGGER.info("size0: {} startIndex: {} finishIndex: {}", vertices.size(), startIndex, finishIndex);
                    assert finishIndex - startIndex >= 2;
//                    if (finishIndex + diff < vertices.size()) {
//                        finishIndex += diff;
//                    }
                    var v = vertices.get(startIndex);
                    var u = vertices.get(finishIndex);
                    final DijkstraAlgorithm alg = new DijkstraAlgorithm(fullGraph, v);
                    alg.run(u);
                    var vToUPath = alg.getRouteFromFullGraph(u);

//                    LOGGER.info("vToUPath size: {}", vToUPath.size());
                    assert vToUPath.size() >= 2;
                    assert v.getIdentificator() == vToUPath.get(0).getIdentificator();
                    assert u.getIdentificator() == vToUPath.get(vToUPath.size() - 1).getIdentificator();
                    for (Vertex vertex : vToUPath) {
                        assert !vertex.isSuperVertex();
                    }

                    for (Vertex vertex : vToUPath) {
                        assert !vertex.isSuperVertex();
                    }
//                    LOGGER.info("size1: {} startIndex: {} finishIndex: {}", vertices.size(), startIndex, finishIndex);
                    vertices.subList(startIndex, finishIndex).clear();
//                    LOGGER.info("size2: {} startIndex: {} finishIndex: {}", vertices.size(), startIndex, finishIndex);
                    vertices.addAll(startIndex, vToUPath);
//                    LOGGER.info("size3: {} startIndex: {} finishIndex: {}", vertices.size(), startIndex, finishIndex);
                    progress = true;
                }
            }
        }
        for (int i = 0; i < vertices.size(); i++) {
            var vertex = vertices.get(i);
            if (vertex.isSuperVertex()) {
                LOGGER.info("index: {} length: {}", i, vertices.size());
                assert !vertex.isSuperVertex();
            }
        }
    }

    private boolean hasDuplicate(Graph g, List<Cycle> oldCycles) {
        for (var oldCycle : oldCycles) {
            // can't be done over array of boolean by index because cycle can contain vertexes from another graph
            Long2BooleanOpenHashMap visited = new Long2BooleanOpenHashMap(g.getVertices().size());
            for (int i = 0; i < oldCycle.vertices().size(); i++) {
                var v = oldCycle.vertices().get(i);
                visited.put(v.getIdentificator(), true);
            }
            int count = 0;
            for (Vertex u : vertices()) {
                final boolean value = visited.get(u.getIdentificator());
                if (value) {
                    count++;
                }
            }
            if (count > ((double) vertices().size()) / 10d * 5d) {
                return true;
            }
        }
        return false;
    }

    private double minDistanceToCycle(Vertex startVertex, DijkstraAlgorithm dijkstra) {
        dijkstra.assertStartVertex(startVertex);
        double minDistance = Double.MAX_VALUE;
        for (Vertex v : vertices()) {
            final double distanceToV = dijkstra.getDistance(v);
            if (distanceToV < minDistance) {
                minDistance = distanceToV;
            }
        }
        assert minDistance != Double.MAX_VALUE;
        return minDistance;
    }

    public Cycle buildShortCycle() {
        List<Vertex> shortCycle = new ArrayList<>(vertices);

        final int minVertexes = 95;
        while (shortCycle.size() > minVertexes) {
            var it = shortCycle.iterator();
            while (it.hasNext() && shortCycle.size() > minVertexes) {
                it.next();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }
        }
        return new Cycle(shortCycle);
    }

    public Cycle getReversedVertices() {
        List<Vertex> reverseCycle = new ArrayList<>(vertices);
        Collections.reverse(reverseCycle);
        return new Cycle(reverseCycle);
    }

    public static double getCycleDistance(List<Vertex> list) {
        assert list.size() > 2;
        double distCycle = 0;
        for (int j = 1; j < list.size(); j++) {
            final Vertex prevV = list.get(j - 1);
            final Vertex nextV = list.get(j);
            distCycle += prevV.getDistance(nextV);
        }
        return distCycle;
    }

    public static double getCycleDistanceSlow(List<Vertex> list) {
        assert list.size() > 2;
        double distCycle = 0;
        for (int j = 1; j < list.size(); j++) {
            final Vertex prevV = list.get(j - 1);
            final Vertex nextV = list.get(j);
            distCycle += LatLon.distanceKM(prevV.getLatLon(), nextV.getLatLon());
        }
        return distCycle;
    }

    public int countSuperVertexes() {
        int superVertexes = 0;
        for (int j = 0; j < vertices.size(); j++) {
            final Vertex v = vertices.get(j);
            if (v.isSuperVertex()) {
                superVertexes++;
            }
        }
        return superVertexes;
    }

    public int countSuperVertexesWithoutEnds() {
        int superVertexes = 0;
        int startIndex = (int) (((double) vertices.size()) / 100 * 20);
        int finishIndex = (int) (((double) vertices.size()) / 100 * 80);
        for (int j = startIndex; j < finishIndex; j++) {
            final Vertex v = vertices.get(j);
            if (v.isSuperVertex()) {
                superVertexes++;
            }
        }
        return superVertexes;
    }

    public void removeExternalCycles(double cycleDistance) {
        boolean removedSomething = true;
        while (removedSomething) {
            removedSomething = false;
            int startIndex = (int) (((double) vertices.size()) / 100 * 5);
            int finishIndex = (int) (((double) vertices.size()) / 100 * 95);

            for (int i = startIndex; i <= finishIndex; i++) {
                for (int j = i + 5; j <= finishIndex; j++) {
                    final Vertex v = vertices.get(i);
                    final Vertex u = vertices.get(j);
                    if (v.getNeighbors().contains(u)) {
                        final List<Vertex> subList = vertices.subList(i + 1, j - 1);
                        // can't use getCycleDistance because we remove subgraph - they are not neighbors (can use when we can for perf)
                        final double subCycleDistance = getCycleDistanceSlow(subList);
                        if (subCycleDistance > cycleDistance / 10 * 3) {
                            break;
                        }
                        subList.clear();
                        removedSomething = true;
                        break;
                    }
                }
                if (removedSomething) {
                    break;
                }
            }
        }
    }

    public int size() {
        return vertices.size();
    }

    public boolean isGood() {

//        double angles = 0;
//        int count1 = 0;
//        int count2 = 0;
//        double a1 = 0;
//        double a2 = 0;
//        for (int i = 0; i < vertices.size() - 3; i++) {
//            final LatLon p1 = vertices.get(i).getLatLon();
//            final LatLon p2 = vertices.get(i + 1).getLatLon();
//            final LatLon p3 = vertices.get(i + 2).getLatLon();
//            double angle =
//                LatLon.angle(p1, p2, p3);
//            angles += angle;
//            if (angle > 0) {
//                count1++;
//                a1 += angle;
//            } else {
//                count2++;
//                a2 += angle;
//            }
//        }
//
//        double realSum = (vertices.size() - 2) * Math.PI;
//        LOGGER.info("graph with angles: {}", angles);
//        LOGGER.info("graph with count1: {}, count2: {}", count1, count2);
//        LOGGER.info("graph with a1: {}, a2: {}", a1, a2);
//        if (angles < realSum) {
//            return true;
//        }
//        return false;

//        var points = vertices.stream()
//            .map(Vertex::getLatLon)
//            .toList();
//        int diff = points.size() / 10;
//
//        int startIndex = (int) (((double) points.size()) / 100 * 5);
//        int finishIndex = (int) (((double) points.size()) / 100 * 95);
//        assert finishIndex > 1;
//        assert finishIndex < points.size();
//        int count = 0;
//        for (int i = startIndex; i <= finishIndex; i++) {
//            for (int j = i + diff; j <= finishIndex; j++) {
//                final LatLon point1 = points.get(i);
//                final LatLon point2 = points.get(j);
//                if (point1.isClosePoint(point2)) {
//                    count++;
//                    break;
//                }
//            }
//        }
//        LOGGER.info("graph has count: {} size: {}", count, points.size());

        return true;
    }

    public boolean hasAnInternalCycle() {
        int startIndex = (int) (((double) vertices.size()) / 100 * 10);
        int finishIndex = (int) (((double) vertices.size()) / 100 * 90);

        int count = 0;
        int eliminate_points = 300;
        for (int i = startIndex; i <= finishIndex; i++) {
            for (int j = i + eliminate_points; j <= finishIndex; j++) {
                if (vertices.get(i).getLatLon().isCloseInCity(vertices.get(j).getLatLon(), 0.1)) {
                    count++;
                    break;
                }
            }
        }
        LOGGER.info("found: {} similar points from: {}", count, finishIndex - startIndex);
        // 10% of the route can go back
        if (count > (finishIndex - startIndex - eliminate_points) / 100 * 10) {
            return true;
        }
        return false;
    }
}
