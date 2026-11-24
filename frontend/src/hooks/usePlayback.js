import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { buildPlaybackUrl, getPlaybackBase } from '../utils/playback'
import { isNativeWebView } from '../utils/runtime'
import { postJson } from '../utils/auth'

export function usePlayback({ selectedMovie, setError }) {
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
  const [canResume, setCanResume] = useState(false)
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
  const previewTransitionTimeoutRef = useRef(null)
  const previewTransitioningRef = useRef(false)

  const selectedProgress = selectedMovie?.source === 'SERIES'
    ? selectedMovie.episodes?.find((episode) => episode.id === selectedMovie.episodeId)?.playbackPosition
    : selectedMovie?.playbackPosition
  const selectedProgressKey = selectedMovie?.source === 'SERIES'
    ? `episode:${selectedMovie.episodeId}`
    : `movie:${selectedMovie?.id}`
  const hasSavedProgress = selectedMovie?.source !== 'EXTERNAL'
    && Math.max(selectedProgress ?? 0, savedPositionsRef.current.get(selectedProgressKey) ?? 0) > 0

  const persistProgress = useCallback((movie, position) => {
    if (!movie || movie.source === 'EXTERNAL' || !Number.isFinite(position) || position < 0) {
      return
    }
    const key = movie.source === 'SERIES' ? `episode:${movie.episodeId}` : `movie:${movie.id}`
    if (savedPositionsRef.current.get(key) === position) {
      return
    }
    savedPositionsRef.current.set(key, position)
    setProgressRevision((revision) => revision + 1)
    const progressUrl = movie.source === 'SERIES'
      ? `/api/series/episodes/${movie.episodeId}/progress`
      : `/api/movies/${movie.id}/progress`
    postJson(progressUrl, { position })
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

  const selectContent = useCallback((movie) => {
    videoRef.current?.pause()
    setError('')
    selectedMovieRef.current = movie
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
  }, [setError])

  const clearSelection = useCallback(() => {
    videoRef.current?.pause()
    selectedMovieRef.current = null
  }, [])

  const resumePreview = useCallback(() => {
    if (!videoRef.current) {
      return
    }
    videoRef.current.muted = false
    videoRef.current.play().catch(() => {
      setError('Playback with audio could not be started.')
    })
  }, [setError])

  const returnToPreview = useCallback(() => {
    persistProgress(selectedMovieRef.current, playbackPositionRef.current)
    setIsFullPlayback(false)
    setKeepPlaybackStream(true)
  }, [persistProgress])

  useEffect(() => {
    setCanResume(false)
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
          setCanResume(true)
        }
      })
      .catch((cause) => {
        if (cause.name !== 'AbortError') {
          setCanResume(false)
        }
      })

    return () => controller.abort()
  }, [selectedMovie, progressRevision])

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
    let wasFullscreen = document.fullscreenElement === playerContainerRef.current
    const updateFullscreen = () => {
      const fullscreen = document.fullscreenElement === playerContainerRef.current
      if (wasFullscreen && !fullscreen) {
        persistProgress(selectedMovieRef.current, playbackPositionRef.current)
      }
      wasFullscreen = fullscreen
      setIsFullscreen(fullscreen)
    }
    document.addEventListener('fullscreenchange', updateFullscreen)
    return () => document.removeEventListener('fullscreenchange', updateFullscreen)
  }, [persistProgress])

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
    hasSavedProgress,
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
