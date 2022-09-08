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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public class Cache {

    private static final Logger LOGGER = LogManager.getLogger(Cache.class);
    private static final String CACHE_FILE = "config/cache/trip.ser";

    private Map<String, OsrmResponse> cache;

    synchronized void loadCache() {
        var startLoadCache = System.nanoTime();
        File file = new File(CACHE_FILE);
        try {
            FileInputStream fis = new FileInputStream(CACHE_FILE);
            ObjectInputStream ois = new ObjectInputStream(fis);
            cache = (Map<String, OsrmResponse>) ois.readObject();
            ois.close();
        } catch (FileNotFoundException e) {
            try {
                file.createNewFile();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } catch (IOException | ClassNotFoundException e) {
            this.cache = new HashMap<>();
            flush();
        }
        if (this.cache == null) {
            this.cache = new HashMap<>();
            flush();
        }
        var finish = System.nanoTime();
        LOGGER.info("load cache for: " + ((finish - startLoadCache) / 1_000_000) + " seconds");
    }

    synchronized OsrmResponse getOrNull(String request) {
        return cache.get(request);
    }

    synchronized void write(String request, OsrmResponse response) {
        cache.put(request, response);
        if (cache.size() % 50 == 0) {
            try {
                final String newFile = CACHE_FILE + "_new";
                FileOutputStream fos = new FileOutputStream(newFile);
                ObjectOutputStream oos = new ObjectOutputStream(fos);
                oos.writeObject(cache);
                oos.close();
                Files.move(Paths.get(newFile), Paths.get(CACHE_FILE), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
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
            Files.move(Paths.get(newFile), Paths.get(CACHE_FILE), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
