package com.cinelode.backend.adapter.out.omdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Maps the metadata fields returned by the OMDb API. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OmdbMovieMetadata(
        @JsonProperty("Response") String response,
        @JsonProperty("Error") String error,
        @JsonProperty("Title") String title,
        @JsonProperty("Year") String year,
        @JsonProperty("Genre") String genre,
        @JsonProperty("Director") String director,
        @JsonProperty("Actors") String actors,
        @JsonProperty("Plot") String plot,
        @JsonProperty("Runtime") String runtime,
        @JsonProperty("Writer") String writer,
        @JsonProperty("Rated") String rated,
        @JsonProperty("imdbRating") String imdbRating,
        @JsonProperty("Awards") String awards,
        @JsonProperty("Poster") String posterUrl) {
    /** Checks whether OMDb returned a movie record. */
    boolean found() {
        return "True".equalsIgnoreCase(response);
    }
}
