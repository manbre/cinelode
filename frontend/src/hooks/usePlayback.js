import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { buildPlaybackUrl, canStartPreviewDelay, getPlaybackBase, isPlayerFullscreen, PREVIEW_POSTER_MS } from '../utils/playback'
import { isNativeWebView } from '../utils/runtime'
import { postJson } from '../utils/auth'

export function usePlayback({ selectedMovie, setError, startupReady }) {
  const [previewPosterReady, setPreviewPosterReady] = useState(false)
  const [previewDelayElapsed, setPreviewDelayElapsed] = useState(false)
  const [previewIteration, setPreviewIteration] = useState(0)
  const [previewTransition, setPreviewTransition] = useState('visible')
  const [isFullPlayback, setIsFullPlayback] = useState(false)
  const [keepPlaybackStream, setKeepPlaybackStream] = useState(false)
  const [tracks, setTracks] = useState([])
  const [selectedAudioTrack, setSelectedAudioTrack] = useState('')
  const [selectedSubtitleTrack, setSelectedSubtitleTrack] = useState('')
  const [playbackStart, setPlaybackStart] = useState(0)
  const [currentTime, setCurrentTime] = useState(0)
  const [duration, setDuration] = useState(0)
  const [resumeAvailableFor, setResumeAvailableFor] = useState(null)
  const [progressRevision, setProgressRevision] = useState(0)
  const [volume, setVolume] = useState(1)
  const [isPlaying, setIsPlaying] = useState(false)
  const [controlsVisible, setControlsVisible] = useState(true)
  const [isFullscreen, setIsFullscreen] = useState(false)

  const videoRef = useRef(null)
  const playerContainerRef = useRef(null)
  const selectedMovieRef = useRef(null)
  const controlsTimeoutRef = useRef(null)
  const playbackPositionRef = useRef(0)
  const savedPositionsRef = useRef(new Map())
  const progressRequestsRef = useRef(new Map())
  const previewTransitionTimeoutRef = useRef(null)
  const previewTransitioningRef = useRef(false)

  const persistProgress = useCallback((movie, position) => {
    if (!movie || movie.source === 'EXTERNAL' || !Number.isFinite(position) || position < 0) {
      return Promise.resolve()
    }
    const key = movie.source === 'SERIES' ? `episode:${movie.episodeId}` : `movie:${movie.id}`
    if (savedPositionsRef.current.get(key) === position) {
      return progressRequestsRef.current.get(key) ?? Promise.resolve()
    }
    savedPositionsRef.current.set(key, position)
    setProgressRevision((revision) => revision + 1)
    const progressUrl = movie.source === 'SERIES'
      ? `/api/series/episodes/${movie.episodeId}/progress`
      : `/api/movies/${movie.id}/progress`
    const previousRequest = progressRequestsRef.current.get(key) ?? Promise.resolve()
    const request = previousRequest
      .catch(() => {})
      .then(() => postJson(progressUrl, { position }))
      .then((response) => {
        if (!response.ok) {
          throw new Error('Playback position could not be saved.')
        }
      })
      .catch(() => {
        if (savedPositionsRef.current.get(key) === position) {
          savedPositionsRef.current.delete(key)
          setProgressRevision((revision) => revision + 1)
        }
        setError('Playback position could not be saved.')
      })
      .finally(() => {
        if (progressRequestsRef.current.get(key) === request) {
          progressRequestsRef.current.delete(key)
        }
      })
    progressRequestsRef.current.set(key, request)
    return request
  }, [setError])

  const audioTracks = useMemo(
    () => tracks.filter((track) => track.type === 'audio'),
    [tracks]
  )
  const subtitleTracks = useMemo(
    () => tracks.filter((track) => track.type === 'subtitle'),
    [tracks]
  )
  const playbackUrl = useMemo(() => buildPlaybackUrl(selectedMovie, {
    isFullPlayback: isFullPlayback || keepPlaybackStream,
    selectedAudioTrack,
    selectedSubtitleTrack,
    playbackStart,
    previewIteration,
    widePreview: isNativeWebView(),
  }), [
    selectedMovie,
    isFullPlayback,
    keepPlaybackStream,
    selectedAudioTrack,
    selectedSubtitleTrack,
    playbackStart,
    previewIteration,
  ])

  const resetPreviewPoster = useCallback(() => {
    setPreviewPosterReady(false)
    setPreviewDelayElapsed(false)
  }, [])

  const selectContent = useCallback((movie) => {
    videoRef.current?.pause()
    setError('')
    selectedMovieRef.current = movie
    resetPreviewPoster()
    setKeepPlaybackStream(false)
    setPreviewIteration(0)
    setPreviewTransition('visible')
    previewTransitioningRef.current = false
    if (previewTransitionTimeoutRef.current) {
      window.clearTimeout(previewTransitionTimeoutRef.current)
    }
    setIsFullPlayback(false)
    setSelectedAudioTrack('')
    setSelectedSubtitleTrack('')
    setPlaybackStart(0)
    playbackPositionRef.current = 0
    setCurrentTime(0)
    setDuration(0)
    setTracks([])
  }, [resetPreviewPoster, setError])

  useEffect(() => {
    if (isFullPlayback || previewDelayElapsed ||
        !canStartPreviewDelay(selectedMovie, startupReady, previewPosterReady)) {
      return
    }

    const timeout = window.setTimeout(() => setPreviewDelayElapsed(true), PREVIEW_POSTER_MS)
    return () => window.clearTimeout(timeout)
  }, [isFullPlayback, previewDelayElapsed, previewPosterReady, selectedMovie, startupReady])

  const clearSelection = useCallback(() => {
    videoRef.current?.pause()
    selectedMovieRef.current = null
  }, [])

  const resumePreview = useCallback(() => {
    if (!previewDelayElapsed || !videoRef.current) {
      return
    }
    videoRef.current.muted = false
    videoRef.current.play().catch(() => {
      setError('Playback with audio could not be started.')
    })
  }, [previewDelayElapsed, setError])

  const restorePreview = useCallback(() => {
    videoRef.current?.pause()
    void persistProgress(selectedMovieRef.current, playbackPositionRef.current)
    resetPreviewPoster()
    setIsFullPlayback(false)
    setKeepPlaybackStream(false)
  }, [persistProgress, resetPreviewPoster])

  const returnToPreview = useCallback(async () => {
    if (!isPlayerFullscreen(playerContainerRef.current, document.fullscreenElement)) {
      restorePreview()
      return
    }
    try {
      await document.exitFullscreen()
    } catch {
      setError('Could not exit fullscreen.')
    }
  }, [restorePreview, setError])

  const resumeKey = selectedMovie
    ? `${selectedMovie.source}:${selectedMovie.id ?? ''}:${selectedMovie.episodeId ?? ''}:${progressRevision}`
    : ''
  const canResume = Boolean(selectedMovie && selectedMovie.source !== 'EXTERNAL'
    && resumeAvailableFor === resumeKey)

  useEffect(() => {
    if (!selectedMovie || selectedMovie.source === 'EXTERNAL') {
      return undefined
    }

    const savedPosition = selectedMovie.source === 'SERIES'
      ? selectedMovie.episodes?.find((episode) => episode.id === selectedMovie.episodeId)?.playbackPosition
      : selectedMovie.playbackPosition
    const key = selectedMovie.source === 'SERIES'
      ? `episode:${selectedMovie.episodeId}`
      : `movie:${selectedMovie.id}`
    const position = Math.max(savedPosition ?? 0, savedPositionsRef.current.get(key) ?? 0)
    if (position < 300) {
      return undefined
    }

    const controller = new AbortController()
    fetch(`${getPlaybackBase(selectedMovie)}/duration`, { signal: controller.signal })
      .then((response) => {
        if (!response.ok) {
          throw new Error('Duration could not be loaded.')
        }
        return response.json()
      })
      .then((mediaDuration) => {
        if (Number.isFinite(mediaDuration) && mediaDuration - position >= 300) {
          setResumeAvailableFor(resumeKey)
        }
      })
      .catch((cause) => {
        if (cause.name !== 'AbortError') {
          // The selection key prevents stale availability from being shown.
        }
      })

    return () => controller.abort()
  }, [selectedMovie, progressRevision, resumeKey])

  const showPlayerControls = useCallback(() => {
    if (!isFullPlayback) {
      return
    }
    setControlsVisible(true)
    if (controlsTimeoutRef.current) {
      window.clearTimeout(controlsTimeoutRef.current)
    }
    controlsTimeoutRef.current = window.setTimeout(() => {
      setControlsVisible(false)
    }, 4000)
  }, [isFullPlayback])

  const advancePreview = useCallback(() => {
    if (isFullPlayback || previewTransitioningRef.current) {
      return
    }

    previewTransitioningRef.current = true
    setPreviewTransition('fading')
    previewTransitionTimeoutRef.current = window.setTimeout(() => {
      setPreviewIteration((iteration) => iteration + 1)
      window.requestAnimationFrame(() => {
        setPreviewTransition('visible')
        previewTransitioningRef.current = false
      })
    }, 350)
  }, [isFullPlayback])

  const startPlayback = useCallback((restart = false) => {
    setError('')
    if (restart && isFullPlayback) {
      playbackPositionRef.current = 0
      setCurrentTime(0)
      if (playbackStart === 0 && videoRef.current) {
        videoRef.current.currentTime = 0
        videoRef.current.play().catch(() => {
          setError('Playback could not be started.')
        })
      } else {
        setPlaybackStart(0)
      }
    } else if (!isFullPlayback) {
      setKeepPlaybackStream(true)
      const serverPosition = selectedMovie?.source === 'SERIES'
        ? selectedMovie.episodes?.find((episode) => episode.id === selectedMovie.episodeId)?.playbackPosition
        : selectedMovie?.playbackPosition
      const key = selectedMovie?.source === 'SERIES'
        ? `episode:${selectedMovie.episodeId}`
        : `movie:${selectedMovie?.id}`
      const savedPosition = Math.max(serverPosition ?? 0, savedPositionsRef.current.get(key) ?? 0)
      const resumePosition = !restart && canResume && Number.isFinite(savedPosition)
        ? savedPosition
        : 0
      setPlaybackStart(resumePosition)
      playbackPositionRef.current = resumePosition
      setSelectedAudioTrack('')
      setSelectedSubtitleTrack('')
      setCurrentTime(resumePosition)
      setDuration(0)
      setTracks([])
      setIsFullPlayback(true)
    }
    setControlsVisible(true)
    if (playerContainerRef.current?.requestFullscreen) {
      playerContainerRef.current.requestFullscreen().catch(() => {
        setError('Could not enter fullscreen.')
      })
    } else {
      setError('Fullscreen is not supported by this browser.')
    }
    showPlayerControls()
  }, [canResume, isFullPlayback, playbackStart, selectedMovie, setError, showPlayerControls])

  useEffect(() => {
    if (!isFullPlayback || !selectedMovie || selectedMovie.source === 'EXTERNAL') {
      return undefined
    }

    controlsTimeoutRef.current = window.setTimeout(() => {
      setControlsVisible(false)
    }, 4000)
    const controller = new AbortController()
    const playbackBase = getPlaybackBase(selectedMovie)

    async function loadTracks() {
      try {
        const response = await fetch(`${playbackBase}/tracks`, {
          signal: controller.signal,
        })
        if (!response.ok) {
          throw new Error('Audio and subtitle tracks could not be loaded.')
        }
        setTracks(await response.json())
      } catch (cause) {
        if (cause.name !== 'AbortError') {
          setError(cause.message)
        }
      }
    }

    async function loadDuration() {
      try {
        const response = await fetch(`${playbackBase}/duration`, {
          signal: controller.signal,
        })
        if (!response.ok) {
          throw new Error('Video duration could not be loaded.')
        }
        const mediaDuration = await response.json()
        if (!Number.isFinite(mediaDuration) || mediaDuration <= 0) {
          throw new Error('The backend returned an invalid video duration.')
        }
        setDuration(mediaDuration)
      } catch (cause) {
        if (cause.name !== 'AbortError') {
          setError(cause.message)
        }
      }
    }

    loadTracks()
    loadDuration()
    return () => {
      controller.abort()
      if (controlsTimeoutRef.current) {
        window.clearTimeout(controlsTimeoutRef.current)
      }
    }
  }, [isFullPlayback, selectedMovie, setError])

  useEffect(() => {
    const video = videoRef.current
    if (video) {
      video.volume = volume
    }
  }, [volume, playbackUrl])

  useEffect(() => () => {
    if (previewTransitionTimeoutRef.current) {
      window.clearTimeout(previewTransitionTimeoutRef.current)
    }
  }, [])

  useEffect(() => {
    let wasFullscreen = isPlayerFullscreen(playerContainerRef.current, document.fullscreenElement)
    const updateFullscreen = () => {
      const fullscreen = isPlayerFullscreen(playerContainerRef.current, document.fullscreenElement)
      if (wasFullscreen && !fullscreen) {
        restorePreview()
      }
      wasFullscreen = fullscreen
      setIsFullscreen(fullscreen)
    }
    document.addEventListener('fullscreenchange', updateFullscreen)
    return () => document.removeEventListener('fullscreenchange', updateFullscreen)
  }, [restorePreview])

  const setSubtitleTrack = useCallback((trackIndex) => {
    setPlaybackStart(playbackPositionRef.current)
    setSelectedSubtitleTrack(trackIndex)
  }, [])

  const changePlaybackTrack = useCallback((trackIndex) => {
    setPlaybackStart(playbackPositionRef.current)
    setSelectedAudioTrack(trackIndex)
  }, [])

  const seekPlayback = useCallback((position) => {
    playbackPositionRef.current = position
    setCurrentTime(position)
    setPlaybackStart(position)
  }, [])

  return {
    audioTracks,
    canResume,
    changePlaybackTrack,
    clearSelection,
    controlsVisible,
    currentTime,
    duration,
    isFullPlayback,
    isPlaybackStream: isFullPlayback || keepPlaybackStream,
    isFullscreen,
    isPlaying,
    playbackPositionRef,
    playbackStart,
    playbackUrl,
    playerContainerRef,
    previewTransition,
    previewDelayElapsed,
    setPreviewPosterReady,
    previewIteration,
    advancePreview,
    resumePreview,
    returnToPreview,
    seekPlayback,
    selectContent,
    selectedAudioTrack,
    selectedMovieRef,
    selectedSubtitleTrack,
    setCurrentTime,
    setDuration,
    setIsFullPlayback,
    setIsPlaying,
    setPreviewIteration,
    setSubtitleTrack,
    setVolume,
    showPlayerControls,
    startPlayback,
    subtitleTracks,
    videoRef,
    volume,
  }
}
