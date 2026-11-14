package com.cinelode.backend.domain;

import java.util.List;

/** Represents one discovered season, local poster, and its episode files. */
public record SeasonFile(int seasonNumber, String posterPath, List<EpisodeFile> episodes) {
}
