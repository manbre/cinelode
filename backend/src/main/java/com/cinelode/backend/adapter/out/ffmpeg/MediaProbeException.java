package com.cinelode.backend.adapter.out.ffmpeg;

/**
 * Thrown when FFprobe cannot inspect a media file.
 */
public class MediaProbeException extends RuntimeException {

    public MediaProbeException(String message) {
        super(message);
    }

    public MediaProbeException(String message, Throwable cause) {
        super(message, cause);
    }
}