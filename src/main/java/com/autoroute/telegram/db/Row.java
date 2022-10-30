package com.autoroute.telegram.db;

import com.autoroute.osm.LatLon;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public record Row(@Nullable Long id, long chatId, @Nullable String userName, long date, State state, LatLon startPoint,
                  Integer minDistance,
                  Integer maxDistance) {

    public Row(long chatId, String userName, long date, State state, LatLon startPoint, Integer minDistance, Integer maxDistance) {
        this(null, chatId, userName, date, state, startPoint, minDistance, maxDistance);
        if (minDistance != null && maxDistance != null) {
            assert minDistance <= maxDistance;
        }
    }

    public Row(long chatId, @Nullable String userName, long date, State state, LatLon startPoint) {
        this(chatId, userName, date, state, startPoint, null, null);
    }

    public Row(long chatId, @Nullable String userName, long date, State empty) {
        this(chatId, userName, date, empty, null);
    }

    public Row withState(State s) {
        return new Row(id, chatId, userName, date, s, startPoint, minDistance, maxDistance);
    }

    public Row withDate(long otherDate) {
        return new Row(id, chatId, userName, otherDate, state, startPoint, minDistance, maxDistance);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Row row = (Row) o;

        if (chatId != row.chatId) return false;
        if (date != row.date) return false;
        if (!Objects.equals(userName, row.userName)) return false;
        if (state != row.state) return false;
        if (!Objects.equals(startPoint, row.startPoint)) return false;
        if (!Objects.equals(minDistance, row.minDistance)) return false;
        return Objects.equals(maxDistance, row.maxDistance);
    }

    @Override
    public int hashCode() {
        int result = (int) (chatId ^ (chatId >>> 32));
        result = 31 * result + (userName != null ? userName.hashCode() : 0);
        result = 31 * result + (int) (date ^ (date >>> 32));
        result = 31 * result + (state != null ? state.hashCode() : 0);
        result = 31 * result + (startPoint != null ? startPoint.hashCode() : 0);
        result = 31 * result + (minDistance != null ? minDistance.hashCode() : 0);
        result = 31 * result + (maxDistance != null ? maxDistance.hashCode() : 0);
        return result;
    }
}
