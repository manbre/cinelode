import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { buildPlaybackUrl, getPlaybackBase } from '../utils/playback'

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
  const [volume, setVolume] = useState(1)
  const [isPlaying, setIsPlaying] = useState(false)
  const [controlsVisible, setControlsVisible] = useState(true)
  const [isFullscreen, setIsFullscreen] = useState(false)

  const videoRef = useRef(null)
  const playerContainerRef = useRef(null)
  const selectedMovieRef = useRef(null)
  const controlsTimeoutRef = useRef(null)
  const playbackPositionRef = useRef(0)
  const previewTransitionTimeoutRef = useRef(null)
  const previewTransitioningRef = useRef(false)

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
    setIsFullPlayback(false)
    setKeepPlaybackStream(true)
  }, [])

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

  const startPlayback = useCallback(() => {
    setError('')
    if (!isFullPlayback) {
      setKeepPlaybackStream(true)
      setPlaybackStart(0)
      playbackPositionRef.current = 0
      setSelectedAudioTrack('')
      setSelectedSubtitleTrack('')
      setCurrentTime(0)
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
  }, [isFullPlayback, setError, showPlayerControls])

  useEffect(() => {
    if (!isFullPlayback || !selectedMovie) {
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
    const updateFullscreen = () => {
      setIsFullscreen(document.fullscreenElement === playerContainerRef.current)
    }
    document.addEventListener('fullscreenchange', updateFullscreen)
    return () => document.removeEventListener('fullscreenchange', updateFullscreen)
  }, [])

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
