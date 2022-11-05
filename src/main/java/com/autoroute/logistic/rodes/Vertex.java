package com.autoroute.logistic.rodes;

import com.autoroute.osm.LatLon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class Vertex {

    private static final Logger LOGGER = LogManager.getLogger(Vertex.class);

    private int id; // 0..N
    private final long identificator;
    private final LatLon latLon;
    private final List<Vertex> neighbors; // TODO: can be an array of ints of ids, but should be updated properly
    private double[] distances; // TODO: can be an array of ints of ids, but should be updated properly
    private boolean superVertex = false;

    public Vertex(int id, long identificator, LatLon latLon) {
        this.id = id;
        this.identificator = identificator;
        this.latLon = latLon;
        this.neighbors = new ArrayList<>(0);
        this.distances = null;
    }

    public Vertex(Vertex old) {
        this.id = old.id;
        this.identificator = old.identificator;
        this.latLon = old.latLon;
        this.neighbors = old.neighbors;
        this.superVertex = old.superVertex;
        this.distances = null;
    }

    public void calculateDistance() {
        this.distances = new double[neighbors.size()];
        for (int i = 0; i < neighbors.size(); i++) {
            var u = neighbors.get(i);
            distances[i] = LatLon.distanceKM(getLatLon(), u.getLatLon());
        }
    }

    public double getDistance(Vertex neighbor) {
        for (int i = 0; i < neighbors.size(); i++) {
            var v = neighbors.get(i);
            if (v.getIdentificator() == neighbor.getIdentificator()) {
                return distances[i];
            }
        }
        // TODO: fix it
        return LatLon.distanceKM(latLon, neighbor.getLatLon());
//        throw new IllegalStateException();
    }

    public boolean addNeighbor(Vertex v) {
        if (!neighbors.contains(v)) {
            neighbors.add(v);
            return true;
        }
        return false;
    }

    public boolean removeNeighbor(Vertex v) {
        return neighbors.remove(v);
    }

    public int getId() {
        return id;
    }

    public long getIdentificator() {
        return identificator;
    }

    public LatLon getLatLon() {
        return latLon;
    }

    public List<Vertex> getNeighbors() {
        return neighbors;
    }

    public boolean containsNeighbor(Vertex v) {
        return neighbors.contains(v);
    }

    public void setId(int id) {
        this.id = id;
    }

    public boolean isSuperVertex() {
        return superVertex;
    }

    public void setSuperVertex() {
        this.superVertex = true;
    }

    @Override
    public boolean equals(Object o) {
        Vertex vertex = (Vertex) o;
        return id == vertex.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        return "Vertex{" +
            "id=" + id +
            ", identificator=" + identificator +
            ", latLon=" + latLon +
            '}';
    }
}
