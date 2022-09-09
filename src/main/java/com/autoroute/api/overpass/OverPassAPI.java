package com.autoroute.api.overpass;

import com.autoroute.osm.Tag;
import de.westnordost.osmapi.OsmConnection;
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

public class OverPassAPI {

    private static final Logger LOGGER = LogManager.getLogger(OverPassAPI.class);

    private final OverpassMapDataApi overpass;

    public OverPassAPI() {
        OsmConnection connection = new OsmConnection("https://overpass-api.de/api/", "User-Agent: Mozilla/5.0");
        connection.setTimeout(30 * 60 * 1000);
        this.overpass = new OverpassMapDataApi(connection);
    }

    public List<OverpassResponse> getNodesInBoxByTags(@NotNull Box box, Set<Tag> tags) {
        StringBuilder query = new StringBuilder("<union>\n");
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
                    overpass.queryElementsWithGeometry(query.toString(), handler);
                    return true;
                } catch (Exception e) {
                    LOGGER.info("couldn't get data by OverPass API, try to repeat: " + e.getMessage());
                    return false;
                }
            })
            .limitIterationsTo(10)
            .backoff(Duration.FIVE_SECONDS, 5, Duration.FIVE_MINUTES)
            .run();
        return responses;
    }
}
