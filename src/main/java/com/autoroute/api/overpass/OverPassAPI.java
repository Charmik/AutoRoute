package com.autoroute.api.overpass;

import com.autoroute.logistic.LatLon;
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
import java.util.concurrent.atomic.AtomicInteger;

public class OverPassAPI {

    private static final Logger LOGGER = LogManager.getLogger(OverPassAPI.class);

    // TODO: add stats by URL
    private static final String[] URLS = {
        "https://overpass-api.de/api/",
        "https://overpass.kumi.systems/api/",
//        "http://overpass.openstreetmap.ru/cgi/",
//        "https://overpass.openstreetmap.fr/api/",
    };
    private final LatLon startVertex;
    private final int maxDistance;
    private int indexURL;

    public OverPassAPI(LatLon startVertex, int maxDistance) {
        this.startVertex = startVertex;
        this.maxDistance = maxDistance;
        this.indexURL = 0;
    }

    public OverpassResponse getCities(@NotNull Box box, Set<Tag> tags) {
        StringBuilder query = new StringBuilder("[out:json][timeout:25];\n");


        LOGGER.info("query:\n{}", query);
//        return executeQuery(query);

        return null;
    }

    public OverpassResponse getNodesInBoxByTags(@NotNull Box box, Set<Tag> tags) {
        StringBuilder query = new StringBuilder("<osm-script timeout=\"360\" element-limit=\"1073741824\">\n")
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

        LOGGER.info("query:\n{}", query.toString().replaceAll("\n", ""));
        return executeQuery(query);
    }

    public OverpassResponse getRodes(@NotNull LatLon center, int radiusMeters) {
        StringBuilder query = new StringBuilder("<osm-script timeout=\"360\" element-limit=\"1073741824\">\n");
        query.append("<query type=\"way\">\n");
        query.append("<around lat=\"");
        query.append(center.lat());
        query.append("\" lon=\"");
        query.append(center.lon());
        query.append("\" radius=\"");
        query.append(radiusMeters);
        query.append("\"/>\n");
        query.append("<has-kv k=\"highway\" regv=\"trunk|primary|secondary|tertiary|trunk_link|primary_link|secondary_link|bus_guideway|road|busway\"/>\n");
        // TODO: unclassified|residential not more than 5-10% of the way? or add it to the path to sight (all types).
//        query.append("<has-kv k=\"highway\" regv=\"trunk|primary|secondary|tertiary|trunk_link|primary_link|secondary_link|bus_guideway|road|busway|unclassified|residential\"/>\n");
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
    private OverpassResponse executeQuery(StringBuilder query) {
        OverpassResponse response = new OverpassResponse();

        final MapDataHandler handler = new MapDataHandler() {
            @Override
            public void handle(BoundingBox boundingBox) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void handle(de.westnordost.osmapi.map.data.Node node) {
                var position = node.getPosition();
                final LatLon latLon = new LatLon(position.getLatitude(), position.getLongitude());

                if (LatLon.distanceKM(latLon, startVertex) < maxDistance / 2) {
                    final Map<String, String> tags = node.getTags();
                    String[] keys = new String[tags.size()];
                    String[] values = new String[tags.size()];
                    mapTagsToArrays(tags, keys, values);

                    var n = new Node(node.getId(), keys, values,
                        latLon);
                    response.add(n);
                }
            }

            @Override
            public void handle(Way way) {
                final List<Long> nodesList = way.getNodeIds();
                long[] nodes = nodesList.stream().mapToLong(i -> i).toArray();
                String refValue = way.getTags().get("ref");
                refValue = HMInterner.INTERNER.intern(refValue);
                var w = new com.autoroute.api.overpass.Way(way.getId(), nodes, refValue);
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

    private static void mapTagsToArrays(Map<String, String> tags, String[] keys, String[] values) {
        int i = 0;
        for (Map.Entry<String, String> e : tags.entrySet()) {
            keys[i] = HMInterner.INTERNER.intern(e.getKey());
            values[i] = HMInterner.INTERNER.intern(e.getValue());
            i++;
        }
    }

    @NotNull
    private OverpassMapDataApi createOverpass() {
        int timeout = 10 * 60 * 1000;
        String url = URLS[indexURL];
        indexURL = (indexURL + 1) % URLS.length;
        OsmConnection connection = new OsmConnection(
            url, "User-Agent: Mozilla/5.0", null, timeout);
        LOGGER.info("Create overpass with url: {}", url);
        return new OverpassMapDataApi(connection);
    }
}
