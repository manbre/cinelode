package com.cinelode.backend.adapter.out.omdb;

import com.cinelode.backend.application.port.MovieMetadataProvider;
import com.cinelode.backend.domain.MovieMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Retrieves and validates movie metadata from OMDb. */
@Component
public class OmdbMovieMetadataProvider implements MovieMetadataProvider {
    private static final Pattern OSCAR_COUNT =
            Pattern.compile("\\bWon\\s+(\\d+)\\s+Oscars?\\b", Pattern.CASE_INSENSITIVE);

    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** Creates the provider using the configured OMDb API key. */
    public OmdbMovieMetadataProvider(
            @Value("${omdb.api.key:}") String apiKey) {
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
    }

    /** Looks up a movie and validates the returned title and year. */
    @Override
    public Optional<MovieMetadata> findByTitleAndYear(String title, int year) {
        if (apiKey.isBlank()) {
            throw new OmdbException("OMDb API key fehlt (omdb.api.key).");
        }

        String url = "https://www.omdbapi.com/?apikey=" + encode(apiKey)
                + "&type=movie&t=" + encode(title) + "&y=" + year;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new OmdbException("OMDb HTTP-Status " + response.statusCode());
            }
            OmdbMovieMetadata metadata = objectMapper.readValue(response.body(), OmdbMovieMetadata.class);
            if (!metadata.found()) {
                if ("Movie not found!".equalsIgnoreCase(metadata.error())) {
                    return Optional.empty();
                }
                throw new OmdbException("OMDb: " + metadata.error());
            }
            if (!normalize(title).equals(normalize(metadata.title()))) {
                throw new OmdbException("OMDb-Titel stimmt nicht sicher überein: " + metadata.title());
            }
            if (!yearMatches(year, metadata.year())) {
                throw new OmdbException("OMDb-Jahr stimmt nicht sicher überein: " + metadata.year());
            }
            return Optional.of(toDomain(metadata));
        } catch (OmdbException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OmdbException("OMDb-Abfrage unterbrochen.", exception);
        } catch (Exception exception) {
            throw new OmdbException("OMDb-Abfrage fehlgeschlagen.", exception);
        }
    }

    /** Converts OMDb fields into provider-independent domain metadata. */
    static MovieMetadata toDomain(OmdbMovieMetadata metadata) {
        Double rating = null;
        try {
            if (metadata.imdbRating() != null && !metadata.imdbRating().equals("N/A")) {
                rating = Double.valueOf(metadata.imdbRating());
            }
        } catch (NumberFormatException ignored) {
            // An unparseable optional rating is not useful metadata.
        }
        Integer oscarWins = parseOscarWins(metadata.awards());
        return new MovieMetadata(metadata.genre(), metadata.director(), metadata.actors(), metadata.plot(),
                metadata.runtime(), metadata.writer(), metadata.rated(), rating, oscarWins, metadata.posterUrl());
    }

    /** Extracts the number of Oscars won from the awards text. */
    private static Integer parseOscarWins(String awards) {
        if (awards == null) {
            return null;
        }
        Matcher matcher = OSCAR_COUNT.matcher(awards);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** Checks whether the returned release year matches the requested year. */
    private static boolean yearMatches(int wantedYear, String actualYear) {
        try {
            return Integer.parseInt(actualYear.substring(0, 4)) == wantedYear;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** Normalizes a title for a tolerant but safe comparison. */
    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** Encodes a value for use in an OMDb query string. */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
