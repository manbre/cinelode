import { useMemo } from 'react'
import { compareMovies, getPeople, normalizePersonName } from '../utils/catalog'

// Derives the sorted catalog, available filters, and visible results from Home's state.
export function useCatalog({
  movies,
  seriesSeasons,
  isSeriesMode,
  search,
  selectedDuration,
  selectedGenre,
  selectedPerson,
  selectedRated,
}) {
  const localMovies = useMemo(
    () => movies.filter((movie) => movie.source === 'LOCAL').sort(compareMovies),
    [movies]
  )

  const catalogCards = useMemo(
    () => isSeriesMode ? seriesSeasons : localMovies,
    [isSeriesMode, localMovies, seriesSeasons]
  )

  const availableGenres = useMemo(
    () => [...new Set(catalogCards.flatMap((movie) =>
      movie.genre?.split(',').map((genre) => genre.trim()).filter(Boolean) ?? []
    ))].sort((first, second) => first.localeCompare(second, 'de')),
    [catalogCards]
  )

  const personMovieCounts = useMemo(() => {
    const counts = new Map()
    for (const movie of catalogCards) {
      for (const role of ['director', 'actors', 'writer']) {
        for (const name of new Set(getPeople(movie, role))) {
          const key = `${role}:${normalizePersonName(name)}`
          counts.set(key, (counts.get(key) ?? 0) + 1)
        }
      }
    }
    return counts
  }, [catalogCards])

  const ratedMovieCounts = useMemo(() => {
    const counts = new Map()
    for (const movie of catalogCards) {
      const rated = movie.rated?.trim()
      if (rated) {
        const key = rated.normalize('NFC').toLocaleLowerCase('de')
        counts.set(key, (counts.get(key) ?? 0) + 1)
      }
    }
    return counts
  }, [catalogCards])

  const filteredMovies = useMemo(() => {
    const query = search.trim().toLocaleLowerCase('de')
    const durationLimit = isSeriesMode ? 25 : 105
    const matchesDuration = (movie) => {
      if (!selectedDuration) {
        return true
      }
      const runtimeMinutes = Number.parseInt(movie.runtime?.match(/\d+/)?.[0] ?? '', 10)
      return runtimeMinutes <= durationLimit
    }

    if (selectedPerson) {
      return catalogCards.filter((movie) => matchesDuration(movie)
        && getPeople(movie, selectedPerson.role).some(
          (name) => normalizePersonName(name) === normalizePersonName(selectedPerson.name)
        ))
    }

    if (selectedRated) {
      return catalogCards.filter(
        (movie) => matchesDuration(movie)
          && movie.rated?.trim().normalize('NFC').toLocaleLowerCase('de') === selectedRated
      )
    }

    return catalogCards.filter((movie) => {
      const searchableFields = [
        movie.title,
        movie.director,
        movie.actors,
        movie.writer,
        movie.seasonNumber ? `Season ${movie.seasonNumber}` : '',
      ]
      const matchesSearch = !query || searchableFields.some((field) =>
        field?.toLocaleLowerCase('de').includes(query)
      )
      return matchesSearch
        && matchesDuration(movie)
        && (!selectedGenre || movie.genre?.split(',').some(
          (genre) => genre.trim() === selectedGenre
        ))
    })
  }, [
    catalogCards,
    isSeriesMode,
    search,
    selectedDuration,
    selectedGenre,
    selectedPerson,
    selectedRated,
  ])

  return {
    localMovies,
    availableGenres,
    personMovieCounts,
    ratedMovieCounts,
    filteredMovies,
  }
}
