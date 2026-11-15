import {
  Captions,
  Maximize,
  Pause,
  Play,
  Volume2,
  X,
} from 'lucide-react'
import { useRef, useState } from 'react'
import { formatTime, trackLabel } from '../utils/playback'

function TrackMenu({
  label,
  icon,
  value,
  options,
  onChange,
}) {
  const [open, setOpen] = useState(false)
  const triggerRef = useRef(null)

  const selectedOption = options.find((option) => option.value === String(value))
    ?? options[0]

  const close = () => {
    setOpen(false)
    window.requestAnimationFrame(() => triggerRef.current?.focus())
  }

  const openMenu = () => {
    setOpen(true)
    window.requestAnimationFrame(() => {
      const selected = document.querySelector(
        `[data-track-menu="${label}"][data-track-value="${String(value)}"]`
      )
      selected?.focus()
    })
  }

  return (
    <div
      className="relative flex items-center gap-2 text-xs"
      onKeyDown={(event) => {
        if ((event.key === 'Escape' || event.key === 'BrowserBack' || event.key === 'GoBack')
            && open) {
          event.preventDefault()
          event.stopPropagation()
          close()
        }
      }}
    >
      {icon}
      <span>{label}</span>
      <button
        ref={triggerRef}
        type="button"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label={`${label}: ${selectedOption.label}`}
        onClick={() => (open ? close() : openMenu())}
        className="max-w-36 rounded border border-white/20 bg-black/70 px-2 py-1.5 text-left text-white"
      >
        {selectedOption.label}
      </button>
      {open && (
        <div
          aria-label={`Choose ${label}`}
          className="absolute bottom-full right-0 z-30 mb-2 min-w-48 rounded-lg border border-white/20 bg-[#18181b] p-1 shadow-2xl"
          role="listbox"
        >
          {options.map((option) => (
            <button
              key={option.value}
              type="button"
              role="option"
              aria-selected={option.value === selectedOption.value}
              data-track-menu={label}
              data-track-value={option.value}
              onClick={() => {
                onChange(option.value)
                close()
              }}
              className={`block w-full rounded px-3 py-2 text-left text-xs ${
                option.value === selectedOption.value
                  ? 'bg-accent text-black'
                  : 'text-white hover:bg-white/10'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

// Renders the video element and its playback controls; playback state is supplied by Home.
function VideoPlayer({
  videoRef,
  playerContainerRef,
  playerControlsRef,
  selectedMovie,
  selectedMovieRef,
  playbackUrl,
  previewTransition,
  playbackStart,
  isFullPlayback,
  isPlaybackStream,
  isFullscreen,
  controlsVisible,
  showPlayerControls,
  setError,
  setDuration,
  setCurrentTime,
  setIsPlaying,
  advancePreview,
  currentTime,
  duration,
  isPlaying,
  seekPlayback,
  volume,
  setVolume,
  audioTracks,
  subtitleTracks,
  selectedAudioTrack,
  selectedSubtitleTrack,
  changePlaybackTrack,
  setSubtitleTrack,
  playbackPositionRef,
  onPlaybackEnded,
}) {
  return (
    <div
      ref={playerContainerRef}
      onMouseMove={showPlayerControls}
      onTouchStart={showPlayerControls}
      onFocusCapture={showPlayerControls}
      className={`relative aspect-video min-h-[17.5rem] overflow-hidden bg-black sm:min-h-[21.25rem] lg:min-h-0 ${
        !isFullPlayback
          ? '[clip-path:inset(0)] lg:aspect-auto lg:h-[min(52dvh,27.5vw)]'
          : ''
      } [&:fullscreen]:aspect-auto [&:fullscreen]:h-screen [&:fullscreen]:w-screen [&:fullscreen]:min-h-0 ${
        isFullscreen && !controlsVisible ? 'cursor-none' : ''
      }`}
    >
      <div className="absolute inset-0 bg-black">
        {selectedMovie.source === 'EXTERNAL' ? (
          selectedMovie.posterUrl && (
            <img
              src={selectedMovie.posterUrl}
              alt=""
              className="absolute inset-0 size-full object-contain"
            />
          )
        ) : <video
          key={playbackUrl}
          ref={videoRef}
          src={playbackUrl}
          autoPlay
          playsInline
          preload="metadata"
          onCanPlay={() => {
            const video = videoRef.current
            if (!video) {
              return
            }
            video.play().catch(() => {
              setError('The browser blocked automatic playback with audio.')
            })
          }}
          onLoadedMetadata={(event) => {
            const mediaDuration = event.currentTarget.duration
            if (Number.isFinite(mediaDuration) && mediaDuration > 0) {
              setDuration((currentDuration) => Math.max(
                currentDuration,
                playbackStart + mediaDuration
              ))
            }
          }}
          onTimeUpdate={(event) => {
            const position = playbackStart + event.currentTarget.currentTime
            playbackPositionRef.current = position
            setCurrentTime(position)
            if (!isPlaybackStream && event.currentTarget.currentTime >= 25) {
              advancePreview()
            }
          }}
          onPlay={() => {
            setIsPlaying(true)
          }}
          onPause={() => setIsPlaying(false)}
          onEnded={() => {
            if (isPlaybackStream) {
              onPlaybackEnded?.()
              return
            }
            if (!isFullPlayback
                && selectedMovieRef.current?.id === selectedMovie.id
                && (selectedMovie.source !== 'SERIES'
                  || selectedMovieRef.current?.episodeId === selectedMovie.episodeId)) {
              advancePreview()
            }
          }}
          onError={() => {
            setError('Playback failed. Check FFmpeg output and the browser console.')
          }}
          className={`absolute object-cover transition-opacity duration-[350ms] ${
            !isFullPlayback ? 'preview-video' : 'inset-0 size-full'
          } ${
            previewTransition === 'fading' ? 'opacity-0' : 'opacity-100'
          }`}
        />}
        {isFullPlayback && (
          <div className="pointer-events-none absolute inset-0 bg-gradient-to-t from-[#171719] via-black/10 to-black/20" />
        )}
      </div>
      {isFullPlayback && isFullscreen && (
        <>
          <div
            className={`pointer-events-none absolute inset-x-0 top-0 z-10 bg-gradient-to-b from-black/80 to-transparent px-6 pb-12 pt-6 transition-opacity duration-300 sm:px-10 sm:pt-10 ${
              controlsVisible ? 'opacity-100' : 'opacity-0'
            }`}
          >
            <h2 className="text-2xl font-bold drop-shadow sm:text-4xl">
              {selectedMovie.source === 'SERIES'
                ? `${selectedMovie.title} · S${String(selectedMovie.seasonNumber).padStart(2, '0')} E${String(selectedMovie.episodeNumber).padStart(2, '0')}`
                : selectedMovie.title}
            </h2>
          </div>
          <div
            ref={playerControlsRef}
            className={`absolute inset-x-0 bottom-0 z-20 bg-gradient-to-t from-black/90 via-black/60 to-transparent px-4 pb-5 pt-16 transition-opacity duration-300 sm:px-8 sm:pb-8 ${
              controlsVisible ? 'opacity-100' : 'pointer-events-none opacity-0'
            }`}
          >
            <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-3 text-white">
              <button
                type="button"
                tabIndex={controlsVisible ? 0 : -1}
                aria-label={isPlaying ? 'Pause' : 'Play'}
                onClick={() => {
                  if (videoRef.current?.paused) {
                    videoRef.current.play().catch(() => {
                      setError('Playback could not be started.')
                    })
                  } else {
                    videoRef.current?.pause()
                  }
                }}
                className="rounded-full p-2 transition hover:bg-white/15"
              >
                {isPlaying ? <Pause size={22} /> : <Play size={22} fill="currentColor" />}
              </button>
              <span className="min-w-[5.5rem] text-xs tabular-nums">
                {formatTime(currentTime)} / {formatTime(duration)}
              </span>
              <input
                tabIndex={controlsVisible ? 0 : -1}
                type="range"
                min="0"
                max={Math.max(duration, 1)}
                step="1"
                value={Math.min(currentTime, duration || currentTime)}
                aria-label="Playback position"
                onChange={(event) => setCurrentTime(Number(event.target.value))}
                onPointerUp={(event) => seekPlayback(Number(event.currentTarget.value))}
                onKeyUp={(event) => seekPlayback(Number(event.currentTarget.value))}
                className="min-w-24 flex-1 accent-accent"
              />
              <label className="flex items-center gap-2" aria-label="Volume">
                <Volume2 size={18} />
                <input
                  tabIndex={controlsVisible ? 0 : -1}
                  type="range"
                  min="0"
                  max="1"
                  step="0.05"
                  value={volume}
                  onChange={(event) => setVolume(Number(event.target.value))}
                  className="w-20 accent-accent"
                />
              </label>
              <TrackMenu
                label="Audio"
                value={selectedAudioTrack}
                options={[
                  { value: '', label: 'Automatisch' },
                  ...audioTracks.map((track, index) => ({
                    value: String(track.index),
                    label: `${trackLabel(track, index)}${track.defaultTrack ? ' · Standard' : ''}`,
                  })),
                ]}
                onChange={changePlaybackTrack}
              />
              <TrackMenu
                label="Subtitles"
                icon={<Captions size={18} />}
                value={selectedSubtitleTrack}
                options={[
                  { value: '', label: 'Subtitles off' },
                  ...subtitleTracks.map((track, index) => ({
                    value: String(track.index),
                    label: `${trackLabel(track, index)}${track.defaultTrack ? ' · Standard' : ''}`,
                  })),
                ]}
                onChange={setSubtitleTrack}
              />
              <button
                type="button"
                tabIndex={controlsVisible ? 0 : -1}
                aria-label="Exit fullscreen"
                onClick={() => {
                  if (isFullscreen) {
                    document.exitFullscreen().catch(() => {
                      setError('Could not exit fullscreen.')
                    })
                  } else {
                    playerContainerRef.current?.requestFullscreen().catch(() => {
                      setError('Could not enter fullscreen.')
                    })
                  }
                }}
                className="rounded-full p-2 transition hover:bg-white/15"
              >
                {isFullscreen ? <X size={20} /> : <Maximize size={20} />}
              </button>
            </div>
          </div>
        </>
      )}
      {!isFullscreen && (
        <div className="absolute inset-x-0 bottom-0 flex items-end justify-start gap-4 p-5 text-left sm:p-7">
          <h2 className="min-w-0 text-[1.35rem] font-bold leading-tight drop-shadow sm:text-[1.6875rem]">
            {selectedMovie.source === 'SERIES'
              ? `${selectedMovie.title} · S${String(selectedMovie.seasonNumber).padStart(2, '0')} E${String(selectedMovie.episodeNumber).padStart(2, '0')}`
              : selectedMovie.title}
          </h2>
        </div>
      )}
    </div>
  )
}

export default VideoPlayer
