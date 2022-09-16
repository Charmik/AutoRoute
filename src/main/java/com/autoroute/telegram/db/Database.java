package com.autoroute.telegram.db;

import com.autoroute.osm.LatLon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class Database {

    private static final Logger LOGGER = LogManager.getLogger(Database.class);

    private final Settings settings;
    private final Connection connection;

    public Database(Settings settings) {
        this.settings = settings;
        this.connection = connect();
        createTable();
    }

    private static final String CREATE_TABLE =
        """
            CREATE TABLE IF NOT EXISTS telegram (
            id SERIAL,
            chat_id BIGINT PRIMARY KEY,
            state INT NOT NULL,
            lat decimal,
            lon decimal,
            min_distance BIGINT,
            max_distance BIGINT
            );""";

    private static final String INSERT_ROW = """
        INSERT INTO telegram(chat_id, state, lat, lon, min_distance, max_distance)  
        VALUES(?,?,?,?,?,?)""";

    private static final String SELECT_ROW_BY_CHAT_ID = "SELECT * from telegram where chat_id = ?";
    private static final String SELECT_ROW_STATE = "SELECT * from telegram where state = ?";

    private static final String UPDATE_ROW_BY_CHAT_ID =
        """
                UPDATE telegram SET state=?, lat=?, lon=?, min_distance=?, max_distance=? WHERE chat_id = ?;
            """;

    private Connection connect() {
        try {
            Connection conn = DriverManager.getConnection(settings.jdbcUrl(), settings.user(), settings.password());
            LOGGER.info("connected to database");
            return conn;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void createTable() {
        try {
            PreparedStatement statement = connection.prepareStatement(CREATE_TABLE);
            statement.execute();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void insertRow(Row row) {
        try {
            PreparedStatement pstmt = connection.prepareStatement(INSERT_ROW, Statement.RETURN_GENERATED_KEYS);
            pstmt.setLong(1, row.chatId());
            pstmt.setInt(2, row.state().getIndex());
            double lat = 0;
            double lon = 0;
            if (row.startPoint() != null) {
                lat = row.startPoint().lat();
                lon = row.startPoint().lon();
            }
            pstmt.setDouble(3, lat);
            pstmt.setDouble(4, lon);

            int minDistance = 0;
            if (row.minDistance() != null) {
                minDistance = row.minDistance();
            }
            pstmt.setInt(5, minDistance);

            int maxDistance = 0;
            if (row.minDistance() != null) {
                maxDistance = row.maxDistance();
            }
            pstmt.setInt(6, maxDistance);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    public Row getRow(long chatId) {
        try (PreparedStatement pstmt = connection.prepareStatement(SELECT_ROW_BY_CHAT_ID)) {

            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Row row = new Row(
                    rs.getLong(1),
                    rs.getLong(2),
                    State.byIndex(rs.getInt(3)),
                    new LatLon(rs.getDouble(4), rs.getDouble(5)),
                    rs.getInt(6),
                    rs.getInt(7)
                );
                return row;
            } else {
                return null;
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateRow(Row row) {
        try (PreparedStatement pstmt = connection.prepareStatement(UPDATE_ROW_BY_CHAT_ID)) {
            pstmt.setInt(1, row.state().getIndex());

            double lat = 0;
            double lon = 0;
            if (row.startPoint() != null) {
                lat = row.startPoint().lat();
                lon = row.startPoint().lon();
            }
            pstmt.setDouble(2, lat);
            pstmt.setDouble(3, lon);

            int minDistance = 0;
            if (row.minDistance() != null) {
                minDistance = row.minDistance();
            }
            pstmt.setInt(4, minDistance);

            int maxDistance = 0;
            if (row.maxDistance() != null) {
                maxDistance = row.maxDistance();
            }
            pstmt.setInt(5, maxDistance);
            pstmt.setLong(6, row.chatId());
            pstmt.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Row> getRowsByState(State state) {
        try (PreparedStatement pstmt = connection.prepareStatement(SELECT_ROW_STATE)) {

            pstmt.setInt(1, state.getIndex());
            ResultSet rs = pstmt.executeQuery();

            List<Row> rows = new ArrayList<>();
            while (rs.next()) {
                Row row = new Row(
                    rs.getLong(1),
                    rs.getLong(2),
                    State.byIndex(rs.getInt(3)),
                    new LatLon(rs.getDouble(4), rs.getDouble(5)),
                    rs.getInt(6),
                    rs.getInt(7)
                );
                rows.add(row);
            }
            return rows;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
