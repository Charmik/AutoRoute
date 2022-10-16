package com.autoroute.logistic.rodes;

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
                               Cycle cycle, DijkstraAlgorithm dijkstra, int minKM, int maxKM) {
        final double distanceToCycle = minDistanceToCycle(startVertex, cycle, dijkstra);
        final double cycleDistance = cycle.getCycleDistance();
        double routeDistance = distanceToCycle * 2 + cycleDistance;

//        int superVertexes = cycle.countSuperVertexes();
//        int superVertexes = cycle.countSuperVertexesWithoutEnds();
        if (1 == 1
            && (routeDistance > minKM && routeDistance < maxKM)
            && (cycleDistance > minKM / 2)
            && distanceToCycle > 5
//            && superVertexes < 2
//            && superVertexes < 2// TODO: only first 5%/last 95% of cycle can be super

//            && cycle.isGood()
            // TODO: && don't cross yourself
        ) {
            cycle.removeExternalCycles();
            if (!hasDuplicate(g, result, cycle) && !hasDuplicate(g, result, cycle.getReversedVertices())) {
//                if (cycle.buildShortCycle().isGood()) {

                int superCount = 0;
                for (Vertex vertex : cycle.vertices()) {
                    for (Vertex superVertex : g.getSuperVertices()) {
                        // TODO: use together with Graph.removeCloseVertexes
                        if (vertex.getLatLon().isCloseInCity(superVertex.getLatLon(), 1.5)) {
                            superCount++;
                            break;
                        }
                    }
                }
                // TODO: run bfs for every Vertex with max distance 5.
                //  If come to another cycle vertex - fix the path

                if (superCount < cycle.size() / 5) {
                    LOGGER.info("index: {}, distanceToCycle: {}, cycleDistance: {}, routeDistance: {}",
                        result.size(), distanceToCycle, cycleDistance, routeDistance);
                    LOGGER.info("have: {}/{} super vertices", superCount, cycle.size());

                    cycle.isGood();
                    cycle.hasAnInternalCycle();

                    replaceSuperVertexesInPath(fullGraph);

                    result.add(cycle);
                }


                return true;
            }
        }
        return false;
    }

    private void replaceSuperVertexesInPath(Graph fullGraph) {
        int start = 0;
        assert size() > 3;
        while (start < size() - 2) {
            start = 0; // TODO: fix
            for (int i = start; i < size() - 1; i++) {
                start = i;
                var v = vertices.get(i);
                var u = vertices.get(i + 1);
                if (u.isSuperVertex()) {
                    // TODO: can pass destination and stop when we erased it
                    final DijkstraAlgorithm alg = new DijkstraAlgorithm(fullGraph, v);
                    alg.run();
                    final List<Vertex> vToUPath = alg.getRouteFromFullGraph(u);
                    vertices.remove(i + 1);
                    vertices.addAll(i + 1, vToUPath);
                    LOGGER.info("replace vertex to path: {}", vToUPath.size());
                    break;
                }
            }
        }
    }

    private boolean hasDuplicate(Graph g, List<Cycle> oldCycles, Cycle newCycle) {
        for (var oldCycle : oldCycles) {
//            boolean[] visited = new boolean[g.getVertices().size()];
            Map<Long, Boolean> visited = new HashMap<>();
            for (int i = 0; i < oldCycle.vertices().size(); i++) {
                var v = oldCycle.vertices().get(i);
//                visited[v.getId()] = true;
                visited.put(v.getIdentificator(), true);
            }

            int count = 0;
            for (Vertex u : newCycle.vertices()) {
//                if (visited[u.getId()]) {
                final Boolean value = visited.get(u.getIdentificator());
                if (value != null && value) {
                    count++;
                }
            }
            if (count > ((double) newCycle.vertices().size()) / 10d * 5d) {
                return true;
            }
        }
        return false;
    }

    private double minDistanceToCycle(Vertex startVertex, Cycle cycle, DijkstraAlgorithm dijkstra) {
        dijkstra.assertStartVertex(startVertex);
        double minDistance = Double.MAX_VALUE;
        for (Vertex v : cycle.vertices()) {
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

    public double getCycleDistance() {
        assert vertices.size() > 2;
        double distCycle = 0;
        for (int j = 1; j < vertices.size(); j++) {
            final Vertex prevV = vertices.get(j - 1);
            final Vertex nextV = vertices.get(j);
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

    public void removeExternalCycles() {
        int skip_points = 5;
        boolean removedSomething = true;
        while (removedSomething) {
            removedSomething = false;
            int startIndex = (int) (((double) vertices.size()) / 100 * 5);
            int finishIndex = (int) (((double) vertices.size()) / 100 * 95);

            for (int i = startIndex; i <= finishIndex - skip_points; i++) {
                for (int j = i + skip_points; j <= finishIndex; j++) {
                    final Vertex v = vertices.get(i);
                    final Vertex u = vertices.get(j);
                    if (v.getNeighbors().contains(u)) {
                        final List<Vertex> subList = vertices.subList(i + 1, j - 1);
                        vertices.removeAll(subList);
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
