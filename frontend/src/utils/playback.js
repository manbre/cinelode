const LANGUAGE_CODES = {
  deu: 'de',
  ger: 'de',
  eng: 'en',
  fra: 'fr',
  fre: 'fr',
  spa: 'es',
  ita: 'it',
  jpn: 'ja',
  zho: 'zh',
  chi: 'zh',
  kor: 'ko',
  nld: 'nl',
  dut: 'nl',
  por: 'pt',
  rus: 'ru',
  ara: 'ar',
  hin: 'hi',
  und: '',
}

const languageNames = new Intl.DisplayNames(['de'], { type: 'language' })

export function getPlaybackBase(selectedMovie) {
  return selectedMovie.source === 'SERIES'
    ? `/api/series/episodes/${selectedMovie.episodeId}`
    : `/api/movies/${selectedMovie.id}`
}

export function buildPlaybackUrl(selectedMovie, {
  isFullPlayback,
  selectedAudioTrack,
  selectedSubtitleTrack,
  playbackStart,
  previewIteration,
}) {
  if (!selectedMovie || selectedMovie.source === 'EXTERNAL') {
    return ''
  }

  const params = new URLSearchParams()
  if (isFullPlayback) {
    if (selectedAudioTrack) {
      params.set('audioTrack', selectedAudioTrack)
    }
    if (selectedSubtitleTrack) {
      params.set('subtitleTrack', selectedSubtitleTrack)
    }
    if (playbackStart > 0) {
      params.set('start', playbackStart.toFixed(3))
    }
  } else {
    params.set('preview', 'true')
    params.set('cycle', String(previewIteration))
  }
  const query = params.toString()
  return `${getPlaybackBase(selectedMovie)}/play${query ? `?${query}` : ''}`
}

// Formats playback values for display in the player controls.
export function formatTime(seconds) {
  const safeSeconds = Number.isFinite(seconds) ? Math.max(0, Math.floor(seconds)) : 0
  const hours = Math.floor(safeSeconds / 3600)
  const minutes = Math.floor((safeSeconds % 3600) / 60)
  const remainingSeconds = safeSeconds % 60
  const time = [minutes, remainingSeconds]
    .map((part) => String(part).padStart(2, '0'))
    .join(':')
  return hours ? `${hours}:${time}` : time
}

export function trackLabel(track, index) {
  const rawLanguage = track.language?.trim().toLowerCase()
  const languageCode = rawLanguage ? LANGUAGE_CODES[rawLanguage] ?? rawLanguage : ''
  const language = languageCode ? languageNames.of(languageCode) : ''
  const title = track.title?.trim()

  if (language && title) {
    return `${language} — ${title}`
  }
  if (language || title) {
    return language || title
  }
  return `Spur ${index + 1}`
}
