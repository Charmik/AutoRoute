package com.autoroute.telegram.db;

import com.autoroute.osm.LatLon;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

class DatabaseTest {

    private Database db;
    private JdbcDatabaseContainer postgreSQLContainer;

    @BeforeEach
    public void init() {
        final String user = "charm";
        final String password = "pass";
        this.postgreSQLContainer = new PostgreSQLContainer("postgres:11.1")
            .withDatabaseName("telegram-tests-db")
            .withUsername(user)
            .withPassword(password)
            .withUrlParam("loggerLevel", "OFF");

        postgreSQLContainer.start();
        final String jdbcUrl = postgreSQLContainer.getJdbcUrl();
        Settings s = new Settings(jdbcUrl, user, password);
        this.db = new Database(s);
    }

    @AfterEach
    public void shutdown() {
        postgreSQLContainer.stop();
    }

    private Row createDefaultRow() {
        return new Row(
            15, "charm", 15, State.SENT_LOCATION, new LatLon(13.2, 14.5), 1, 2);
    }

    @Test
    void insertAndGetRow() {
        Assertions.assertNull(db.getRow(15));
        final Row r1 = createDefaultRow();
        db.insertRow(r1);
        final Row r2 = db.getRow(15);
        Assertions.assertEquals(r1, r2);
    }

    @Test
    void insertTwoRows() {
        Assertions.assertNull(db.getRow(15));
        final Row r1 = createDefaultRow();
        final Row r2 = new Row(16, "charm", 15, State.SENT_LOCATION, new LatLon(13.2, 14.5), 1, 2);
        db.insertRow(r1);
        db.insertRow(r2);
    }

    @Test
    void insertTwice() {
        final Row r1 = createDefaultRow();
        db.insertRow(r1);

        try {
            db.insertRow(r1);
            fail("second insert should fail because of duplicate primary key");
        } catch (RuntimeException e) {
            Assertions.assertTrue(e.getMessage().contains("duplicate key"));
        }
    }

    @Test
    void updateRow() {
        final Row r1 = new Row(15, "charm", 15, State.SENT_LOCATION, new LatLon(13.2, 14.5), 1, 2);
        db.insertRow(r1);
        final Row r2 = new Row(r1.chatId(), "charm", 15, State.GOT_ALL_ROUTES, new LatLon(5, 10), 1, 2);
        db.updateRow(r2);
        final Row r3 = db.getRow(15);
        Assertions.assertEquals(r2, r3);
    }

    @Test
    void userNameCanBeNull() {
        final Row r1 = new Row(15, null, 15, State.SENT_LOCATION, new LatLon(13.2, 14.5), 1, 2);
        db.insertRow(r1);
        final Row r2 = db.getRow(15);
        Assertions.assertEquals(r1, r2);
    }

    @Test
    void getRowsByState() {
        final Row r1 = new Row(1, "charm", 15, State.CREATED, new LatLon(1, 1), 1, 2);
        final Row r2 = new Row(2, "charm", 15, State.CREATED, new LatLon(2, 2), 2, 3);
        final Row r3 = new Row(3, "charm", 15, State.SENT_LOCATION, new LatLon(3, 3), 3, 4);
        final Row r4 = new Row(4, "charm", 15, State.SENT_LOCATION, new LatLon(4, 4), 4, 5);

        db.insertRow(r1);
        db.insertRow(r2);
        db.insertRow(r3);
        db.insertRow(r4);

        final List<Row> r1r2 = db.getRowsByStateSortedByDate(State.CREATED);
        Assertions.assertEquals(2, r1r2.size());
        Assertions.assertTrue(r1r2.contains(r1));
        Assertions.assertTrue(r1r2.contains(r2));

        final List<Row> r3r4 = db.getRowsByStateSortedByDate(State.SENT_LOCATION);
        Assertions.assertEquals(2, r3r4.size());
        Assertions.assertTrue(r3r4.contains(r3));
        Assertions.assertTrue(r3r4.contains(r4));

        final List<Row> empty = db.getRowsByStateSortedByDate(State.SENT_DISTANCE);
        Assertions.assertEquals(0, empty.size());
    }

    @Test
    void getRowsSortedByDate() {
        List<Row> list = new ArrayList<>();
        int n = 100;
        for (int i = 0; i < n; i++) {
            Row r = new Row(i, "charm", i, State.CREATED, new LatLon(1, 1), 1, 2);
            list.add(r);
        }
        Collections.shuffle(list);
        for (Row row : list) {
            db.insertRow(row);
        }
        final List<Row> listFromDB = db.getRowsByStateSortedByDate(State.CREATED);
        for (int i = 0; i < n; i++) {
            final long timeStamp = listFromDB.get(i).date();
            Assertions.assertEquals(i, timeStamp);
        }
    }

}