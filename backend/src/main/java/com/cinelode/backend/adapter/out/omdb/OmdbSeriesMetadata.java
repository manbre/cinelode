package com.cinelode.backend.adapter.out.omdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Maps the series details returned by OMDb. */
@JsonIgnoreProperties(ignoreUnknown = true)
record OmdbSeriesMetadata(
        @JsonProperty("Response") String response,
        @JsonProperty("Error") String error,
        @JsonProperty("Title") String title,
        @JsonProperty("Year") String year,
        @JsonProperty("Genre") String genre,
        @JsonProperty("Actors") String actors,
        @JsonProperty("Plot") String plot,
        @JsonProperty("Runtime") String runtime,
        @JsonProperty("Writer") String writer,
        @JsonProperty("Rated") String rated,
        @JsonProperty("imdbRating") String imdbRating,
        @JsonProperty("Awards") String awards) {
    boolean found() {
        return "True".equalsIgnoreCase(response);
    }
}
