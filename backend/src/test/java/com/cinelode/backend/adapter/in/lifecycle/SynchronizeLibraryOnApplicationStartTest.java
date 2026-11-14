package com.cinelode.backend.adapter.in.lifecycle;

import com.cinelode.backend.application.usecase.IncompleteMetadataException;
import com.cinelode.backend.application.usecase.SynchronizeLibrary;
import com.cinelode.backend.application.usecase.SynchronizeSeriesLibrary;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SynchronizeLibraryOnApplicationStartTest {
    @Test
    void allowsApplicationStartupWhenMetadataRemainsIncomplete() {
        SynchronizeLibrary synchronizer = new SynchronizeLibrary(null, null, null, null) {
            @Override
            public int execute() {
                throw new IncompleteMetadataException("Metadaten unvollständig.");
            }
        };
        SynchronizeLibraryOnApplicationStart runner =
                new SynchronizeLibraryOnApplicationStart(synchronizer, successfulSeriesSynchronizer());

        assertDoesNotThrow(() -> runner.run(new DefaultApplicationArguments()));
    }

    @Test
    void propagatesUnexpectedSynchronizationFailures() {
        SynchronizeLibrary synchronizer = new SynchronizeLibrary(null, null, null, null) {
            @Override
            public int execute() {
                throw new IllegalStateException("Medienscan fehlgeschlagen.");
            }
        };
        SynchronizeLibraryOnApplicationStart runner =
                new SynchronizeLibraryOnApplicationStart(synchronizer, successfulSeriesSynchronizer());

        assertThrows(IllegalStateException.class, () -> runner.run(new DefaultApplicationArguments()));
    }

    private static SynchronizeSeriesLibrary successfulSeriesSynchronizer() {
        return new SynchronizeSeriesLibrary(null, null, null) {
            @Override
            public int execute() {
                return 0;
            }
        };
    }
}
