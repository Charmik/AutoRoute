package com.autoroute.api.overpass;

import com.autoroute.Constants;
import com.autoroute.osm.Tag;
import de.westnordost.osmapi.OsmConnection;
import de.westnordost.osmapi.common.errors.OsmConnectionException;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

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

    public List<OverpassResponse> getNodesInBoxByTags(@NotNull Box box, Set<Tag> tags) {
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

        LOGGER.info("query:\n{}", query);
        return executeQuery(query);
    }

    public List<OverpassResponse> getRodes(@NotNull com.autoroute.osm.LatLon center, int radiusMeters) {
        StringBuilder query = new StringBuilder("<osm-script timeout=\"120\" element-limit=\"1073741824\">\n");
        query.append("<query type=\"way\">\n");
        query.append("<around lat=\"");
        query.append(center.lat());
        query.append("\" lon=\"");
        query.append(center.lon());
        query.append("\" radius=\"");
        query.append(radiusMeters);
        query.append("\"/>\n");
        query.append("<has-kv k=\"highway\" regv=\"trunk|primary|secondary|tertiary|motorway_link|trunk_link|primary_link|secondary_link|bus_guideway|road|busway\"/>\n");
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
    private static List<OverpassResponse> executeQuery(StringBuilder query) {
        List<OverpassResponse> responses = new ArrayList<>();

        final MapDataHandler handler = new MapDataHandler() {
            @Override
            public void handle(BoundingBox boundingBox) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void handle(de.westnordost.osmapi.map.data.Node node) {
                var position = node.getPosition();

                var response = new OverpassResponse(node.getId(), node.getTags(),
                    new com.autoroute.osm.LatLon(position.getLatitude(), position.getLongitude()));
                responses.add(response);
            }

            @Override
            public void handle(Way way) {
                // TODO: implement if necessary for roads
            }

            @Override
            public void handle(Relation relation) {
                throw new UnsupportedOperationException();
            }
        };

        Repeater.create("Wait to get data by OverPass API")
            .until(() -> {
                try {
                    OverpassMapDataApi overpass = createOverpass();
                    overpass.queryElements(query.toString(), handler);

                    return true;
                } catch (OsmConnectionException e) {
                    LOGGER.info("couldn't get data by OverPass API, try to repeat: ", e);
                    return false;
                } catch (Exception e) {
                    LOGGER.info("couldn't get data by OverPass API, try to repeat: ", e);
                    return false;
                }
            })
            .limitIterationsTo(20)
            .backoff(Duration.FIVE_SECONDS, 2, Duration.ONE_MINUTE)
            .run();
        return responses;
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

    public static void main(String[] args) {
        final int MAX_KM = 50;
        final double DIFF_DEGREE = ((double) MAX_KM / Constants.KM_IN_ONE_DEGREE) / 2;

        final com.autoroute.osm.LatLon bor = new com.autoroute.osm.LatLon(59.908977, 29.068520);
        final Box box = new Box(
            bor.lat() - DIFF_DEGREE,
            bor.lon() - DIFF_DEGREE,
            bor.lat() + DIFF_DEGREE,
            bor.lon() + DIFF_DEGREE
        ); // bor

        final OverPassAPI overPassAPI = new OverPassAPI();

//        final List<OverpassResponse> nodes = overPassAPI.getNodesInBoxByTags(box, tagsReader.getTags());
//        System.out.println("nodes: " + nodes.size());
//        for (OverpassResponse node : nodes) {
//            System.out.println(node.latLon() + " " + node.tags());
//        }

        final List<OverpassResponse> roads =
            overPassAPI.getRodes(new com.autoroute.osm.LatLon(59.908977, 29.068520), 100000);

        System.out.println("size: " + roads.size());
//        roads.sort(Comparator.comparing(OverpassResponse::latLon));
//        String s = "[out:json][timeout:120];\n";
//        s = s + "(";
//        for (OverpassResponse resp : roads) {
//            s = s + "node(" + resp.id() + ");\n";
//        }
//        s = s + ");\n";
//        s = s + "out;";
//        System.out.println(s);
    }
}
