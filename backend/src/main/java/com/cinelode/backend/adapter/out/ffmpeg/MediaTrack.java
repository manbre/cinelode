package com.cinelode.backend.adapter.out.ffmpeg;

/**
 * Represents a media stream discovered by FFprobe.
 *
 * @param index        FFmpeg stream index
 * @param type         video, audio or subtitle
 * @param codec        codec name
 * @param language     ISO language tag, if available
 * @param title        optional stream title
 * @param defaultTrack whether the stream is marked as default
 */
public record MediaTrack(
        int index,
        String type,
        String codec,
        String language,
        String title,
        boolean defaultTrack
) {
}
