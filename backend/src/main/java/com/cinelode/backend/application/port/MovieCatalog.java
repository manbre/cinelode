package com.cinelode.backend.application.port;

import com.cinelode.backend.domain.MovieFile;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/** Defines operations for discovering and reading movie files. */
public interface MovieCatalog {
    /** Scans the configured library for supported movie entries. */
    List<MovieFile> scan();

    /** Resolves a stored path within the configured media root. */
    Path resolveMediaFile(String filePath);

    /** Reads the external URL from a TXT movie entry. */
    URI readExternalUrl(String filePath);
}
