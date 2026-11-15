const movieTitleCollator = new Intl.Collator('de', { sensitivity: 'base' })
const franchiseNumberCollator = new Intl.Collator('de', { numeric: true })

export function isMovie(content) {
  return content?.source === 'LOCAL' || content?.source === 'EXTERNAL'
}

export function contentKey(content) {
  return `${content.source}:${content.id}`
}

function firstEpisodeSelection(season) {
  const episode = [...(season.episodes ?? [])]
    .sort((first, second) => first.episodeNumber - second.episodeNumber)[0]
  return episode
    ? {
        ...season,
        episodeId: episode.id,
        episodeNumber: episode.episodeNumber,
      }
    : null
}

export function getNextPlaybackSelection({
  selectedContent,
  playlist,
  localMovies,
  seriesSeasons,
}) {
  if (playlist.length > 0) {
    const playlistIndex = playlist.findIndex(
      (item) => contentKey(item) === contentKey(selectedContent)
    )
    return playlistIndex >= 0 ? playlist[playlistIndex + 1] ?? null : null
  }

  if (selectedContent.source === 'SERIES') {
    const seasonIndex = seriesSeasons.findIndex(
      (season) => contentKey(season) === contentKey(selectedContent)
    )
    const currentEpisodeIndex = (selectedContent.episodes ?? [])
      .findIndex((episode) => episode.id === selectedContent.episodeId)
    const nextEpisode = [...(selectedContent.episodes ?? [])]
      .sort((first, second) => first.episodeNumber - second.episodeNumber)
      .find((episode) => episode.episodeNumber > selectedContent.episodeNumber)

    if (seasonIndex >= 0 && nextEpisode) {
      return {
        ...selectedContent,
        episodeId: nextEpisode.id,
        episodeNumber: nextEpisode.episodeNumber,
      }
    }

    if (seasonIndex >= 0 && currentEpisodeIndex >= 0) {
      for (const season of seriesSeasons.slice(seasonIndex + 1)) {
        const nextSeason = firstEpisodeSelection(season)
        if (nextSeason) {
          return nextSeason
        }
      }
    }
    return null
  }

  const movieIndex = localMovies.findIndex(
    (movie) => contentKey(movie) === contentKey(selectedContent)
  )
  return movieIndex >= 0 ? localMovies[movieIndex + 1] ?? null : null
}

// Shared catalog helpers for consistent localized sorting and person matching.
export function normalizePersonName(name) {
  return name.normalize('NFC').toLocaleLowerCase('de')
}

export function compareMovies(first, second) {
  const firstFranchise = first.franchise?.trim()
  const secondFranchise = second.franchise?.trim()
  const groupComparison = movieTitleCollator.compare(
    firstFranchise || first.title,
    secondFranchise || second.title
  )
  if (groupComparison !== 0) {
    return groupComparison
  }

  if (Boolean(firstFranchise) !== Boolean(secondFranchise)) {
    return firstFranchise ? -1 : 1
  }

  if (firstFranchise && secondFranchise) {
    if (first.franchiseNumber && second.franchiseNumber) {
      const numberComparison = franchiseNumberCollator.compare(
        first.franchiseNumber,
        second.franchiseNumber
      )
      if (numberComparison !== 0) {
        return numberComparison
      }
    } else if (first.franchiseNumber || second.franchiseNumber) {
      return first.franchiseNumber ? -1 : 1
    }
  }

  return movieTitleCollator.compare(first.title, second.title)
    || (first.year ?? 0) - (second.year ?? 0)
}

export function getPeople(movie, role) {
  return [movie[role]]
    .flatMap((value) => value?.split(',') ?? [])
    .map((name) => name.trim())
    .filter(Boolean)
}
