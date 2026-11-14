package com.cinelode.backend.adapter.out.omdb;

import com.cinelode.backend.application.port.SeriesMetadataProvider;
import com.cinelode.backend.domain.SeriesMetadata;
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

/** Retrieves and validates series metadata from OMDb without downloading posters. */
@Component
public class OmdbSeriesMetadataProvider implements SeriesMetadataProvider {
    private static final Pattern EMMY_COUNT =
            Pattern.compile("\\bWon\\s+(\\d+)\\s+Primetime Emmys\\b", Pattern.CASE_INSENSITIVE);

    private final String apiKey;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public OmdbSeriesMetadataProvider(@Value("${omdb.api.key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public Optional<SeriesMetadata> findByTitleAndYear(String title, int year) {
        if (apiKey.isBlank()) {
            throw new OmdbException("OMDb API key fehlt (omdb.api.key).");
        }

        String url = "https://www.omdbapi.com/?apikey=" + encode(apiKey)
                + "&type=series&t=" + encode(title) + "&y=" + year;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new OmdbException("OMDb HTTP-Status " + response.statusCode());
            }
            OmdbSeriesMetadata metadata = objectMapper.readValue(response.body(), OmdbSeriesMetadata.class);
            if (!metadata.found()) {
                if ("Series not found!".equalsIgnoreCase(metadata.error())) {
                    return Optional.empty();
                }
                throw new OmdbException("OMDb: " + metadata.error());
            }
            if (!normalize(title).equals(normalize(metadata.title()))) {
                throw new OmdbException("OMDb-Serientitel stimmt nicht sicher überein: " + metadata.title());
            }
            if (!yearMatches(year, metadata.year())) {
                throw new OmdbException("OMDb-Serienjahr stimmt nicht sicher überein: " + metadata.year());
            }
            return Optional.of(toDomain(metadata));
        } catch (OmdbException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OmdbException("OMDb-Abfrage unterbrochen.", exception);
        } catch (Exception exception) {
            throw new OmdbException("OMDb-Serienabfrage fehlgeschlagen.", exception);
        }
    }

    static SeriesMetadata toDomain(OmdbSeriesMetadata metadata) {
        Double rating = null;
        try {
            if (metadata.imdbRating() != null && !metadata.imdbRating().equalsIgnoreCase("N/A")) {
                rating = Double.valueOf(metadata.imdbRating());
            }
        } catch (NumberFormatException ignored) {
            // An unparseable optional rating is not useful metadata.
        }
        return new SeriesMetadata(
                valid(metadata.genre()),
                valid(metadata.actors()),
                valid(metadata.plot()),
                valid(metadata.runtime()),
                valid(metadata.writer()),
                valid(metadata.rated()),
                rating,
                parseEmmyWins(metadata.awards()));
    }

    private static Integer parseEmmyWins(String awards) {
        if (awards == null) {
            return null;
        }
        Matcher matcher = EMMY_COUNT.matcher(awards);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean yearMatches(int wantedYear, String actualYear) {
        try {
            return Integer.parseInt(actualYear.substring(0, 4)) == wantedYear;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String valid(String value) {
        return value == null || value.equalsIgnoreCase("N/A") ? null : value;
    }
}
