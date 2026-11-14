package com.cinelode.backend.application.usecase;

import com.cinelode.backend.application.port.MovieRepository;
import com.cinelode.backend.domain.Movie;

import java.util.List;

/** Loads all movies for the catalog view. */
public class ListMovies {
    private final MovieRepository movies;

    /** Creates the use case with its movie repository. */
    public ListMovies(MovieRepository movies) {
        this.movies = movies;
    }

    /** Returns all movies in the catalog. */
    public List<Movie> execute() {
        return movies.findAll();
    }
}
