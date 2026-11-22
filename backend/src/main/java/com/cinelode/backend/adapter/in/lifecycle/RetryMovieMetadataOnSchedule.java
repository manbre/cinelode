package com.cinelode.backend.adapter.in.lifecycle;

import com.cinelode.backend.application.usecase.SynchronizeLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retries transient metadata failures after the host network has had time to come up. */
@Component
public class RetryMovieMetadataOnSchedule {
    private static final Logger LOGGER = LoggerFactory.getLogger(RetryMovieMetadataOnSchedule.class);
    private static final int MAX_RETRY_PASSES = 12;

    private final SynchronizeLibrary synchronizeLibrary;
    private int retryPasses;
    private boolean finished;

    public RetryMovieMetadataOnSchedule(SynchronizeLibrary synchronizeLibrary) {
        this.synchronizeLibrary = synchronizeLibrary;
    }

    /** Retries metadata errors once a minute after startup, then every five minutes for up to one hour. */
    @Scheduled(initialDelay = 60_000, fixedDelay = 300_000)
    public synchronized void retryFailedMetadata() {
        if (finished) {
            return;
        }

        int retryCount = synchronizeLibrary.retryErroredMetadata();
        retryPasses++;
        if (retryCount == 0) {
            finished = true;
            LOGGER.info("Keine fehlgeschlagenen Filmetadaten mehr zum Wiederholen.");
            return;
        }

        if (retryPasses >= MAX_RETRY_PASSES) {
            finished = true;
            LOGGER.warn("Metadaten-Wiederholungen nach {} Durchläufen beendet; {} Filme bleiben fehlerhaft.",
                    retryPasses, retryCount);
        } else {
            LOGGER.info("Metadaten-Wiederholungsdurchlauf {}: {} Filme werden erneut abgefragt.",
                    retryPasses, retryCount);
        }
    }
}
