package com.autoroute.logistic.rodes;

import com.autoroute.logistic.rodes.dijkstra.DijkstraAlgorithm;
import com.autoroute.logistic.rodes.dijkstra.DijkstraCache;
import com.autoroute.osm.LatLon;
import com.autoroute.utils.Utils;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Cycle {

    private static final Logger LOGGER = LogManager.getLogger(Cycle.class);

    private final List<Vertex> vertices;
    private @Nullable List<Vertex> compactVertices = null;

    public Cycle(List<Vertex> vertices) {
        this.vertices = vertices;
    }

    public void setCompactVertices(@NotNull List<Vertex> compactVertices) {
        assert compactVertices != null;
        this.compactVertices = compactVertices;
    }

    public boolean tryAddCycle(Graph g, Graph fullGraph, Vertex startVertex, List<Cycle> result,
                               DijkstraAlgorithm dijkstra, int minKM, int maxKM, DijkstraCache dijkstraCache) {
        final double distanceToCycle = minDistanceToCycle(startVertex, dijkstra);
        final double cycleDistance = getCycleDistance(vertices);
        double routeDistance = distanceToCycle * 2 + cycleDistance;
        final int superVertexes = countSuperVertexes();

        if (1 == 1
            // distance will be increased by full graph
            && isGoodDistance(cycleDistance * 0.7, distanceToCycle * 0.7, minKM, maxKM)
            && !isInCity(countSuperVertexes())
            // TODO: && don't cross yourself
        ) {
//            LOGGER.info("index: {} length: {}", result.size(), vertices.size());
            removeExternalCycles(cycleDistance);
            if (isInCity(countSuperVertexes())) {
                return false;
            }
            if (!hasDuplicate(g, result) && !getReversedVertices().hasDuplicate(g, result)) {
                Utils.writeGPX(vertices, "cycles/1_", result.size());

                if (!replaceSuperVertexesInPath(fullGraph, dijkstraCache)) {
                    return false;
                }
                Utils.writeGPX(vertices, "cycles/2_", result.size());

                List<Vertex> fullGraphVertexes = new ArrayList<>();
                for (Vertex v : vertices) {
                    fullGraphVertexes.add(fullGraph.findByIdentificator(v.getIdentificator()));
                }

                Cycle fullCycle = new Cycle(fullGraphVertexes);

                fullCycle.removeExternalCycles(cycleDistance);
                Utils.writeGPX(fullCycle.vertices, "cycles/3_", result.size());

                final double distanceToFullCycle = fullCycle.minDistanceToCycle(startVertex, dijkstra);
                final double cycleFullDistance = getCycleDistance(fullCycle.vertices);

                if (isGoodDistance(cycleFullDistance, distanceToFullCycle, minKM, maxKM)) {
                    LOGGER.info("index: {}, distanceToCycle: {}, cycleDistance: {}, routeDistance: {}, superVertexes: {}",
                        result.size(), distanceToFullCycle, cycleFullDistance, routeDistance, superVertexes);
                    fullCycle.setCompactVertices(vertices);
                    result.add(fullCycle);
                }
                return true;
            }
        }
        return false;
    }

    private boolean isGoodDistance(double cycleDistance, double distanceToCycle, double minKM, double maxKM) {
        double routeDistance = distanceToCycle * 2 + cycleDistance;
        if (1 == 1
            && (cycleDistance > minKM / 2)
            && routeDistance < maxKM
            && (cycleDistance > routeDistance / 100 * 30)
        ) {
            return true;
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

    private boolean replaceSuperVertexesInPath(Graph fullGraph, DijkstraCache cache) {
        assert size() > 3;
        boolean progress = true;
        while (progress) {
            progress = false;
            assert vertices.size() > 2;
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

            int diff = 5;
            for (int i = 1; i < size() - 1; i++) {
                var superVertex = vertices.get(i);
                if (superVertex.isSuperVertex()) {
                    int startIndex = i - 1;
                    int finishIndex = i + 1;
                    while (!vertices.get(finishIndex).isSuperVertex()
                        && finishIndex < vertices.size() - 1
                        && finishIndex < i + 1 + diff) {
                        finishIndex++;
                    }
                    assert finishIndex - startIndex >= 2;
                    var v = vertices.get(startIndex);
                    var u = vertices.get(finishIndex);

                    var p = new DijkstraCache.Pair(v.getIdentificator(), u.getIdentificator());
                    List<Vertex> vToUPath;
                    final List<Vertex> cachePath = cache.get(p);
                    if (cachePath == null) {
                        final DijkstraAlgorithm alg = new DijkstraAlgorithm(fullGraph, v);
                        alg.run(u);
                        vToUPath = alg.getRouteFromFullGraph(u);
                        cache.put(p, vToUPath);
                    } else {
                        vToUPath = cachePath;
                    }

                    final double oldDistance = v.getDistance(u);
                    final double newDistanceOfPath = getCycleDistance(vToUPath);
                    assert newDistanceOfPath >= oldDistance;
                    // distance increased significantly, probably with superNode we went over river/big road
                    // where we can't really ride. Can we do it better here?
                    if (oldDistance * 3 < newDistanceOfPath) {
                        // LOGGER.info("oldDistance: {}, newDistanceOfPath: {}", oldDistance, newDistanceOfPath);
                        return false;
                    }

                    assert vToUPath.size() >= 2;
                    assert v.getIdentificator() == vToUPath.get(0).getIdentificator();
                    assert u.getIdentificator() == vToUPath.get(vToUPath.size() - 1).getIdentificator();
                    for (Vertex vertex : vToUPath) {
                        assert !vertex.isSuperVertex();
                    }

                    for (Vertex vertex : vToUPath) {
                        assert !vertex.isSuperVertex();
                    }
                    vertices.subList(startIndex, finishIndex).clear();
                    vertices.addAll(startIndex, vToUPath);
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
        return true;
    }

    private boolean hasDuplicate(Graph g, List<Cycle> oldCycles) {
        for (var oldCycle : oldCycles) {
            // can't be done over array of boolean by index because cycle can contain vertexes from another graph
            Long2BooleanOpenHashMap visited = new Long2BooleanOpenHashMap(g.getVertices().size());
            assert oldCycle.compactVertices != null;
            for (int i = 0; i < oldCycle.compactVertices.size(); i++) {
                var v = oldCycle.compactVertices.get(i);
                visited.put(v.getIdentificator(), true);
            }
            int count = 0;
            for (Vertex v : vertices) {
                boolean found = false;
                for (Vertex u : v.getNeighbors()) {
                    final boolean value = visited.get(u.getIdentificator());
                    if (value) {
                        found = true;
                        break;
                    }
                }
                final boolean value = visited.get(v.getIdentificator());
                if (value || found) {
                    count++;
                }
            }
            if (count > ((double) vertices.size()) / 10d * 8d) {
                return true;
            }
        }
        return false;
    }

    private double minDistanceToCycle(Vertex startVertex, DijkstraAlgorithm dijkstra) {
        dijkstra.assertStartVertex(startVertex);
        double minDistance = Double.MAX_VALUE;
        for (Vertex v : vertices) {
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
                    // TODO: if they are very close by distance - try to merge it with dijkstra?
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

    public List<Vertex> getVertices() {
        return vertices;
    }
}
