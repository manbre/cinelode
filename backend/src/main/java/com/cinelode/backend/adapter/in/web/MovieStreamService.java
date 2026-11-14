package com.cinelode.backend.adapter.in.web;

import com.cinelode.backend.application.port.MovieCatalog;
import com.cinelode.backend.domain.Movie;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Handles external redirects and byte-range streaming for local MKV files. */
@Service
public class MovieStreamService {
    private static final MediaType VIDEO_MEDIA_TYPE = MediaType.parseMediaType("video/x-matroska");

    private final MovieCatalog catalog;

    public MovieStreamService(MovieCatalog catalog) {
        this.catalog = catalog;
    }

    public ResponseEntity<?> stream(Movie movie, HttpHeaders headers) {
        final Path file;
        try {
            file = catalog.resolveMediaFile(movie.filePath());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        }
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            return ResponseEntity.notFound().build();
        }

        if (movie.filePath().toLowerCase(Locale.ROOT).endsWith(".txt")) {
            try {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .location(catalog.readExternalUrl(movie.filePath()))
                        .build();
            } catch (IllegalArgumentException | IllegalStateException exception) {
                return ResponseEntity.badRequest().build();
            }
        }

        if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mkv")) {
            return ResponseEntity.badRequest().build();
        }

        try {
            long fileSize = Files.size(file);
            final List<HttpRange> ranges;
            try {
                ranges = headers.getRange();
            } catch (IllegalArgumentException exception) {
                return rangeNotSatisfiable(fileSize);
            }
            if (ranges.isEmpty()) {
                return ResponseEntity.ok()
                        .contentType(VIDEO_MEDIA_TYPE)
                        .contentLength(fileSize)
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .body(new FileSystemResource(file));
            }
            if (ranges.size() != 1) {
                return rangeNotSatisfiable(fileSize);
            }

            HttpRange range = ranges.getFirst();
            final long start;
            final long rangeEnd;
            try {
                start = range.getRangeStart(fileSize);
                rangeEnd = range.getRangeEnd(fileSize);
            } catch (IllegalArgumentException exception) {
                return rangeNotSatisfiable(fileSize);
            }
            if (start < 0 || start >= fileSize || rangeEnd < start) {
                return rangeNotSatisfiable(fileSize);
            }

            long end = Math.min(rangeEnd, fileSize - 1);
            long contentLength = end - start + 1;
            InputStream input = Files.newInputStream(file);
            input.skipNBytes(start);

            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(VIDEO_MEDIA_TYPE)
                    .contentLength(contentLength)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize)
                    .body(new InputStreamResource(limit(input, contentLength)));
        } catch (IOException | IllegalArgumentException exception) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private static InputStream limit(InputStream input, long length) {
        return new FilterInputStream(input) {
            private long remaining = length;

            @Override
            public int read() throws IOException {
                if (remaining <= 0) {
                    return -1;
                }
                int value = super.read();
                if (value != -1) {
                    remaining--;
                }
                return value;
            }

            @Override
            public int read(byte[] buffer, int offset, int requestedLength) throws IOException {
                if (remaining <= 0) {
                    return -1;
                }
                int read = super.read(buffer, offset, (int) Math.min(requestedLength, remaining));
                if (read != -1) {
                    remaining -= read;
                }
                return read;
            }
        };
    }

    private static ResponseEntity<?> rangeNotSatisfiable(long fileSize) {
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                .build();
    }
}
