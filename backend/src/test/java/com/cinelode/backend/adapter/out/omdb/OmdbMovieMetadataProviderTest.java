package com.cinelode.backend.adapter.out.omdb;

import com.cinelode.backend.domain.MovieMetadata;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OmdbMovieMetadataProviderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Extracts the Oscar win total from an OMDb awards string. */
    @Test
    void extractsOscarCountFromAwardsText() throws Exception {
        OmdbMovieMetadata omdbMetadata = objectMapper.readValue(
                """
                {"Awards":"Won 5 Oscars. Another 12 wins & 34 nominations."}
                """,
                OmdbMovieMetadata.class);

        MovieMetadata metadata = OmdbMovieMetadataProvider.toDomain(omdbMetadata);

        assertEquals(5, metadata.oscarWins());
    }

    /** Leaves the Oscar win total empty when the awards text only reports nominations. */
    @Test
    void leavesOscarCountNullWhenAwardsTextOnlyReportsNominations() throws Exception {
        OmdbMovieMetadata omdbMetadata = objectMapper.readValue(
                """
                {"Awards":"Nominated for 5 Oscars"}
                """,
                OmdbMovieMetadata.class);

        MovieMetadata metadata = OmdbMovieMetadataProvider.toDomain(omdbMetadata);

        assertNull(metadata.oscarWins());
    }

    /** Uses the count following "Won" when nominations are also reported. */
    @Test
    void extractsOscarWinsWithoutUsingTheNominationCount() throws Exception {
        OmdbMovieMetadata omdbMetadata = objectMapper.readValue(
                """
                {"Awards":"Nominated for 8 Oscars. Won 2 Oscars."}
                """,
                OmdbMovieMetadata.class);

        MovieMetadata metadata = OmdbMovieMetadataProvider.toDomain(omdbMetadata);

        assertEquals(2, metadata.oscarWins());
    }
}
