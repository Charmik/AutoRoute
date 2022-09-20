package com.autoroute.logistic;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AlgorithmIterationStats {

    private static final Logger LOGGER = LogManager.getLogger(AlgorithmIterationStats.class);

    private long processedIterations;
    private long startProcessingTime;
    private long lastLogTime;

    void startProcessing() {
        this.processedIterations = 0;
        final long now = System.currentTimeMillis();
        this.startProcessingTime = now;
        this.lastLogTime = now;
    }

    void endProcessing() {
        LOGGER.info("log final stats.");
        tryLogStats();
    }

    public synchronized void pushTiming(Long time) {
        processedIterations++;
        LOGGER.info("time: {} seconds", (double) time / 1000);
    }

    public synchronized void tryLogStats() {
        final long now = System.currentTimeMillis();
        if (now - lastLogTime > 60 * 1000) {
            var passedTime = ((double) now - startProcessingTime) / 1000;
            LOGGER.info("iteration stats:\nfor: {} s we processed: {} iterations\nthroughput: {} iterations per second",
                passedTime, processedIterations, (passedTime / processedIterations));
            lastLogTime = now;
        }
    }
}
