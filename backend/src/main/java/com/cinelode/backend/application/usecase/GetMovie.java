package com.cinelode.backend.application.usecase;

import com.cinelode.backend.application.port.MovieRepository;
import com.cinelode.backend.domain.Movie;

import java.util.Optional;

/** Loads a movie by its catalog identifier. */
public class GetMovie {
    private final MovieRepository movies;

    /** Creates the use case with its movie repository. */
    public GetMovie(MovieRepository movies) {
        this.movies = movies;
    }

    /** Returns the movie with the requested identifier, if present. */
    public Optional<Movie> execute(long id) {
        return movies.findById(id);
    }
}
