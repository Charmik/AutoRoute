package com.autoroute.logistic.rodes;

import com.autoroute.osm.LatLon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;

public class Graph {

    private static final Logger LOGGER = LogManager.getLogger(Graph.class);

    private List<Vertex> vertices;

    public Graph(List<Vertex> vertices) {
        this.vertices = new ArrayList<>(vertices);
    }

    public Graph(Graph other) {
        this.vertices = other.vertices;
    }

    /*
    public void buildGraph() {
        // TODO: is it possible to optimize?
        for (int i = 0; i < vertices.size(); i++) {
            for (int j = i + 1; j < vertices.size(); j++) {
                var v1 = vertices.get(i);
                var v2 = vertices.get(j);
                if (v1.getLatLon().isClosePoint(v2.getLatLon())) {
                    v1.addNeighbor(v2);
                    v2.addNeighbor(v1);
                }
            }
        }
    }
    */

    public Cycle dfs(Vertex v) {
        boolean[] visit = new boolean[vertices.size()];
        int[] steps = new int[vertices.size()];
        Arrays.fill(steps, -1);
        List<Vertex> currentWay = new ArrayList<>(1024);
        currentWay.add(v);
        var res = dfs(v, currentWay, 0, visit, steps);
        return res;
    }

    private Cycle dfs(Vertex v, List<Vertex> currentWay, int depth, boolean[] visit, int[] steps) {
        assert v.getId() == vertices.get(v.getId()).getId();
        assert v.getNeighbors().size() == vertices.get(v.getId()).getNeighbors().size();
        final int id = v.getId();
        visit[id] = true;
        steps[id] = depth;

        var neighbors = new ArrayList<>(v.getNeighbors());
        Collections.shuffle(neighbors);

        for (Vertex u : neighbors) {
//            System.out.println("d2: " + LatLon.distanceKM(v.getLatLon(), u.getLatLon()));
            try {
                if (!visit[u.getId()]) {
                    currentWay.add(u);
                    var res = dfs(u, currentWay, depth + 1, visit, steps);
                    if (!res.route().isEmpty()) {
                        return res;
                    }
                    currentWay.remove(currentWay.size() - 1);
                } else {
                    final int uSteps = steps[u.getId()];
                    assert uSteps != -1;
                    if (depth > uSteps) {
                        final int diffDepth = depth - uSteps;
                        if (diffDepth > 30) {
                            currentWay.add(u);
                            return new Cycle(currentWay, u, v);
                        }
                    }
                }
            } catch (Throwable t) {
                System.out.println(t);
            }
        }
        return Cycle.empty();
    }

    public Vertex findNearestVertex(LatLon latLon) {
        Vertex minV = vertices.get(0);
        double minD = LatLon.distanceKM(latLon, vertices.get(0).getLatLon());
        for (int i = 1; i < vertices.size(); i++) {
            var v = vertices.get(i);
            var d = LatLon.distanceKM(latLon, v.getLatLon());
            if (d < minD) {
                minV = v;
                minD = d;
            }
        }
        return minV;
    }

    public void buildFast(double maxDistance) {
        boolean[] addedNeighbor = new boolean[1];
        iterateOverPotentialNeighbors(maxDistance,
            () -> {
                addedNeighbor[0] = false;
            },
            (i, v1, j, v2) -> {
                final double d = LatLon.distanceKM(v1.getLatLon(), v2.getLatLon());
//                System.out.println("d1: " + d);
                assert d < maxDistance;
                if (v1.addNeighbor(v2)) {
                    addedNeighbor[0] = true;
                }
                if (v2.addNeighbor(v1)) {
                    addedNeighbor[0] = true;
                }
            },
            (i, j) -> {
                return addedNeighbor[0];
            });
    }

    public void eliminatesNodes(double maxDistance) {
        boolean[][] remove = new boolean[1][vertices.size()];
        int[] oldSize = new int[1];
        iterateOverPotentialNeighbors(maxDistance,
            () -> {
                oldSize[0] = vertices.size();
            },
            (i, v1, j, v2) -> {
                remove[0][i] = true;
            },
            (i, j) -> {
                List<Vertex> l = new ArrayList<>();
                for (int it = 0; it < vertices.size(); it++) {
                    if (!remove[0][it]) {
                        l.add(vertices.get(it));
                    }
                }
                System.out.println("get: " + l.size() + " vertexes");
                if (l.size() == oldSize[0]) {
                    return false;
                }
                vertices = l;
                remove[0] = new boolean[vertices.size()];
                updateIds();
                return true;
            });
    }

    public void iterateOverPotentialNeighbors(
        double maxDistance,
        Runnable beforeIteration,
        FourConsumer<Integer, Vertex, Integer, Vertex> func1,
        BiFunction<Integer, Integer, Boolean> continueFunc) {
        Set<Vertex> choosen = new HashSet<>();

        int[] fail = new int[1];
        for (int iteration = 0; iteration < 500; iteration++) {
            beforeIteration.run();
            Vertex randomVertex;
            while (true) {
                var randomIndex = ThreadLocalRandom.current().nextInt(vertices.size());
                randomVertex = vertices.get(randomIndex);
                if (!choosen.contains(randomVertex)) {
                    choosen.add(randomVertex);
                    break;
                }
            }
            final LatLon latLon = randomVertex.getLatLon();
            vertices.sort((o1, o2) -> {
                double distance1;
                double distance2;
                if (fail[0] == 0) {
                    distance1 = LatLon.fastDistance(o1.getLatLon(), latLon);
                    distance2 = LatLon.fastDistance(o2.getLatLon(), latLon);
                } else {
                    distance1 = LatLon.distanceKM(o1.getLatLon(), latLon);
                    distance2 = LatLon.distanceKM(o2.getLatLon(), latLon);
                }
                return Double.compare(distance1, distance2);
            });
            updateIds();

            for (int m = 0; m < vertices.size(); m += 2) {
                int i = m - 1;
                int j = m + 1;
                while (i >= 0
                    && LatLon.distanceKM(vertices.get(i).getLatLon(), vertices.get(m).getLatLon()) < maxDistance) {
                    func1.accept(i, vertices.get(i), m, vertices.get(m));
                    i--;
                }
                while (j < vertices.size()
                    && LatLon.distanceKM(vertices.get(j).getLatLon(), vertices.get(m).getLatLon()) < maxDistance) {
                    if (j >= vertices.size()) {
                        break;
                    }
                    func1.accept(j, vertices.get(j), m, vertices.get(m));
                    j++;
                }
            }
            if (!continueFunc.apply(0, 0)) {
                fail[0]++;
                if (fail[0] == 2) {
                    LOGGER.info("using more accurate distance between points");
                    break;
                }
            }
        }

        StringBuilder s = new StringBuilder("[out:json][timeout:120];\n");
        s.append("(");
        for (Vertex vertex : vertices) {
            s.append("node(").append(vertex.getIdentificator()).append(");");
        }
        s.append(");\n");
        s.append("out;");
        System.out.println(s);
    }

    public void eliminatesNodesSlow(double maxDistance) {
        for (int iter = 0; iter < 1000; iter++) {
            int removed = 0;
            boolean[] remove = new boolean[vertices.size()];
            boolean[] visit = new boolean[vertices.size()];
            for (int i = 0; i < vertices.size(); i++) {
                for (int j = i + 1; j < vertices.size(); j++) {
                    if (checkDistance(maxDistance, remove, visit, i, j)) {
                        removed++;
                    }
                }
            }
            if (removed == 0) {
                break;
            }
            final int newSize = vertices.size() - removed;
            List<Vertex> newArr = new ArrayList<>(newSize);
            for (int i = 0; i < vertices.size(); i++) {
                if (!remove[i]) {
                    newArr.add(vertices.get(i));
                }
            }
            System.out.println("old size: " + vertices.size() + " new size: " + newArr.size());
            vertices = newArr;
            updateIds();
        }
    }

    public void buildSlow(double maxDistance) {
        for (int i = 0; i < vertices.size(); i++) {
            for (int j = i + 1; j < vertices.size(); j++) {
                var v1 = vertices.get(i);
                var v2 = vertices.get(j);
                final double d = LatLon.distanceKM(v1.getLatLon(), v2.getLatLon());
                if (d < maxDistance) {
                    v1.addNeighbor(v2);
                    v2.addNeighbor(v1);
                }
            }
        }
    }

    private boolean checkDistance(double distance, boolean[] remove, boolean[] visit, int i, int j) {
        var v1 = vertices.get(i);
        var v2 = vertices.get(j);
        final double distBetween = LatLon.distanceKM(v1.getLatLon(), v2.getLatLon());
        if (!remove[i] &&
            !remove[j] &&
            !visit[i] &&
            !visit[j]) {
            if (distBetween < distance) {
                visit[i] = visit[j] = true;
                remove[i] = true;
                return true;
            }
        }
        return false;
    }

    public List<Vertex> getVertices() {
        return vertices;
    }

    public void mergeNeighbours(int minNeighboursCount, double maxDistance) {
        boolean progress = true;
        for (int iter = 0; iter < 10000 && progress; iter++) {
            progress = false;

            for (int i = 0; i < vertices.size(); i++) {
                var v = vertices.get(i);
                final Set<Vertex> neighbors = new HashSet<>(v.getNeighbors());
                final Set<Vertex> removeVertexes = new HashSet<>();

                if (neighbors.size() >= minNeighboursCount) {
                    for (Vertex u : neighbors) {
                        final double d = LatLon.distanceKM(v.getLatLon(), u.getLatLon());
                        if (d < maxDistance * neighbors.size()) {
                            removeVertexes.add(u);
                            v.removeNeighbor(u);
                            progress = true;
                        }
                    }
                    if (progress) {
                        for (Vertex removeVertex : removeVertexes) {
                            final Set<Vertex> removeVertexNeighbors = new HashSet<>(removeVertex.getNeighbors());
                            updateEdges(removeVertexNeighbors, v, maxDistance);
                            for (Vertex neighbor : removeVertexNeighbors) {
                                neighbor.removeNeighbor(removeVertex);
                            }
                            vertices.remove(removeVertex);
                        }
                        break;
                    }

                }
            }
        }
        updateIds();
    }

    private void updateIds() {
        for (int i = 0; i < vertices.size(); i++) {
            vertices.get(i).setId(i);
        }
    }

    private void updateEdges(Set<Vertex> neighbors, Vertex u, double maxDistance) {
        final List<Vertex> oldNeighbors = new ArrayList<>(neighbors);
        for (int i = 0; i < oldNeighbors.size(); i++) {
            var v = oldNeighbors.get(i);
            final double d = LatLon.distanceKM(v.getLatLon(), u.getLatLon());
            if (!v.equals(u)) {
                if (v.getNeighbors().size() < 5 || u.getNeighbors().size() < 5) {
                    v.addNeighbor(u);
                    u.addNeighbor(v);
                }
                if (d < maxDistance * oldNeighbors.size() && !v.equals(u)) {
                    v.addNeighbor(u);
                    u.addNeighbor(v);
                }
            }
        }
    }

    public void removeNearVertexes(List<Vertex> cycleVertexes) {
        for (Vertex v : cycleVertexes) {
            boolean[] removed = new boolean[vertices.size()];
            for (int i = 0; i < vertices.size(); i++) {
                Vertex u = vertices.get(i);
                if (v.getLatLon().isCloseInCity(u.getLatLon())) {
                    removed[i] = true;
                    for (Vertex neighbor : u.getNeighbors()) {
                        neighbor.removeNeighbor(u);
                    }
                }
            }
            List<Vertex> newVertices = new ArrayList<>();
            for (int i = 0; i < vertices.size(); i++) {
                Vertex u = vertices.get(i);
                if (!removed[i]) {
                    newVertices.add(u);
                }
            }
            vertices = newVertices;
        }
        updateIds();
    }

    @FunctionalInterface
    public interface FourConsumer<T, U, V, X> {

        /**
         * Performs this operation on the given arguments.
         *
         * @param t the first input argument
         * @param u the second input argument
         */
        void accept(T t, U u, V v, X x);

        /**
         * Returns a composed {@code BiConsumer} that performs, in sequence, this
         * operation followed by the {@code after} operation. If performing either
         * operation throws an exception, it is relayed to the caller of the
         * composed operation.  If performing this operation throws an exception,
         * the {@code after} operation will not be performed.
         *
         * @param after the operation to perform after this operation
         * @return a composed {@code BiConsumer} that performs in sequence this
         * operation followed by the {@code after} operation
         * @throws NullPointerException if {@code after} is null
         */
        default FourConsumer<T, U, V, X> andThen(FourConsumer<? super T, ? super U, ? super V, ? super X> after) {
            Objects.requireNonNull(after);

            return (l, r, s, x) -> {
                accept(l, r, s, x);
                after.accept(l, r, s, x);
            };
        }
    }

    /*
        public void eliminatesNodes(double maxDistance) {
            Set<Vertex> choosen = new HashSet<>();
            int[] fail = new int[1];
            for (int iteration = 0; iteration < 500; iteration++) {
                var oldSize = vertices.size();
                Vertex randomVertex;
                while (true) {
                    var randomIndex = ThreadLocalRandom.current().nextInt(vertices.size());
                    randomVertex = vertices[randomIndex];
                    if (!choosen.contains(randomVertex)) {
                        choosen.add(randomVertex);
                        break;
                    }
                }
                final LatLon latLon = randomVertex.getLatLon();
                Arrays.sort(vertices, (o1, o2) -> {
                    double distance1;
                    double distance2;
                    if (fail[0] == 0) {
                        distance1 = LatLon.fastDistance(o1.getLatLon(), latLon);
                        distance2 = LatLon.fastDistance(o2.getLatLon(), latLon);
                    } else {
                        distance1 = LatLon.distanceKM(o1.getLatLon(), latLon);
                        distance2 = LatLon.distanceKM(o2.getLatLon(), latLon);
                    }
                    return Double.compare(distance1, distance2);
                });

                boolean[] remove = new boolean[vertices.size()];
                for (int m = 0; m < vertices.size(); m += 2) {
                    int i = m - 1;
                    int j = m + 1;
                    while (i >= 0
                        && LatLon.distanceKM(vertices.get(i).getLatLon(), vertices.get(m).getLatLon()) < maxDistance) {
                        remove[i] = true;
                        i--;
                    }
                    while (j < vertices.size()
                        && LatLon.distanceKM(vertices.get(j).getLatLon(), vertices.get(m).getLatLon()) < maxDistance) {
                        if (j >= vertices.size()) {
                            break;
                        }
                        remove[j] = true;
                        j++;
                    }
                }
                List<Vertex> l = new ArrayList<>();
                for (int i = 0; i < vertices.size(); i++) {
                    if (!remove[i]) {
                        l.add(vertices.get(i));
                    }
                }
                System.out.println("get: " + l.size() + " vertexes");
                if (l.size() == oldSize) {
                    fail[0]++;
                    if (fail[0] == 2) {
                        break;
                    }
                    LOGGER.info("using more accurate distance between points");
                }
                vertices = l.toArray(new Vertex[0]);
            }

            StringBuilder s = new StringBuilder("[out:json][timeout:120];\n");
            s.append("(");
            for (Vertex vertex : vertices) {
                s.append("node(").append(vertex.getIdentificator()).append(");");
            }
            s.append(");\n");
            s.append("out;");
            System.out.println(s);
        }
    */
}
