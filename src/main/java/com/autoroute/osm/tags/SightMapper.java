package com.autoroute.osm.tags;

import com.autoroute.api.overpass.Node;
import com.autoroute.osm.Tag;
import com.autoroute.sight.Sight;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SightMapper {

    public static List<Sight> getSightsFromNodes(List<Node> nodes) {
        List<Sight> sights = new ArrayList<>(nodes.size());
        for (Node node : nodes) {
            int rating = getRating(node);
            final String fee = node.tags().get("fee");
            if (node.tags().containsKey("charge") || ("yes".equals(fee))) {
                rating = 0;
            }
            String name = getName(node);
            // TODO: generate some weird name for objects without name?
            if (name == null) {
                final boolean isDrinkNode = node.tags().entrySet()
                    .stream()
                    .anyMatch(e -> isDrinkingWater(new Tag(e.getKey(), e.getValue())));
                if (isDrinkNode) {
                    name = "drink";
                }
            }

            final Sight sight = new Sight(node.id(), node.latLon(), name, rating);
            sights.add(sight);
        }
        return sights;
    }

    private static String getName(Node node) {
        var tags = node.tags();
        if (tags.containsKey("name:en")) {
            return tags.get("name:en");
        }
        if (tags.containsKey("name")) {
            return tags.get("name");
        }
        return null;
    }

    private static int getRating(Node node) {
        final Set<Tag> tags = node.getTags();
        int rating = 0;
        for (Tag tag : tags) {
            rating += tagToRating(tag);
        }
        return rating;
    }

    private static int tagToRating(Tag tag) {
        if ("no".equals(tag.value())) {
            return 0;
        }
        boolean is_drinking = isDrinkingWater(tag);
        if (is_drinking) {
            return 10;
        }
        if ("historic".equals(tag.key())) {
            return 5;
        }
        if (tag.key().contains("wiki")) {
            return 3;
        }
        return 1;
    }

    private static boolean isDrinkingWater(Tag tag) {
        final String key = tag.key();
        final String value = tag.value();
        boolean is_drinking =
            "drinking_water".equals(key) && "yes".equals(value);
        is_drinking = is_drinking |
            "amenity".equals(key) && "water_point".equals(value);
        is_drinking = is_drinking |
            "amenity".equals(key) && "drinking_water".equals(value);
        return is_drinking;
    }
}
