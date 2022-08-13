package com.autoroute.tags;

import com.autoroute.osm.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class TagsFileReader {

    private final Set<Tag> tags;

    public TagsFileReader() {
        this.tags = new HashSet<>();
    }

    public void readTags() throws IOException {
        var lines =
            Files.readAllLines(
                Paths.get("config")
                    .resolve(Paths.get("cool_tags.txt")));
        for (String line : lines) {
            final String[] split_line = line.split(" ");
            if (split_line.length != 2) {
                throw new IllegalStateException("tags are not correct: " + line);
            }
            final String key = split_line[0].strip();
            final String value = split_line[1].strip();
            Tag tag = new Tag(key, value);
            if (tags.contains(tag)) {
                throw new IllegalStateException("tags are not correct, duplicate key: " + key);
            }
            tags.add(tag);
        }

    }

    public Set<Tag> getTags() {
        return tags;
    }
}