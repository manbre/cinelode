package com.cinelode.backend.adapter.out.ffmpeg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfmpegProcessRunnerTest {
    @TempDir
    Path temporaryDirectory;

    private final FfmpegProcessRunner runner = new FfmpegProcessRunner();

    @Test
    void streamsSuccessfulProcessOutput() throws Exception {
        Path script = createScript("printf 'fragmented-mp4'");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        runner.stream(List.of(script.toString()), output);

        assertEquals("fragmented-mp4", output.toString());
    }

    @Test
    void reportsNonZeroExitStatus() throws Exception {
        Path script = createScript("exit 7");

        IOException error = assertThrows(IOException.class,
                () -> runner.stream(List.of(script.toString()), new ByteArrayOutputStream()));

        assertTrue(error.getMessage().contains("status 7"));
    }

    @Test
    void terminatesTheProcessWhenTheClientStreamFails() throws Exception {
        Path pidFile = temporaryDirectory.resolve("ffmpeg.pid");
        Path script = createScript("printf '%s' \"$$\" > '" + pidFile + "'\n"
                + "while true; do printf 'stream-data'; done");
        OutputStream disconnectedClient = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("client disconnected");
            }
        };

        assertThrows(IOException.class,
                () -> runner.stream(List.of(script.toString()), disconnectedClient));

        long processId = Long.parseLong(Files.readString(pidFile));
        assertFalse(ProcessHandle.of(processId).map(ProcessHandle::isAlive).orElse(false));
    }

    private Path createScript(String body) throws IOException {
        Path script = Files.createTempFile(temporaryDirectory, "fake-ffmpeg-", ".sh");
        Files.writeString(script, "#!/bin/sh\n" + body + "\n");
        assertTrue(script.toFile().setExecutable(true));
        return script;
    }
}
