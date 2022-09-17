package com.autoroute.api.overpass;

import com.autoroute.osm.Tag;
import de.westnordost.osmapi.OsmConnection;
import de.westnordost.osmapi.common.errors.OsmConnectionException;
import de.westnordost.osmapi.map.data.BoundingBox;
import de.westnordost.osmapi.map.data.LatLon;
import de.westnordost.osmapi.map.data.Node;
import de.westnordost.osmapi.map.data.Relation;
import de.westnordost.osmapi.map.data.Way;
import de.westnordost.osmapi.overpass.MapDataWithGeometryHandler;
import de.westnordost.osmapi.overpass.OverpassMapDataApi;
import org.apache.brooklyn.util.repeat.Repeater;
import org.apache.brooklyn.util.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class OverPassAPI {

    private static final Logger LOGGER = LogManager.getLogger(OverPassAPI.class);

    private static final String[] URLS = {
        "https://overpass-api.de/api/",
        "https://overpass.kumi.systems/api/",
        "http://overpass.openstreetmap.ru/cgi/",
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

        LOGGER.info("query:\n" + query);

        List<OverpassResponse> responses = new ArrayList<>();

        final MapDataWithGeometryHandler handler = new MapDataWithGeometryHandler() {
            @Override
            public void handle(@NotNull BoundingBox boundingBox) {
                LOGGER.info("handle with boundingBox not implemented");
            }

            @Override
            public void handle(@NotNull Node node) {
                var position = node.getPosition();

                var response = new OverpassResponse(node.getId(), node.getTags(),
                    new com.autoroute.osm.LatLon(position.getLatitude(), position.getLongitude()));
                responses.add(response);
            }

            @Override
            public void handle(@NotNull Way way, @NotNull BoundingBox boundingBox, @NotNull List<LatLon> list) {
                LOGGER.info("handle with way not implemented");
            }

            @Override
            public void handle(@NotNull Relation relation, @NotNull BoundingBox boundingBox, @NotNull Map<Long, LatLon> map, @NotNull Map<Long, List<LatLon>> map1) {
                LOGGER.info("handle with relation not implemented");
            }
        };

        Repeater.create("Wait to get data by OverPass API")
            .until(() -> {
                try {
                    OverpassMapDataApi overpass = createOverpass();
                    overpass.queryElementsWithGeometry(query.toString(), handler);
                    return true;
                } catch (OsmConnectionException e) {
                    LOGGER.info("couldn't get data by OverPass API, try to repeat: ", e);
                    LOGGER.info(e.getMessage() + " " + e.getDescription() + e);
                    return false;
                } catch (Exception e) {
                    LOGGER.info("couldn't get data by OverPass API, try to repeat: ", e);
                    return false;
                }
            })
            .limitIterationsTo(20)
            .backoff(Duration.FIVE_SECONDS, 2, Duration.FIVE_MINUTES)
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
}
