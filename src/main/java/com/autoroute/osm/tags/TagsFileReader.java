package com.autoroute.osm.tags;

import com.autoroute.osm.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TagsFileReader {

    private final Set<Tag> tags;

    public TagsFileReader() {
        this.tags = new HashSet<>();
    }

    public void readTags() {
        readTags("cool_tags.txt");
    }

    public void readTags(String fileName) {
        try {
            List<String> lines = Files.readAllLines(
                Paths.get("config")
                    .resolve(Paths.get(fileName)));
            tags.clear();
            for (String line : lines) {
                if (line.startsWith("--")) {
                    continue;
                }
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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Set<Tag> getTags() {
        return tags;
    }
}
