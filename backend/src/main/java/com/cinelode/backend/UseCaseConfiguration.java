package com.cinelode.backend;

import com.cinelode.backend.application.port.MovieCatalog;
import com.cinelode.backend.application.port.MovieMetadataProvider;
import com.cinelode.backend.application.port.MovieRepository;
import com.cinelode.backend.application.port.PosterStorage;
import com.cinelode.backend.application.port.SeriesCatalog;
import com.cinelode.backend.application.port.SeriesMetadataProvider;
import com.cinelode.backend.application.port.SeriesRepository;
import com.cinelode.backend.application.usecase.GetMovie;
import com.cinelode.backend.application.usecase.ListMovies;
import com.cinelode.backend.application.usecase.SynchronizeLibrary;
import com.cinelode.backend.application.usecase.SynchronizeSeriesLibrary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires application use cases to their required ports. */
@Configuration
public class UseCaseConfiguration {
    /** Creates the library synchronization use case from its ports. */
    @Bean
    SynchronizeLibrary synchronizeLibrary(
            MovieCatalog catalog,
            MovieRepository movies,
            MovieMetadataProvider metadataProvider,
            PosterStorage posters) {
        return new SynchronizeLibrary(catalog, movies, metadataProvider, posters);
    }

    /** Creates the movie-list use case. */
    @Bean
    ListMovies listMovies(MovieRepository movies) {
        return new ListMovies(movies);
    }

    @Bean
    SynchronizeSeriesLibrary synchronizeSeriesLibrary(
            SeriesCatalog catalog,
            SeriesRepository repository,
            SeriesMetadataProvider metadataProvider) {
        return new SynchronizeSeriesLibrary(catalog, repository, metadataProvider);
    }

    /** Creates the single-movie lookup use case. */
    @Bean
    GetMovie getMovie(MovieRepository movies) {
        return new GetMovie(movies);
    }
}
