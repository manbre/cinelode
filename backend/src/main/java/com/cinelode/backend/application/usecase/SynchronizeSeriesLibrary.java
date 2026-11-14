package com.cinelode.backend.application.usecase;

import com.cinelode.backend.application.port.SeriesCatalog;
import com.cinelode.backend.application.port.SeriesMetadataProvider;
import com.cinelode.backend.application.port.SeriesRepository;
import com.cinelode.backend.domain.MetadataStatus;
import com.cinelode.backend.domain.Series;
import com.cinelode.backend.domain.SeriesFile;
import com.cinelode.backend.domain.SeriesMetadata;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Discovers series and stores shared metadata once per series. */
public class SynchronizeSeriesLibrary {
    private static final int MAX_METADATA_ATTEMPTS = 3;
    private static final Logger LOGGER = Logger.getLogger(SynchronizeSeriesLibrary.class.getName());

    private final SeriesCatalog catalog;
    private final SeriesRepository repository;
    private final SeriesMetadataProvider metadataProvider;

    public SynchronizeSeriesLibrary(
            SeriesCatalog catalog,
            SeriesRepository repository,
            SeriesMetadataProvider metadataProvider) {
        this.catalog = catalog;
        this.repository = repository;
        this.metadataProvider = metadataProvider;
    }

    public int execute() {
        List<SeriesFile> discovered = catalog.scan();
        repository.synchronize(discovered);

        for (int attempt = 1; attempt <= MAX_METADATA_ATTEMPTS; attempt++) {
            List<Series> incomplete = repository.findSeriesWithIncompleteMetadata();
            if (incomplete.isEmpty()) {
                return discovered.size();
            }
            int currentAttempt = attempt;
            LOGGER.info(() -> "OMDb-Serienmetadatenversuch " + currentAttempt + "/" + MAX_METADATA_ATTEMPTS
                    + " für " + incomplete.size() + " Serien.");
            incomplete.forEach(this::enrichMetadata);
        }

        List<Series> incomplete = repository.findSeriesWithIncompleteMetadata();
        if (!incomplete.isEmpty()) {
            String titles = incomplete.stream()
                    .map(series -> series.title() + " (" + series.year() + ")")
                    .toList()
                    .toString();
            throw new IncompleteMetadataException("Serienmetadaten nach " + MAX_METADATA_ATTEMPTS
                    + " Versuchen nicht vollständig: " + titles);
        }
        return discovered.size();
    }

    private void enrichMetadata(Series series) {
        try {
            Optional<SeriesMetadata> metadata = metadataProvider.findByTitleAndYear(series.title(), series.year());
            if (metadata.isEmpty()) {
                repository.markMetadataStatus(series.id(), MetadataStatus.NOT_FOUND);
                return;
            }
            repository.saveMetadata(series.id(), metadata.get());
        } catch (RuntimeException exception) {
            repository.markMetadataStatus(series.id(), MetadataStatus.ERROR);
            LOGGER.log(Level.WARNING,
                    "Serienmetadatenfehler für " + series.title() + " (" + series.year() + ")", exception);
        }
    }
}
