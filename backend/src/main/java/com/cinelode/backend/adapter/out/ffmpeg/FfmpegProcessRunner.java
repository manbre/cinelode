package com.cinelode.backend.adapter.out.ffmpeg;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class FfmpegProcessRunner {
    private static final long PROCESS_SHUTDOWN_TIMEOUT_SECONDS = 5;

    public void stream(List<String> command, OutputStream outputStream) throws IOException {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();

            try (var inputStream = process.getInputStream()) {
                inputStream.transferTo(outputStream);
                outputStream.flush();
            }

            if (!process.waitFor(PROCESS_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IOException("FFmpeg did not terminate in time.");
            }
            if (process.exitValue() != 0) {
                throw new IOException("FFmpeg exited with status " + process.exitValue() + ".");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("FFmpeg playback was interrupted.", exception);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroy();
                try {
                    if (!process.waitFor(PROCESS_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        process.waitFor(PROCESS_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    }
                } catch (InterruptedException exception) {
                    process.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
