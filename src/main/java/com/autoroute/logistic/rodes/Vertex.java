package com.autoroute.logistic.rodes;

import com.autoroute.osm.LatLon;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Vertex {

    private int id; // 0..N
    private final long identificator;
    private final LatLon latLon;
    private final Set<Vertex> neighbors; // TODO: can be an array of ints of ids, but should be updated properly

    public Vertex(int id, long identificator, LatLon latLon) {
        this.id = id;
        this.identificator = identificator;
        this.latLon = latLon;
        this.neighbors = new HashSet<>();
    }

    public boolean addNeighbor(Vertex v) {
        return neighbors.add(v);
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

    public Set<Vertex> getNeighbors() {
        return neighbors;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Vertex vertex = (Vertex) o;

        if (id != vertex.id) return false;
        if (identificator != vertex.identificator) return false;
        return Objects.equals(latLon, vertex.latLon);
    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + (int) (identificator ^ (identificator >>> 32));
        result = 31 * result + (latLon != null ? latLon.hashCode() : 0);
        return result;
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
