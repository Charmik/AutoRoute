package com.autoroute.api.overpass;

import java.util.HashMap;
import java.util.Map;

public record Way(long id, long[] nodesIds) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Way way = (Way) o;

        return id == way.id;
    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }
}
