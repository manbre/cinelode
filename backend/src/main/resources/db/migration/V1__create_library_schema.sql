CREATE TABLE IF NOT EXISTS movies (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    year INTEGER,
    file_path TEXT NOT NULL UNIQUE,
    franchise TEXT,
    genre TEXT,
    director TEXT,
    actors TEXT,
    plot TEXT,
    runtime TEXT,
    writer TEXT,
    rated TEXT,
    imdb_rating REAL,
    oscar_wins INTEGER,
    metadata_status TEXT,
    playback_position REAL NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS series (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    year INTEGER NOT NULL,
    directory_path TEXT NOT NULL UNIQUE,
    genre TEXT,
    actors TEXT,
    plot TEXT,
    runtime TEXT,
    writer TEXT,
    rated TEXT,
    imdb_rating REAL,
    primetime_emmy_wins INTEGER,
    metadata_status TEXT
);

CREATE TABLE IF NOT EXISTS seasons (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    series_id INTEGER NOT NULL REFERENCES series(id),
    season_number INTEGER NOT NULL,
    poster_path TEXT,
    UNIQUE(series_id, season_number)
);

CREATE TABLE IF NOT EXISTS episodes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    season_id INTEGER NOT NULL REFERENCES seasons(id),
    episode_number INTEGER NOT NULL,
    file_path TEXT NOT NULL UNIQUE,
    playback_position REAL NOT NULL DEFAULT 0,
    UNIQUE(season_id, episode_number)
);
