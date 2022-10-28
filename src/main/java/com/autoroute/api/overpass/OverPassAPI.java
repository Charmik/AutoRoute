package com.autoroute.api.overpass;

import com.autoroute.osm.Tag;
import com.autoroute.utils.HMInterner;
import de.westnordost.osmapi.OsmConnection;
import de.westnordost.osmapi.map.data.BoundingBox;
import de.westnordost.osmapi.map.data.Relation;
import de.westnordost.osmapi.map.data.Way;
import de.westnordost.osmapi.map.handler.MapDataHandler;
import de.westnordost.osmapi.overpass.OverpassMapDataApi;
import org.apache.brooklyn.util.repeat.Repeater;
import org.apache.brooklyn.util.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class OverPassAPI {

    private static final Logger LOGGER = LogManager.getLogger(OverPassAPI.class);

    private static final String[] URLS = {
        "https://overpass-api.de/api/",
        "https://overpass.kumi.systems/api/",
//        "http://overpass.openstreetmap.ru/cgi/",
//        "https://overpass.openstreetmap.fr/api/",
    };

    public OverPassAPI() {

    }

    public OverpassResponse getCities(@NotNull Box box, Set<Tag> tags) {
        StringBuilder query = new StringBuilder("[out:json][timeout:25];\n");


        LOGGER.info("query:\n{}", query);
//        return executeQuery(query);

        return null;
    }

    public OverpassResponse getNodesInBoxByTags(@NotNull Box box, Set<Tag> tags) {
        StringBuilder query = new StringBuilder("<osm-script timeout=\"120\" element-limit=\"1073741824\">\n")
            .append("<union>\n");
        for (Tag tag : tags) {
            query.append("<query type=\"node\">\n");
            query.append("<has-kv k=\"")
                .append(tag.key()).append("\" v=\"")
                .append(tag.value()).append("\"/>");
            query.append("<bbox-query s=\"")
                .append(box.x1()).append("\" w=\"")
                .append(box.y1()).append("\" n=\"")
                .append(box.x2()).append("\" e=\"")
                .append(box.y2()).append("\"/>\n");
            query.append("</query>\n");
        }
        query.append("</union>\n");
        query.append("<print mode=\"meta\"/>\n");
        query.append("</osm-script>");

        LOGGER.info("query:\n{}", query.toString().replaceAll("\n",""));
        return executeQuery(query);
    }

    public OverpassResponse getRodes(@NotNull com.autoroute.osm.LatLon center, int radiusMeters) {
        StringBuilder query = new StringBuilder("<osm-script timeout=\"120\" element-limit=\"1073741824\">\n");
        query.append("<query type=\"way\">\n");
        query.append("<around lat=\"");
        query.append(center.lat());
        query.append("\" lon=\"");
        query.append(center.lon());
        query.append("\" radius=\"");
        query.append(radiusMeters);
        query.append("\"/>\n");
        query.append("<has-kv k=\"highway\" regv=\"trunk|primary|secondary|tertiary|trunk_link|primary_link|secondary_link|bus_guideway|road|busway\"/>\n");
        query.append("</query>\n");
        query.append("<union>\n");
        query.append("<item/>\n");
        query.append("<recurse type=\"down\"/>\n");
        query.append("</union>\n");
        query.append("<print/>\n");

        LOGGER.info("query:\n{}", query);
        return executeQuery(query);
    }

    @NotNull
    private static OverpassResponse executeQuery(StringBuilder query) {
        OverpassResponse response = new OverpassResponse();

        final MapDataHandler handler = new MapDataHandler() {
            @Override
            public void handle(BoundingBox boundingBox) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void handle(de.westnordost.osmapi.map.data.Node node) {
                var position = node.getPosition();
                var n = new Node(node.getId(), node.getTags(),
                    new com.autoroute.osm.LatLon(position.getLatitude(), position.getLongitude()));
                response.add(n);
            }

            @Override
            public void handle(Way way) {
                final List<Long> nodesList = way.getNodeIds();
                long[] nodes = nodesList.stream().mapToLong(i -> i).toArray();

                final Map<String, String> tags = way.getTags();
                String[] keys = new String[tags.size()];
                String[] values = new String[tags.size()];
                int i = 0;
                for (Map.Entry<String, String> e : tags.entrySet()) {
                    keys[i] = HMInterner.INTERNER.intern(e.getKey());
                    values[i] = HMInterner.INTERNER.intern(e.getValue());
                    i++;
                }
                var w = new com.autoroute.api.overpass.Way(way.getId(), nodes, keys, values);
                response.add(w);
            }

            @Override
            public void handle(Relation relation) {
                throw new UnsupportedOperationException();
            }
        };

        AtomicInteger counter = new AtomicInteger(1);
        Repeater.create("Wait to get data by OverPass API")
            .until(() -> {
                try {
                    OverpassMapDataApi overpass = createOverpass();
                    overpass.queryElements(query.toString(), handler);
                    return true;
                } catch (Exception e) {
                    LOGGER.info("couldn't get data by OverPass API, try to repeat: {}", counter.get());
                    counter.incrementAndGet();
                    return false;
                }
            })
            .limitIterationsTo(20)
            .backoff(Duration.FIVE_SECONDS, 2, Duration.seconds(30))
            .run();
        return response;
    }

    @NotNull
    private static OverpassMapDataApi createOverpass() {
        int timeout = 10 * 60 * 1000;
        String url = URLS[ThreadLocalRandom.current().nextInt(URLS.length)];
        OsmConnection connection = new OsmConnection(
            url, "User-Agent: Mozilla/5.0", null, timeout);
        LOGGER.info("Create overpass with url: {}", url);
        return new OverpassMapDataApi(connection);
    }
}
