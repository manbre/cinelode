
package com.cinelode.backend.adapter.out.ffmpeg;

import com.cinelode.backend.application.port.MovieCatalog;
import com.cinelode.backend.domain.Movie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class FfprobeService {

    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final Logger LOGGER = Logger.getLogger(FfprobeService.class.getName());

    private final MovieCatalog catalog;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String executable;

    private final Map<CacheKey, MediaMetadata> cache =
            new LinkedHashMap<>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<CacheKey, MediaMetadata> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            };
    private final Map<CacheKey, FutureTask<MediaMetadata>> inFlight = new ConcurrentHashMap<>();

    public FfprobeService(
            MovieCatalog catalog,
            @Value("${cinelode.ffprobe.path:ffprobe}") String executable) {
        this.catalog = catalog;
        this.executable = executable;
    }

    public List<MediaTrack> inspect(Movie movie) {
        Path file = catalog.resolveMediaFile(movie.filePath()).normalize();
        return inspect(file);
    }

    public List<MediaTrack> inspect(Path file) {
        return inspectMetadata(file).tracks();
    }

    public double inspectDuration(Movie movie) {
        Path file = catalog.resolveMediaFile(movie.filePath()).normalize();
        return inspectDuration(file);
    }

    public double inspectDuration(Path file) {
        String rawDuration = inspectMetadata(file).duration();
        try {
            double duration = Double.parseDouble(rawDuration);
            if (!Double.isFinite(duration) || duration <= 0) {
                throw new MediaProbeException("FFprobe returned a non-positive media duration.");
            }
            return duration;
        } catch (NumberFormatException exception) {
            throw new MediaProbeException(
                    "FFprobe returned an invalid media duration.",
                    exception
            );
        }
    }

    private MediaMetadata inspectMetadata(Path requestedFile) {
        Path file = validateMediaFile(requestedFile);
        final CacheKey key;
        try {
            key = new CacheKey(file, Files.size(file), Files.getLastModifiedTime(file).toMillis());
        } catch (IOException exception) {
            throw new MediaProbeException("Could not read media file metadata.", exception);
        }

        MediaMetadata cached = getCached(key);
        if (cached != null) {
            return cached;
        }

        FutureTask<MediaMetadata> task = new FutureTask<>(() -> {
            MediaMetadata existing = getCached(key);
            if (existing != null) {
                return existing;
            }
            MediaMetadata metadata = inspectFile(file);
            putCached(key, metadata);
            return metadata;
        });
        FutureTask<MediaMetadata> previous = inFlight.putIfAbsent(key, task);
        if (previous == null) {
            try {
                task.run();
            } finally {
                inFlight.remove(key, task);
            }
        } else {
            task = previous;
        }
        try {
            return task.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MediaProbeException("FFprobe inspection was interrupted.", exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw new MediaProbeException("FFprobe inspection failed.", exception.getCause());
        }
    }

    private static Path validateMediaFile(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized) || !Files.isReadable(normalized)
                || !normalized.getFileName().toString()
                        .toLowerCase(Locale.ROOT).endsWith(".mkv")) {
            throw new MediaProbeException(
                    "Media file is missing, unreadable, or unsupported."
            );
        }
        return normalized;
    }

    private MediaMetadata inspectFile(Path file) {
        Path outputFile = null;
        Process process = null;

        try {
            outputFile = Files.createTempFile("cinelode-ffprobe-", ".json");

            process = new ProcessBuilder(
                    executable,
                    "-v", "error",
                    "-show_entries",
                    "stream=index,codec_type,codec_name:"
                            + "stream_tags=language,title:"
                            + "stream_disposition=default:"
                            + "format=duration",
                    "-of", "json",
                    file.toString()
            )
                    .redirectErrorStream(true)
                    .redirectOutput(outputFile.toFile())
                    .start();

            boolean completed = process.waitFor(
                    PROCESS_TIMEOUT.toSeconds(),
                    TimeUnit.SECONDS
            );

            if (!completed) {
                process.destroyForcibly();
                throw new MediaProbeException(
                        "FFprobe timed out while inspecting the media file."
                );
            }

            if (process.exitValue() != 0) {
                throw new MediaProbeException(
                        "FFprobe failed to inspect the media file."
                );
            }

            String output = Files.readString(
                    outputFile,
                    StandardCharsets.UTF_8
            );

            return parseMetadata(output);

        } catch (IOException exception) {
            throw new MediaProbeException(
                    "Could not execute FFprobe. Check cinelode.ffprobe.path.",
                    exception
            );

        } catch (InterruptedException exception) {
            if (process != null) {
                process.destroyForcibly();
            }

            Thread.currentThread().interrupt();

            throw new MediaProbeException(
                    "FFprobe inspection was interrupted.",
                    exception
            );

        } finally {
            terminateIfRunning(process);
            if (outputFile != null) {
                try {
                    Files.deleteIfExists(outputFile);
                } catch (IOException exception) {
                    LOGGER.log(Level.WARNING, "Could not delete FFprobe output file.", exception);
                }
            }
        }
    }

    private static void terminateIfRunning(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private synchronized MediaMetadata getCached(CacheKey key) {
        return cache.get(key);
    }

    private synchronized void putCached(
            CacheKey key,
            MediaMetadata metadata) {

        cache.keySet().removeIf(existing ->
                existing.path().equals(key.path())
                        && !existing.equals(key)
        );

        cache.put(key, metadata);
    }

    private MediaMetadata parseMetadata(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode streams = root.path("streams");

            List<MediaTrack> tracks = new ArrayList<>();

            for (JsonNode stream : streams) {
                int index = stream.path("index").asInt(-1);
                String type = stream.path("codec_type").asString("");

                if (index < 0 || !isSupportedTrackType(type)) {
                    continue;
                }

                JsonNode tags = stream.path("tags");

                tracks.add(new MediaTrack(
                        index,
                        type,
                        stream.path("codec_name").asString("unknown"),
                        tags.path("language").asString("und"),
                        tags.path("title").asString(""),
                        stream.path("disposition")
                                .path("default")
                                .asInt(0) == 1
                ));
            }

            return new MediaMetadata(List.copyOf(tracks), root.path("format").path("duration").asString(""));

        } catch (JacksonException exception) {
            throw new MediaProbeException(
                    "FFprobe returned invalid JSON.",
                    exception
            );
        }
    }

    private static boolean isSupportedTrackType(String type) {
        return "video".equals(type)
                || "audio".equals(type)
                || "subtitle".equals(type);
    }

    private record CacheKey(
            Path path,
            long size,
            long lastModified
    ) {}

    private record MediaMetadata(List<MediaTrack> tracks, String duration) {}
}
