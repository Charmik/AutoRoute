package com.autoroute.logistic.rodes;

import com.autoroute.api.overpass.Mapper;
import com.autoroute.api.overpass.OverpassResponse;
import com.autoroute.gpx.GpxGenerator;
import com.autoroute.osm.LatLon;
import com.autoroute.osm.WayPoint;
import io.jenetics.jpx.GPX;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;

public class GraphBuilder {

    private static final Logger LOGGER = LogManager.getLogger(GraphBuilder.class);

    public static Graph buildFullGraph(OverpassResponse response,
                                       LatLon start,
                                       int minDistanceKM,
                                       int maxDistanceKM) {
        LOGGER.info("Start building full-graph");
        final Graph g = Mapper.mapToGraph(response, minDistanceKM, maxDistanceKM);

        generalPhases(g, start, maxDistanceKM, g.findNearestVertex(start).getIdentificator(), 0.05);
        return g;
    }

    // TODO: add timing stats and print after building
    public static Graph buildGraph(OverpassResponse response,
                                   LatLon start,
                                   long identificatorStartVertex,
                                   int minDistanceKM,
                                   int maxDistanceKM) {
        LOGGER.info("Start building graph");
        try {
            Files.walk(Paths.get("o"), 1)
                .filter(e -> e.toString().endsWith(".gpx"))
                .forEach(e -> e.toFile().delete());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        LOGGER.info("Start mapToVertex");
        Graph g = Mapper.mapToGraph(response, minDistanceKM, maxDistanceKM);
        LOGGER.info("Finish mapToVertex");


        generalPhases(g, start, maxDistanceKM, identificatorStartVertex, 0.1);

        LOGGER.info("Start removeCloseVertexes");
        g.removeCloseVertexes(1, identificatorStartVertex);
        g.checkGraph(identificatorStartVertex);

        var superVertexes = g.getVertices().stream()
            .filter(Vertex::isSuperVertex)
            .map(v -> new WayPoint(1, v.getLatLon(), ""))
            .toList();
        final GPX gpx = GpxGenerator.generate(Collections.emptyList(), superVertexes);
        try {
            GPX.write(gpx,
                Paths.get("o/!superVertexes.gpx"));
        } catch (IOException e) {
        }

        LOGGER.info("Finish removeCloseVertexes");
        LOGGER.info("graph6 has: {} vertices", g.getVertices().size());

        LOGGER.info("Start removeEdges");
        g.removeEdges(identificatorStartVertex);
        g.checkGraph(identificatorStartVertex);
        LOGGER.info("Finish removeEdges");
        LOGGER.info("graph7 has: {} vertices", g.getVertices().size());

        LOGGER.info("Start calculateDistanceForNeighbours");
        g.calculateDistanceForNeighbours();

        assert g.findNearestVertex(start).getIdentificator() == identificatorStartVertex;
        g.checkGraph(identificatorStartVertex);
        return g;
    }

    private static Vertex getNearestVertex(Graph g, LatLon start, long identificatorStartVertex) {
        final Vertex nearestVertex = g.findNearestVertex(start);
        assert g.findNearestVertex(start).getIdentificator() == identificatorStartVertex;
        return nearestVertex;
    }

    private static void generalPhases(Graph g, LatLon start, int maxDistanceKM, long identificatorStartVertex, double edgeDistance) {
        ;
        LOGGER.info("graph has: {} vertexes", g.size());
        LOGGER.info("Start removeLongAwayVertices");
        g.removeLongAwayVertices(getNearestVertex(g, start, identificatorStartVertex), maxDistanceKM / 2);
        g.checkGraph(identificatorStartVertex);
        LOGGER.info("graph2 has: {} vertices", g.getVertices().size());

        LOGGER.info("Start removeSingleEdgeVertexes");
        g.removeSingleEdgeVertexes(identificatorStartVertex);
        g.checkGraph(identificatorStartVertex);
        LOGGER.info("graph3 has: {} vertices", g.getVertices().size());

        LOGGER.info("Start addEdgesFromStartPoint");
        g.addEdgesFromStartPoint(getNearestVertex(g, start, identificatorStartVertex), edgeDistance);
        g.checkGraph(identificatorStartVertex);
        LOGGER.info("graph4 has: {} vertices", g.getVertices().size());

        LOGGER.info("Start removeNotVisitedVertexes");
        g.removeNotVisitedVertexes(getNearestVertex(g, start, identificatorStartVertex));
        g.checkGraph(identificatorStartVertex);
        LOGGER.info("graph5 has: {} vertices", g.getVertices().size());
    }
}
