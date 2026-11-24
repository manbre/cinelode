import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import CatalogPanel from '../components/CatalogPanel'
import MovieDetails from '../components/MovieDetails'
import VideoPlayer from '../components/VideoPlayer'
import { useCatalog } from '../hooks/useCatalog'
import { usePlayback } from '../hooks/usePlayback'
import { useTvNavigation } from '../hooks/useTvNavigation'
import { useCinelodeStore } from '../store'
import {
  contentKey,
  getInitialLibrarySelection,
  getNextPlaybackSelection,
} from '../utils/catalog'
import { openExternalMovie } from '../utils/externalPlayback'
const MOVIES_API = '/api/movies'
function notifyNativeHistory(canGoBack) {
  window.ReactNativeWebView?.postMessage(
    JSON.stringify({
      type: 'cinelode-history',
      canGoBack,
    }),
  )
}
function setCatalogHistoryView(view) {
  const state = { ...window.history.state, cinelodeView: view }
  if (window.history.state?.cinelodeView) {
    window.history.replaceState(state, '', window.location.href)
  } else {
    window.history.pushState(state, '', window.location.href)
  }
  notifyNativeHistory(true)
}
function clearCatalogHistoryView() {
  const state = { ...window.history.state }
  delete state.cinelodeView
  window.history.replaceState(state, '', window.location.href)
  notifyNativeHistory(false)
}
function notifyNativePlayerState(isFullscreen) {
  window.ReactNativeWebView?.postMessage(
    JSON.stringify({
      type: 'cinelode-player-state',
      isFullscreen,
    }),
  )
}
// Coordinates catalog selection and playback state across the focused child components.
function Home({ onLogout }) {
  const movies = useCinelodeStore((state) => state.movies)
  const seriesSeasons = useCinelodeStore((state) => state.seriesSeasons)
  const isSeriesMode = useCinelodeStore((state) => state.isSeriesMode)
  const selectedMovie = useCinelodeStore((state) => state.selectedMovie)
  const search = useCinelodeStore((state) => state.search)
  const selectedDuration = useCinelodeStore((state) => state.selectedDuration)
  const selectedGenre = useCinelodeStore((state) => state.selectedGenre)
  const selectedPerson = useCinelodeStore((state) => state.selectedPerson)
  const selectedRated = useCinelodeStore((state) => state.selectedRated)
  const loading = useCinelodeStore((state) => state.loading)
  const error = useCinelodeStore((state) => state.error)
  const setMovies = useCinelodeStore((state) => state.setMovies)
  const setSeriesSeasons = useCinelodeStore((state) => state.setSeriesSeasons)
  const setIsSeriesMode = useCinelodeStore((state) => state.setIsSeriesMode)
  const setSelectedMovie = useCinelodeStore((state) => state.setSelectedMovie)
  const setSearch = useCinelodeStore((state) => state.setSearch)
  const setSelectedDuration = useCinelodeStore(
    (state) => state.setSelectedDuration,
  )
  const setSelectedGenre = useCinelodeStore((state) => state.setSelectedGenre)
  const setSelectedPerson = useCinelodeStore((state) => state.setSelectedPerson)
  const setSelectedRated = useCinelodeStore((state) => state.setSelectedRated)
  const setLoading = useCinelodeStore((state) => state.setLoading)
  const setError = useCinelodeStore((state) => state.setError)
  const [logoutError, setLogoutError] = useState('')
  const showExitConfirmation = useCinelodeStore(
    (state) => state.showExitConfirmation,
  )
  const setShowExitConfirmation = useCinelodeStore(
    (state) => state.setShowExitConfirmation,
  )
  const playlist = useCinelodeStore((state) => state.playlist)
  const togglePlaylistItem = useCinelodeStore(
    (state) => state.togglePlaylistItem,
  )
  const clearPlaylist = useCinelodeStore((state) => state.clearPlaylist)
  const exitCancelRef = useRef(null)
  const playerControlsRef = useRef(null)
  const selectionActivatedRef = useRef(false)
  const {
    audioTracks,
    changePlaybackTrack,
    canResume,
    clearSelection,
    controlsVisible,
    currentTime,
    duration,
    isFullPlayback,
    isPlaybackStream,
    isFullscreen,
    isPlaying,
    playbackPositionRef,
    playbackStart,
    playbackUrl,
    playerContainerRef,
    previewTransition,
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
    setSubtitleTrack,
    setVolume,
    showPlayerControls,
    startPlayback,
    subtitleTracks,
    videoRef,
    volume,
  } = usePlayback({ selectedMovie, setError })
  const firstLibraryMovie = useMemo(
    () => getInitialLibrarySelection(movies),
    [movies],
  )
  const resetToInitialView = useCallback(() => {
    setIsSeriesMode(false)
    setSearch('')
    setSelectedDuration('')
    setSelectedGenre('')
    setSelectedPerson('')
    setSelectedRated('')
    if (firstLibraryMovie) {
      if (!selectedMovie || contentKey(selectedMovie) !== contentKey(firstLibraryMovie)) {
        selectContent(firstLibraryMovie)
        setSelectedMovie(firstLibraryMovie)
      }
    } else {
      clearSelection()
      setSelectedMovie(null)
    }
  }, [
    clearSelection,
    firstLibraryMovie,
    selectedMovie,
    selectContent,
    setIsSeriesMode,
    setSearch,
    setSelectedDuration,
    setSelectedGenre,
    setSelectedMovie,
    setSelectedPerson,
    setSelectedRated,
  ])
  const playlistPositions = new Map(
    playlist.map((movie, index) => [contentKey(movie), index + 1]),
  )
  const handleBack = useCallback(() => {
    if (showExitConfirmation) {
      setShowExitConfirmation(false)
      return
    }
    if (isFullscreen || isFullPlayback) {
      if (isFullscreen) {
        document.exitFullscreen().catch(() => {
          setError('Could not exit fullscreen.')
        })
      }
      returnToPreview()
      return
    }
    const isInitialView = !isSeriesMode
      && firstLibraryMovie
      && selectedMovie
      && contentKey(selectedMovie) === contentKey(firstLibraryMovie)
      && !search
      && !selectedDuration
      && !selectedGenre
      && !selectedPerson
      && !selectedRated
    if (!isInitialView) {
      resetToInitialView()
      clearCatalogHistoryView()
      return
    }
    setShowExitConfirmation(true)
  }, [
    firstLibraryMovie,
    isFullPlayback,
    isFullscreen,
    isSeriesMode,
    resetToInitialView,
    returnToPreview,
    search,
    selectedDuration,
    selectedGenre,
    selectedMovie,
    selectedPerson,
    selectedRated,
    setError,
    setShowExitConfirmation,
    showExitConfirmation,
  ])
  const handlePlayPause = useCallback(() => {
    const video = videoRef.current
    if (!video) {
      return
    }
    if (video.paused) {
      video.play().catch(() => {
        setError('Playback could not be started.')
      })
    } else {
      video.pause()
    }
  }, [setError, videoRef])
  const handleSeek = useCallback(
    (offset) => {
      if (!isFullPlayback || !videoRef.current) {
        return
      }
      showPlayerControls()
      const nextPosition = Math.min(
        Math.max(playbackPositionRef.current + offset, 0),
        duration || Number.MAX_SAFE_INTEGER,
      )
      seekPlayback(nextPosition)
    },
    [
      duration,
      isFullPlayback,
      playbackPositionRef,
      seekPlayback,
      showPlayerControls,
      videoRef,
    ],
  )
  const handleDirection = useCallback(
    (direction) => {
      if (!isFullPlayback || !isFullscreen) {
        return false
      }
      const active = document.activeElement
      if (active instanceof HTMLElement && active.closest('[role="listbox"]')) {
        return false
      }
      if (!controlsVisible) {
        showPlayerControls()
        window.requestAnimationFrame(() => {
          playerControlsRef.current?.querySelector('button, input')?.focus()
        })
        return true
      }
      if (direction !== 'left' && direction !== 'right') {
        return false
      }
      const controls = [
        ...(playerControlsRef.current?.querySelectorAll(
          'button:not([disabled]):not([tabindex="-1"]), input:not([disabled]):not([tabindex="-1"])',
        ) ?? []),
      ]
      const currentIndex = controls.indexOf(active)
      if (currentIndex < 0) {
        return false
      }
      const nextIndex =
        direction === 'right' ? currentIndex + 1 : currentIndex - 1
      const next = controls[nextIndex]
      if (!(next instanceof HTMLElement)) {
        return false
      }
      next.focus()
      next.scrollIntoView({ block: 'nearest', inline: 'nearest' })
      return true
    },
    [controlsVisible, isFullPlayback, isFullscreen, showPlayerControls],
  )
  const { restoreFocus } = useTvNavigation({
    onBack: handleBack,
    onDirection: handleDirection,
    onPlayPause: handlePlayPause,
    onSeek: handleSeek,
  })
  useEffect(() => {
    if (showExitConfirmation) {
      window.requestAnimationFrame(() => exitCancelRef.current?.focus())
    }
  }, [showExitConfirmation])
  useEffect(() => {
    if (!selectedMovie) {
      window.requestAnimationFrame(restoreFocus)
    }
  }, [restoreFocus, selectedMovie])
  useEffect(() => {
    notifyNativeHistory(Boolean(window.history.state?.cinelodeView))
  }, [])
  useEffect(() => {
    notifyNativePlayerState(isFullPlayback)
  }, [isFullPlayback])
  useEffect(() => {
    const handleHistoryBack = () => {
      if (isFullscreen || isFullPlayback) {
        notifyNativeHistory(Boolean(window.history.state?.cinelodeView))
        if (isFullscreen) {
          document.exitFullscreen().catch(() => {
            setError('Could not exit fullscreen.')
          })
        }
        returnToPreview()
        return
      }
      resetToInitialView()
      clearCatalogHistoryView()
    }
    window.addEventListener('popstate', handleHistoryBack)
    return () => window.removeEventListener('popstate', handleHistoryBack)
  }, [
    isFullPlayback,
    isFullscreen,
    resetToInitialView,
    returnToPreview,
    setError,
  ])
  useEffect(() => {
    const controller = new AbortController()
    async function loadCatalogs() {
      try {
        const [moviesResponse, seriesResponse] = await Promise.all([
          fetch(MOVIES_API, { signal: controller.signal }),
          fetch('/api/series', { signal: controller.signal }),
        ])
        if (!moviesResponse.ok) {
          throw new Error('Movie library could not be loaded.')
        }
        if (!seriesResponse.ok) {
          throw new Error('Series library could not be loaded.')
        }
        const [movieData, seriesData] = await Promise.all([
          moviesResponse.json(),
          seriesResponse.json(),
        ])
        const loadedMovies = Array.isArray(movieData) ? movieData : []
        setMovies(loadedMovies)
        setSeriesSeasons(
          Array.isArray(seriesData)
            ? seriesData.map((season) => ({ ...season, source: 'SERIES' }))
            : [],
        )
        const firstLocalMovie = getInitialLibrarySelection(loadedMovies)
        if (firstLocalMovie) {
          selectContent(firstLocalMovie)
          setSelectedMovie(firstLocalMovie)
        }
      } catch (cause) {
        if (cause.name !== 'AbortError') {
          setError(cause.message)
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false)
        }
      }
    }
    loadCatalogs()
    return () => {
      controller.abort()
    }
  }, [
    selectContent,
    setError,
    setLoading,
    setMovies,
    setSelectedMovie,
    setSeriesSeasons,
  ])
  const {
    localMovies,
    availableGenres,
    personMovieCounts,
    ratedMovieCounts,
    filteredMovies,
  } = useCatalog({
    movies,
    seriesSeasons,
    isSeriesMode,
    search,
    selectedDuration,
    selectedGenre,
    selectedPerson,
    selectedRated,
  })
  const openMovie = useCallback(
    (movie) => {
      if (
        selectedMovieRef.current?.id === movie.id &&
        selectedMovieRef.current?.source === movie.source
      ) {
        if (movie.source !== 'EXTERNAL') {
          resumePreview()
        }
        return
      }
      selectContent(movie)
      setSelectedMovie(movie)
      selectionActivatedRef.current = true
      setCatalogHistoryView('movie')
    },
    [resumePreview, selectContent, selectedMovieRef, setSelectedMovie],
  )
  const openSeason = useCallback(
    (season) => {
      const firstEpisode =
        season.episodes.find((episode) => episode.episodeNumber === 1) ??
        season.episodes[0]
      if (!firstEpisode) {
        setError(`No episodes found for season ${season.seasonNumber}.`)
        return
      }
      if (
        selectedMovieRef.current?.source === 'SERIES' &&
        selectedMovieRef.current.id === season.id
      ) {
        resumePreview()
        return
      }
      const selection = {
        ...season,
        source: 'SERIES',
        episodeId: firstEpisode.id,
        episodeNumber: firstEpisode.episodeNumber,
      }
      selectContent(selection)
      setSelectedMovie(selection)
      selectionActivatedRef.current = true
      setCatalogHistoryView('season')
    },
    [
      resumePreview,
      selectContent,
      selectedMovieRef,
      setError,
      setSelectedMovie,
    ],
  )
  const handleCatalogSelection = useCallback(
    (movie) => {
      const isSelected =
        selectedMovie && contentKey(selectedMovie) === contentKey(movie)
      if (!isSelected) {
        if (movie.source === 'SERIES') {
          openSeason(movie)
        } else {
          openMovie(movie)
        }
        return
      }
      if (!selectionActivatedRef.current) {
        selectionActivatedRef.current = true
        return
      }
      togglePlaylistItem(selectedMovie)
    },
    [openMovie, openSeason, selectedMovie, togglePlaylistItem],
  )
  const handleStartPlayback = useCallback(() => {
    if (selectedMovie?.source !== 'EXTERNAL') {
      startPlayback()
      return
    }
    setError('')
    openExternalMovie(selectedMovie, {
      fetchUrl: window.fetch.bind(window),
      openWindow: () => {
        const popup = window.open('', '_blank')
        if (popup) {
          popup.opener = null
        }
        return popup
      },
      nativeBridge: window.ReactNativeWebView,
    }).catch((cause) =>
      setError(cause.message || 'The external movie could not be opened.'),
    )
  }, [selectedMovie, setError, startPlayback])
  const handleRestartPlayback = useCallback(() => {
    if (selectedMovie?.source !== 'EXTERNAL') {
      startPlayback(true)
    }
  }, [selectedMovie, startPlayback])
  useEffect(() => {
    const onExternalOpenError = () =>
      setError('The external link could not be opened on this device.')
    window.addEventListener('cinelode-external-open-error', onExternalOpenError)
    return () =>
      window.removeEventListener(
        'cinelode-external-open-error',
        onExternalOpenError,
      )
  }, [setError])
  const handlePlaybackEnded = useCallback(() => {
    if (!isFullPlayback || !selectedMovie) {
      return
    }
    const nextMovie = getNextPlaybackSelection({
      selectedContent: selectedMovie,
      playlist,
      localMovies,
      seriesSeasons,
    })
    if (!nextMovie) {
      if (playlist.length > 0) {
        clearPlaylist()
      }
      returnToPreview()
      return
    }
    selectContent(nextMovie)
    setSelectedMovie(nextMovie)
    if (nextMovie.source !== 'EXTERNAL') {
      setIsFullPlayback(true)
    } else if (document.fullscreenElement) {
      document
        .exitFullscreen()
        .catch(() => setError('Could not exit fullscreen.'))
    }
  }, [
    isFullPlayback,
    clearPlaylist,
    playlist,
    localMovies,
    returnToPreview,
    selectedMovie,
    selectContent,
    seriesSeasons,
    setIsFullPlayback,
    setSelectedMovie,
    setError,
  ])
  const openEpisode = useCallback(
    (episode) => {
      if (
        !selectedMovie ||
        selectedMovie.source !== 'SERIES' ||
        selectedMovie.episodeId === episode.id
      ) {
        return
      }
      const selection = {
        ...selectedMovie,
        episodeId: episode.id,
        episodeNumber: episode.episodeNumber,
      }
      selectContent(selection)
      setSelectedMovie(selection)
    },
    [selectContent, selectedMovie, setSelectedMovie],
  )
  const changeCatalogMode = (showSeries) => {
    setIsSeriesMode(showSeries)
    setSearch('')
    setSelectedDuration('')
    setSelectedGenre('')
    setSelectedPerson('')
    setSelectedRated('')
    const firstCard = showSeries ? seriesSeasons[0] : localMovies[0]
    if (firstCard) {
      if (showSeries) {
        openSeason(firstCard)
      } else {
        openMovie(firstCard)
      }
    }
  }
  const handleLogout = async () => {
    setLogoutError('')
    try {
      await onLogout()
    } catch (cause) {
      setLogoutError(cause.message || 'Abmeldung fehlgeschlagen.')
    }
  }
  return (
    <main className="layout-frame relative isolate flex h-dvh min-h-0 flex-col overflow-hidden bg-[#101011]/55 text-text-primary lg:grid lg:grid-rows-[minmax(0,1fr)]">
      {selectedMovie?.posterUrl && (
        <>
          <img
            src={selectedMovie.posterUrl}
            alt=""
            aria-hidden="true"
            className="pointer-events-none absolute -inset-[10%] z-0 size-[120%] object-cover opacity-45 blur-[45px] scale-110"
          />
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-0 z-[1] bg-[#101011]/35"
          />
        </>
      )}
      <CatalogPanel
        onLogout={onLogout ? handleLogout : undefined}
        loading={loading}
        error={error}
        availableGenres={availableGenres}
        selectedGenre={selectedGenre}
        setSelectedGenre={setSelectedGenre}
        setSelectedPerson={setSelectedPerson}
        setSelectedRated={setSelectedRated}
        selectedDuration={selectedDuration}
        setSelectedDuration={setSelectedDuration}
        isSeriesMode={isSeriesMode}
        changeCatalogMode={changeCatalogMode}
        search={search}
        setSearch={setSearch}
        filteredMovies={filteredMovies}
        playlistPositions={playlistPositions}
        selectedMovie={selectedMovie}
        openMovie={handleCatalogSelection}
        openSeason={handleCatalogSelection}
      />
      {logoutError && (
        <p
          role="alert"
          className="fixed bottom-4 right-4 z-50 rounded-lg bg-red-950 px-4 py-3 text-sm text-red-200"
        >
          {logoutError}
        </p>
      )}
      <aside
        className={`hide-scrollbar relative z-10 min-h-0 min-w-0 bg-[#111112]/50 ${
          selectedMovie
            ? `order-1 block h-[42dvh] shrink-0 overflow-y-auto lg:order-2 lg:h-full lg:min-h-0 lg:shrink-0 ${
                !isFullPlayback
                  ? 'details-panel'
                  : 'lg:overflow-y-auto'
              }`
            : 'hidden lg:block'
        }`}
      >
        {selectedMovie ? (
          <>
            <VideoPlayer
              videoRef={videoRef}
              playerContainerRef={playerContainerRef}
              playerControlsRef={playerControlsRef}
              selectedMovie={selectedMovie}
              selectedMovieRef={selectedMovieRef}
              playbackUrl={playbackUrl}
              onSeek={handleSeek}
              previewTransition={previewTransition}
              advancePreview={advancePreview}
              playbackStart={playbackStart}
              isFullPlayback={isFullPlayback}
              isPlaybackStream={isPlaybackStream}
              isFullscreen={isFullscreen}
              controlsVisible={controlsVisible}
              showPlayerControls={showPlayerControls}
              setError={setError}
              setDuration={setDuration}
              setCurrentTime={setCurrentTime}
              setIsPlaying={setIsPlaying}
              currentTime={currentTime}
              duration={duration}
              isPlaying={isPlaying}
              seekPlayback={seekPlayback}
              volume={volume}
              setVolume={setVolume}
              audioTracks={audioTracks}
              subtitleTracks={subtitleTracks}
              selectedAudioTrack={selectedAudioTrack}
              selectedSubtitleTrack={selectedSubtitleTrack}
              changePlaybackTrack={changePlaybackTrack}
              setSubtitleTrack={setSubtitleTrack}
              playbackPositionRef={playbackPositionRef}
              onPlaybackEnded={handlePlaybackEnded}
              onRestartPlayback={handleRestartPlayback}
            />
            <div className="movie-info min-h-0 lg:overflow-y-auto">
              <MovieDetails
                selectedMovie={selectedMovie}
                openEpisode={openEpisode}
                personMovieCounts={personMovieCounts}
                selectedPerson={selectedPerson}
                setSelectedPerson={setSelectedPerson}
                setSelectedGenre={setSelectedGenre}
                selectedRated={selectedRated}
                setSelectedRated={setSelectedRated}
                ratedMovieCounts={ratedMovieCounts}
                onStartPlayback={handleStartPlayback}
                onRestartPlayback={handleRestartPlayback}
                canResume={canResume}
              />
            </div>
          </>
        ) : (
          <div className="flex h-full flex-col items-center justify-center gap-3 p-8 text-center text-white/35">
            <p className="text-sm">
              {isSeriesMode
                ? 'Select a season to see episodes and a preview.'
                : 'Select a movie to see details and a preview.'}
            </p>
          </div>
        )}
      </aside>
      {showExitConfirmation && (
        <div
          aria-labelledby="exit-dialog-title"
          aria-modal="true"
          className="fixed inset-0 z-[60] flex items-center justify-center bg-black/75 p-8"
          role="dialog"
        >
          <div className="w-full max-w-lg rounded-2xl border border-white/15 bg-[#18181b] p-8 text-center shadow-2xl">
            <h2 id="exit-dialog-title" className="text-2xl font-semibold">
              Close application?
            </h2>
            <p className="mt-3 text-base text-white/65">
              Do you really want to close Cinelode?
            </p>
            <div className="mt-8 flex justify-center gap-4">
              <button
                ref={exitCancelRef}
                type="button"
                onClick={() => setShowExitConfirmation(false)}
                className="min-h-12 rounded-lg border border-white/20 px-6 py-3 font-semibold text-white"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => {
                  window.ReactNativeWebView?.postMessage(
                    JSON.stringify({
                      type: 'cinelode-exit-confirmed',
                    }),
                  )
                }}
                className="min-h-12 rounded-lg bg-accent px-6 py-3 font-semibold text-black"
              >
                Close application
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  )
}
export default Home
