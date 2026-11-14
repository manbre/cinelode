package com.cinelode.backend.domain;

/** Represents an episode filename mapped to a season and episode number. */
public record EpisodeFile(int episodeNumber, String filePath) {
}
