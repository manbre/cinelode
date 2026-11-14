package com.cinelode.backend.application.port;

import com.cinelode.backend.domain.SeriesFile;

import java.nio.file.Path;
import java.util.List;

/** Defines discovery and safe path resolution for local series media. */
public interface SeriesCatalog {
    List<SeriesFile> scan();

    Path resolveMediaFile(String filePath);
}
