package com.cinelode.backend.adapter.in.web;

import com.cinelode.backend.application.port.MovieCatalog;
import com.cinelode.backend.domain.Movie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MovieStreamServiceTest {
    @TempDir
    Path temporaryDirectory;

    private Path mediaFile;
    private Movie movie;
    private MovieCatalog catalog;
    private MovieStreamService streamService;

    @BeforeEach
    void setUp() throws Exception {
        mediaFile = Files.write(temporaryDirectory.resolve("Example (2024).mkv"),
                new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
        movie = new Movie(1L, "Example", 2024, mediaFile.toString(), null,
                null, null, null, null, null, null, null, null, null, null);
        catalog = mock(MovieCatalog.class);
        when(catalog.resolveMediaFile(mediaFile.toString())).thenReturn(mediaFile);
        streamService = new MovieStreamService(catalog);
    }

    @Test
    void servesAByteRangeWithCorrectContentRangeAndPayload() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RANGE, "bytes=2-5");

        var response = streamService.stream(movie, headers);

        assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());
        assertEquals("bytes 2-5/10", response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));
        InputStreamResource body = assertInstanceOf(InputStreamResource.class, response.getBody());
        assertArrayEquals(new byte[] {2, 3, 4, 5}, body.getInputStream().readAllBytes());
    }

    @Test
    void servesSuffixRangesAndRejectsUnsatisfiableOrMultipleRanges() throws Exception {
        HttpHeaders suffix = new HttpHeaders();
        suffix.set(HttpHeaders.RANGE, "bytes=-3");
        var suffixResponse = streamService.stream(movie, suffix);
        InputStreamResource body = assertInstanceOf(InputStreamResource.class, suffixResponse.getBody());
        assertArrayEquals(new byte[] {7, 8, 9}, body.getInputStream().readAllBytes());

        HttpHeaders unsatisfiable = new HttpHeaders();
        unsatisfiable.set(HttpHeaders.RANGE, "bytes=10-");
        assertEquals(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                streamService.stream(movie, unsatisfiable).getStatusCode());

        HttpHeaders multiple = new HttpHeaders();
        multiple.set(HttpHeaders.RANGE, "bytes=0-1,4-5");
        assertEquals(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                streamService.stream(movie, multiple).getStatusCode());

        HttpHeaders malformed = new HttpHeaders();
        malformed.set(HttpHeaders.RANGE, "not-a-byte-range");
        assertEquals(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                streamService.stream(movie, malformed).getStatusCode());
    }

    @Test
    void servesFullContentWhenNoRangeIsRequested() {
        var response = streamService.stream(movie, new HttpHeaders());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(10, response.getHeaders().getContentLength());
        assertInstanceOf(FileSystemResource.class, response.getBody());
        assertEquals("bytes", response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES));
    }

    @Test
    void returnsNotFoundWhenTheCatalogRejectsAnUnsafeStoredPath() {
        when(catalog.resolveMediaFile(mediaFile.toString()))
                .thenThrow(new IllegalArgumentException("outside root"));

        assertEquals(HttpStatus.NOT_FOUND,
                streamService.stream(movie, new HttpHeaders()).getStatusCode());
    }
}
