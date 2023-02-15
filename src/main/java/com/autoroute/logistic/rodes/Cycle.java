package com.autoroute.logistic.rodes;

import com.autoroute.logistic.LogisticUtils;
import com.autoroute.logistic.rodes.dijkstra.DijkstraAlgorithm;
import com.autoroute.osm.LatLon;
import com.autoroute.utils.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Cycle {

    private static final Logger LOGGER = LogManager.getLogger(Cycle.class);
    private static long DEBUG_COUNTER = 0;

    private final List<Vertex> vertices;
    private @Nullable List<Vertex> compactVertices = null;
    private final Random r = new Random(43);

    public Cycle(List<Vertex> vertices) {
        this.vertices = vertices;
    }

    public void setCompactVertices(@NotNull List<Vertex> compactVertices) {
        assert compactVertices != null;
        this.compactVertices = compactVertices;
    }

    public boolean tryAddCycle(Graph fullGraph, Vertex startVertex, List<Cycle> result,
                               DijkstraAlgorithm dijkstra, int minKM, int maxKM) {
        assertFirstAndEndCycle();
        DEBUG_COUNTER++;

        final double distanceToCycle = minDistanceToCycle(startVertex, dijkstra);
        final double cycleDistance = getCycleDistance(vertices);
        double routeDistance = distanceToCycle * 2 + cycleDistance;
        final int superVertexes = countSuperVertexes();

        // distance will be increased by full graph
        if (isGoodDistance(cycleDistance, distanceToCycle, minKM * 0.7, maxKM * 0.8)
            && !isInCity(superVertexes)
            && size() > 50
            // TODO: && don't cross yourself
        ) {
            removeSuperVerticesAtTheStartAndEnd();
            assertFirstAndEndCycle();
            removeExternalCycles(cycleDistance);
            removeExternalGoToAnotherRoadAndComeBack(fullGraph);
            if (isInCity(countSuperVertexes()) || isSmallCycle()) {
                return false;
            }
            var duplicateVertices = new ArrayList<>(vertices);
            var duplicateReversedVertices = new ArrayList<>(duplicateVertices);
            Collections.reverse(duplicateReversedVertices);
            if (!hasDuplicate(duplicateVertices, result) && !hasDuplicate(duplicateReversedVertices, result)) {
                if (Utils.isDebugging()) {
                    Utils.writeDebugGPX(vertices, "cycles/" + (result.size() + 1) + "_1");
                }

                if (!replaceSuperVertexesInPath(fullGraph)) {
                    return false;
                }
                if (Utils.isDebugging()) {
                    Utils.writeDebugGPX(vertices, "cycles/" + (result.size() + 1) + "_2");
                }

                List<Vertex> fullGraphVertexes = new ArrayList<>();
                for (Vertex v : vertices) {
                    fullGraphVertexes.add(fullGraph.findByIdentificator(v.getIdentificator()));
                }

                Cycle fullCycle = new Cycle(fullGraphVertexes);
                fullCycle.removeExternalCycles(getCycleDistance(vertices));
                if (Utils.isDebugging()) {
                    Utils.writeDebugGPX(fullCycle.vertices, "cycles/" + (result.size() + 1) + "_3");
                }

                // TODO: extract code to work only with fullCycle
                final double distanceToFullCycle = fullCycle.minDistanceToCycle(startVertex, dijkstra);
                final double cycleFullDistance = getCycleDistance(fullCycle.vertices);

                // TODO: if we have straight road between i & i + X - then we need use it instead of road hook
                if (isGoodDistance(cycleFullDistance, distanceToFullCycle, minKM, maxKM)) {
                    // TODO: reverse cycle, depends on the country left/right roads
                    fullCycle.setCompactVertices(duplicateVertices);
                    result.add(fullCycle);
                    LOGGER.info("index: {}, distanceToCycle: {}, cycleDistance: {}, routeDistance: {}, superVertexes: {}",
                        result.size(), distanceToFullCycle, cycleFullDistance, routeDistance, superVertexes);
                }
                return true;
            }
        }
        return false;
    }

    private boolean isSmallCycle() {
        return size() < 50;
    }

    private void removeSuperVerticesAtTheStartAndEnd() {
        // To be sure that Start vertex is not changed.
        while (vertices.get(0).isSuperVertex() || vertices.get(vertices.size() - 1).isSuperVertex()) {
            assertFirstAndEndCycle();
            vertices.remove(0);
            vertices.add(vertices.get(0));
            assertFirstAndEndCycle();
        }
    }

    private boolean isGoodDistance(double cycleDistance, double distanceToCycle, double minKM, double maxKM) {
        double routeDistance = distanceToCycle * 2 + cycleDistance;
        if (1 == 1
//            && (cycleDistance > minKM / 2)
            && routeDistance >= minKM
            && routeDistance <= maxKM
            && (cycleDistance > routeDistance * 0.5)
        ) {
            return true;
        }
        return false;
    }

    // TODO: rewrite by loading cities from OSM
    private boolean isInCity(int superVertexes) {
        boolean inCity = false;
        if ((vertices.size() < 150 && superVertexes > ((double) vertices.size() / 100 * 10)
            || superVertexes > ((double) vertices.size() / 100 * 15))) {
            inCity = true;
        }
        return inCity;
    }

    private boolean replaceSuperVertexesInPath(Graph fullGraph) {
        assert size() > 3;
        boolean progress = true;
        assertFirstAndEndCycle();
        while (progress) {
            progress = false;
            assert vertices.size() > 4;
            assertFirstAndEndCycle();
            assert !vertices.get(0).isSuperVertex() && !vertices.get(vertices.size() - 1).isSuperVertex();

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

                    final DijkstraAlgorithm alg = new DijkstraAlgorithm(fullGraph, v);
                    alg.run(u);
                    List<Vertex> vToUPath = alg.getRouteFromFullGraph(u);
                    // TODO: remove it - not possible if we have connected graph
                    if (vToUPath.size() == 1) { // didn't find a route between v & u
                        return false;
                    }
                    assert vToUPath.size() >= 2;

                    final double oldDistance = v.getDistance(u);
                    final double newDistanceOfPath = getCycleDistance(vToUPath);
                    // distance increased significantly, probably with superNode we went over river/big road
                    // where we can't really ride. Can we do it better here?
                    // TODO: compare it with full distance? if it's small percent - maybe it's okay?
                    if (oldDistance * 5 < newDistanceOfPath) {
                        return false;
                    }


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

    private void assertFirstAndEndCycle() {
        assert vertices.get(0).getIdentificator() == vertices.get(vertices.size() - 1).getIdentificator();
    }

    // TODO: check in the end with full route? some cycle can go via start point, in another add way - get the same route.
    public static boolean hasDuplicate(List<Vertex> currentCycle, List<Cycle> oldCycles) {
        final List<LatLon> newBox = getBoxByCycle(currentCycle);
        assert isCycleInsideBox(currentCycle, newBox, 0.99);

        for (int i = 0; i < oldCycles.size(); i++) {
            var oldCycle = oldCycles.get(i);
            final List<Vertex> oldVertices = oldCycle.compactVertices;
            assert oldVertices != null;
            final List<LatLon> oldBox = getBoxByCycle(oldVertices);
            assert isCycleInsideBox(oldVertices, oldBox, 0.99);
            double comparePercent = 0.8;
            if (isCycleInsideBox(currentCycle, oldBox, comparePercent) &&
                isCycleInsideBox(oldVertices, newBox, comparePercent)) {
                return true;
            }
        }
        return false;
    }

    private static List<LatLon> getBoxByCycle(List<Vertex> vertices) {
        assert !vertices.isEmpty();
        final LatLon firstElementLatLon = vertices.get(0).getLatLon();
        LatLon minX = firstElementLatLon;
        LatLon minY = firstElementLatLon;
        LatLon maxX = firstElementLatLon;
        LatLon maxY = firstElementLatLon;
        for (Vertex v : vertices) {
            final LatLon latLon = v.getLatLon();
            if (latLon.lat() < minX.lat()) {
                minX = latLon;
            }
            if (latLon.lon() < minY.lon()) {
                minY = latLon;
            }
            if (latLon.lat() > maxX.lat()) {
                maxX = latLon;
            }
            if (latLon.lon() > maxY.lon()) {
                maxY = latLon;
            }
        }
        for (Vertex v : vertices) {
            final LatLon latLon = v.getLatLon();
            assert latLon.lat() >= minX.lat();
            assert latLon.lon() >= minY.lon();
            assert latLon.lat() <= maxX.lat();
            assert latLon.lon() <= maxY.lon();
        }
        return List.of(minX, minY, maxX, maxY);
    }

    private static boolean isCycleInsideBox(List<Vertex> vertices, List<LatLon> list, double percent) {
        assert list.size() == 4;
        double EPS = 0.001;
        double minX = list.get(0).lat() - EPS;
        double minY = list.get(1).lon() - EPS;
        double maxX = list.get(2).lat() + EPS;
        double maxY = list.get(3).lon() + EPS;
        int count = 0;
        for (Vertex v : vertices) {
            final LatLon latLon = v.getLatLon();
            if (latLon.lat() >= minX &&
                latLon.lon() >= minY &&
                latLon.lat() <= maxX &&
                latLon.lon() <= maxY) {
                count++;
            }
        }
        if (count > ((double) vertices.size()) * percent) {
            return true;
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

    public static double getCycleDistance(List<Vertex> list) {
        assert list.size() >= 2;
        double distCycle = 0;
        for (int j = 1; j < list.size(); j++) {
            final Vertex prevV = list.get(j - 1);
            final Vertex nextV = list.get(j);
            distCycle += prevV.getDistance(nextV);
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

    public void removeExternalCycles(double cycleDistance) {
        boolean removedSomething = true;
        while (removedSomething) {
            removedSomething = false;
            int startIndex = (int) (((double) vertices.size()) / 100 * 5);
            int finishIndex = (int) (((double) vertices.size()) / 100 * 95);

            for (int i = startIndex; i <= finishIndex; i++) {
                for (int j = i + 3; j <= finishIndex; j++) {
                    final Vertex v = vertices.get(i);
                    final Vertex u = vertices.get(j);
                    // TODO: if they are very close by distance - try to merge it with dijkstra?
                    if (v.containsNeighbor(u)) {
                        final List<Vertex> subList = vertices.subList(i + 1, j);
                        // can't use getCycleDistance because we remove subgraph - they are not neighbors (can use when we can for perf)
                        final double subCycleDistance = LogisticUtils.getCycleDistanceSlow(subList);
                        if (subCycleDistance > cycleDistance / 10 * 2) {
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

    public void removeExternalGoToAnotherRoadAndComeBack(Graph fullGraph) {
        int startIndex = (int) (((double) vertices.size()) / 100 * 5);
        int finishIndex = (int) (((double) vertices.size()) / 100 * 95);

        int[] percentiles = {5, 10, 15, 18};
        int i = startIndex;
        while (i < finishIndex && i < vertices.size()) {
            for (int percent : percentiles) {
                if (isSmallCycle()) {
                    return;
                }
                int j = i + (vertices.size() * percent / 100);
                if (j > vertices.size() - 1) {
                    break;
                }
                if (j - i > vertices.size() / 100 * 20) {
                    continue;
                }
                assert j > i : "i: " + i + " j: " + j;
                var v = vertices.get(i);
                var u = vertices.get(j);
                if (v.getRef() != null && v.getRef() == u.getRef()) {
                    // check if path i..j contains a vertex on another road
                    int iterCount = 5;
                    for (int iter = 1; iter < iterCount; iter++) {
                        int middleVertex = (((j - i) / iterCount) * iter) + i;
                        assert middleVertex > i : "i: " + i + " j: " + j + " percent: " + percent + " vertices.size(): " + vertices.size() + " iter: " + iter;
                        assert middleVertex < j : "i: " + i + " j: " + j + " percent: " + percent + " vertices.size(): " + vertices.size() + " iter: " + iter;
                        var k = vertices.get(middleVertex);
                        if (k.getRef() != null && v.getRef() != k.getRef()) {
                            final List<Vertex> subList = vertices.subList(i + 1, j + 1);
                            int oldSize = subList.size();
                            subList.clear();

                            final DijkstraAlgorithm alg = new DijkstraAlgorithm(fullGraph, v);
                            alg.run(u);
                            List<Vertex> vToNeighborPath = alg.getRouteFromFullGraph(u);
                            subList.addAll(vToNeighborPath);
                            if (oldSize == vToNeighborPath.size()) {
                                i = j + 1;
                                break;
                            }
                            break;
                        }
                    }
                }
            }
            i++;
        }
    }

    boolean containsLatLon(LatLon latLon, double distance) {
        for (Vertex v : vertices) {
            if (LatLon.distanceKM(v.getLatLon(), latLon) < distance) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        return vertices.size();
    }

    public List<Vertex> getVertices() {
        return vertices;
    }
}
