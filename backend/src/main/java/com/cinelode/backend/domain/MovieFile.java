package com.cinelode.backend.domain;

/** Represents a movie entry discovered in the media library. */
public record MovieFile(String title, int year, String filePath, String franchise) {
}
