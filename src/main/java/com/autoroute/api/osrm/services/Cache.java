package com.autoroute.api.osrm.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public class Cache {

    private static final Logger LOGGER = LogManager.getLogger(Cache.class);
    private static final Path CACHE_DIR = Paths.get("config").resolve("cache");
    private static final Path CACHE_FILE = CACHE_DIR.resolve("trip.ser");
    private static final int CACHE_MAX_SIZE = 1 << 13;

    private LRUCache cache;

    synchronized void loadCache() {
        LOGGER.info("Start loading cache");
        var startLoadCache = System.nanoTime();
        CACHE_DIR.toFile().mkdirs();
        File file = CACHE_FILE.toFile();
        try {
            FileInputStream fis = new FileInputStream(CACHE_FILE.toFile());
            ObjectInputStream ois = new ObjectInputStream(fis);
            cache = (LRUCache) ois.readObject();
            ois.close();
        } catch (FileNotFoundException e) {
            try {
                file.createNewFile();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } catch (IOException | ClassNotFoundException e) {
            this.cache = new LRUCache(CACHE_MAX_SIZE);
            flush();
        }
        if (this.cache == null) {
            this.cache = new LRUCache(CACHE_MAX_SIZE);
            flush();
        }
        var finish = System.nanoTime();
        LOGGER.info("loaded cache for: " + ((finish - startLoadCache) / 1_000_000) + " seconds");
    }

    synchronized OsrmResponse getOrNull(String request) {
        return cache.get(request);
    }

    synchronized void write(String request, OsrmResponse response) {
        cache.put(request, response);
        if (cache.size() % 100 == 0) {
            try {
                LOGGER.info("Start flushing cache with size: {}", cache.size());
                final String newFile = CACHE_FILE + "_new";
                FileOutputStream fos = new FileOutputStream(newFile);
                ObjectOutputStream oos = new ObjectOutputStream(fos);
                oos.writeObject(cache);
                oos.close();
                Files.move(Paths.get(newFile), CACHE_FILE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            LOGGER.info("Finished flushing cache");
        }
    }

    synchronized void flush() {
        writeToDisk();
    }

    private void writeToDisk() {
        try {
            final String newFile = CACHE_FILE + "_new";
            FileOutputStream fos = new FileOutputStream(newFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(cache);
            oos.close();
            Files.move(Paths.get(newFile), CACHE_FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class LRUCache extends LinkedHashMap<String, OsrmResponse> implements Serializable {

        private final int maxSize;

        public LRUCache(int capacity) {
            super(capacity, 0.75f, true);
            this.maxSize = capacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, OsrmResponse> eldest) {
            if (this.size() > maxSize) {
                LOGGER.info("cache if full, size: {}", this.size());
                return true;
            }
            return false;
        }
    }
}
