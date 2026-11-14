package com.cinelode.backend.adapter.out.omdb;

import com.cinelode.backend.domain.SeriesMetadata;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OmdbSeriesMetadataProviderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void extractsPrimetimeEmmyWinsAndSharedSeriesMetadata() throws Exception {
        OmdbSeriesMetadata response = objectMapper.readValue("""
                {
                  "Genre":"Crime, Drama",
                  "Actors":"Actor One, Actor Two",
                  "Plot":"A plot",
                  "Runtime":"45 min",
                  "Writer":"Writer",
                  "Rated":"TV-MA",
                  "imdbRating":"9.5",
                  "Awards":"Won 16 Primetime Emmys. 58 wins & 248 nominations total."
                }
                """, OmdbSeriesMetadata.class);

        SeriesMetadata metadata = OmdbSeriesMetadataProvider.toDomain(response);

        assertEquals("Crime, Drama", metadata.genre());
        assertEquals("45 min", metadata.runtime());
        assertEquals(9.5, metadata.imdbRating());
        assertEquals(16, metadata.primetimeEmmyWins());
    }

    @Test
    void leavesEmmyWinsUnknownWhenAwardsOnlyReportNominations() throws Exception {
        OmdbSeriesMetadata response = objectMapper.readValue("""
                {"Awards":"Nominated for 12 Primetime Emmys"}
                """, OmdbSeriesMetadata.class);

        assertNull(OmdbSeriesMetadataProvider.toDomain(response).primetimeEmmyWins());
    }
}
