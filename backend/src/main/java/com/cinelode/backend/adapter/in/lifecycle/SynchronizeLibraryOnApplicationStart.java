package com.cinelode.backend.adapter.in.lifecycle;

import com.cinelode.backend.application.usecase.IncompleteMetadataException;
import com.cinelode.backend.application.usecase.SynchronizeLibrary;
import com.cinelode.backend.application.usecase.SynchronizeSeriesLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Starts library synchronization when the application is ready. */
@Component
public class SynchronizeLibraryOnApplicationStart implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(SynchronizeLibraryOnApplicationStart.class);

    private final SynchronizeLibrary synchronizeLibrary;
    private final SynchronizeSeriesLibrary synchronizeSeriesLibrary;

    /** Creates the startup adapter with both catalog synchronization use cases. */
    public SynchronizeLibraryOnApplicationStart(
            SynchronizeLibrary synchronizeLibrary,
            SynchronizeSeriesLibrary synchronizeSeriesLibrary) {
        this.synchronizeLibrary = synchronizeLibrary;
        this.synchronizeSeriesLibrary = synchronizeSeriesLibrary;
    }

    /** Runs library synchronization after the application context starts. */
    @Override
    public void run(ApplicationArguments arguments) {
        try {
            synchronizeLibrary.execute();
        } catch (IncompleteMetadataException exception) {
            LOGGER.warn("Bibliothek gestartet, aber einige Filmdatensätze sind noch unvollständig: {}",
                    exception.getMessage());
        }
        try {
            synchronizeSeriesLibrary.execute();
        } catch (IncompleteMetadataException exception) {
            LOGGER.warn("Bibliothek gestartet, aber einige Serienmetadaten sind noch unvollständig: {}",
                    exception.getMessage());
        }
    }
}
