package com.cinelode.backend.domain;

import java.util.List;

/** Represents a series directory and its discovered seasons. */
public record SeriesFile(String title, int year, String directoryPath, List<SeasonFile> seasons) {
}
