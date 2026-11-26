import { useId, useLayoutEffect, useRef, useState } from 'react'
import { Play, RotateCcw, Star } from 'lucide-react'
import AwardWins from './AwardWins'
import { getPeople, normalizePersonName } from '../utils/catalog'

function PlotText({ text }) {
  const plotRef = useRef(null)
  const [visibleText, setVisibleText] = useState(text)

  useLayoutEffect(() => {
    const plot = plotRef.current
    if (!plot || !text) return undefined

    const updateVisibleText = () => {
      const width = plot.getBoundingClientRect().width
      if (!width) return

      const measure = plot.cloneNode(false)
      measure.classList.remove('line-clamp-4')
      Object.assign(measure.style, {
        position: 'fixed',
        left: '-10000px',
        top: '0',
        visibility: 'hidden',
        display: 'block',
        width: `${width}px`,
        height: 'auto',
        maxHeight: 'none',
        overflow: 'visible',
        webkitLineClamp: 'unset',
        webkitBoxOrient: 'unset',
      })
      document.body.append(measure)

      const lineHeight = Number.parseFloat(getComputedStyle(plot).lineHeight)
      const maxHeight = lineHeight * 4
      const fits = (value) => {
        measure.textContent = value
        return measure.getBoundingClientRect().height <= maxHeight + 1
      }

      if (fits(text)) {
        setVisibleText(text)
        measure.remove()
        return
      }

      const words = text.trim().split(/\s+/)
      let low = 0
      let high = words.length
      while (low < high) {
        const middle = Math.ceil((low + high) / 2)
        if (fits(`${words.slice(0, middle).join(' ')}…`)) low = middle
        else high = middle - 1
      }

      setVisibleText(`${words.slice(0, low).join(' ')}…`)
      measure.remove()
    }

    updateVisibleText()
    const observer = new ResizeObserver(updateVisibleText)
    observer.observe(plot)
    return () => observer.disconnect()
  }, [text])

  return (
    <p ref={plotRef} className="text-sm leading-relaxed text-white/80">
      <span className="md:hidden">{text}</span>
      <span className="hidden md:inline">{visibleText}</span>
    </p>
  )
}

// Displays the selected title's metadata and keeps episode/person actions with that view.
function MovieDetails({
  selectedMovie,
  inPlaylist,
  onTogglePlaylist,
  openEpisode,
  personMovieCounts,
  selectedPerson,
  setSelectedPerson,
  setSelectedGenre,
  selectedRated,
  setSelectedRated,
  ratedMovieCounts,
  onStartPlayback,
  onRestartPlayback,
  resumeProgress,
}) {
  const remainingTimeId = useId()
  const canResume = Boolean(resumeProgress)
  const playlistAction = inPlaylist ? 'Remove from playlist' : 'Add to playlist'
  const renderPeople = (value, role) => getPeople({ [role]: value }, role)
    .map((name) => {
      const normalizedName = normalizePersonName(name)
      const personKey = `${role}:${normalizedName}`
      const repeated = (personMovieCounts.get(personKey) ?? 0) > 1
      return repeated ? (
        <button
          key={name}
          type="button"
          data-person-role={role}
          onClick={() => {
            setSelectedGenre('')
            setSelectedRated('')
            setSelectedPerson({ name: name.normalize('NFC'), role })
          }}
          className={`inline-flex items-center whitespace-nowrap rounded-full border px-2.5 leading-5 text-left transition ${
            selectedPerson?.role === role
              && normalizePersonName(selectedPerson.name) === normalizedName
              ? 'border-accent bg-accent text-black'
              : 'border-white/10 bg-white/[0.04] text-white/85 hover:border-white/25 hover:text-white'
          }`}
        >
          {name}
        </button>
      ) : (
        <span key={name} className="whitespace-nowrap px-1 leading-5 text-white/85">{name}</span>
      )
    })

  return (
    <>
      <div className="mx-auto max-w-md px-4 pb-2 pt-6 md:mx-0 md:max-w-none md:pr-7 lg:pl-5 lg:pr-4">
        <div className="flex flex-col gap-5">
          <div className="flex flex-col items-center gap-x-6 gap-y-3 text-center text-sm text-white/75 md:flex-row md:flex-wrap md:text-left">
            <div className={`flex max-w-full justify-center gap-2 md:-ml-1 md:shrink-0 md:justify-start md:gap-3 ${
              canResume ? 'items-start' : 'items-center'
            }`}>
              <div className="w-24 shrink-0 md:w-32">
                <button
                  type="button"
                  data-navigation-role="playback-action"
                  data-primary-play="true"
                  aria-describedby={canResume ? remainingTimeId : undefined}
                  onClick={onStartPlayback}
                  className="relative inline-flex w-full items-center justify-center gap-2 whitespace-nowrap rounded-md border border-accent bg-accent px-2 py-2.5 text-sm font-semibold text-black transition hover:border-accent-hover hover:bg-accent-hover md:px-6"
                >
                  <Play size={14} fill="currentColor" />
                  {canResume ? 'Resume' : 'Play'}
                  {resumeProgress && (
                    <span aria-hidden="true" className="absolute inset-x-1 bottom-0.5 h-0.5 rounded-full bg-black/20">
                      <span
                        className="block h-full rounded-full bg-black/75"
                        style={{ width: `${resumeProgress.percent}%` }}
                      />
                    </span>
                  )}
                </button>
                {resumeProgress && (
                  <p id={remainingTimeId} className="mt-1 text-xs tabular-nums text-white/70">
                    {resumeProgress.remainingMinutes} min left
                  </p>
                )}
              </div>
              <button
                type="button"
                data-navigation-role="playback-action"
                aria-label="Restart from beginning"
                title="Restart from beginning"
                aria-hidden={!canResume}
                tabIndex={canResume ? 0 : -1}
                onClick={onRestartPlayback}
                className={`size-10 shrink-0 items-center justify-center rounded-md border border-white/20 text-white transition hover:border-white/40 hover:bg-white/10 ${
                  canResume ? 'inline-flex' : 'hidden md:pointer-events-none md:invisible md:inline-flex'
                }`}
              >
                <RotateCcw size={16} />
              </button>
              {selectedMovie.source !== 'EXTERNAL' && (
                <button
                  type="button"
                  onClick={onTogglePlaylist}
                  className={`inline-flex min-h-11 items-center justify-center rounded-md border px-2 text-center text-xs leading-tight transition md:hidden ${
                    inPlaylist
                      ? 'border-accent text-accent'
                      : 'border-white/20 text-white hover:border-white/40 hover:bg-white/10'
                  }`}
                >
                  {playlistAction}
                </button>
              )}
            </div>
            <div className="flex min-w-0 flex-col items-center gap-y-1 text-sm text-white/75 md:items-start">
              {(selectedMovie.imdbRating != null || selectedMovie.year
                || selectedMovie.oscarWins || selectedMovie.primetimeEmmyWins) && (
                <div className="flex flex-wrap items-center justify-center gap-x-4 md:justify-start">
                  {selectedMovie.imdbRating != null && (
                    <span className="inline-flex items-center gap-1">
                      <Star size={12} fill="currentColor" />
                      {selectedMovie.imdbRating.toFixed(1)}
                    </span>
                  )}
                  {selectedMovie.year && <span>{selectedMovie.year}</span>}
                  <AwardWins content={selectedMovie} />
                </div>
              )}
              {(selectedMovie.genre || selectedMovie.runtime || selectedMovie.rated) && (
                <div className="flex flex-wrap items-center justify-center gap-x-3 gap-y-1 md:justify-start">
                  {selectedMovie.genre && <span>{selectedMovie.genre}</span>}
                  {selectedMovie.runtime && <span>{selectedMovie.runtime}</span>}
                  {selectedMovie.rated && (selectedMovie.source === 'SERIES' ? (
                    <span>{selectedMovie.rated}</span>
                  ) : (() => {
                      const rated = selectedMovie.rated.trim()
                      const ratedKey = rated.normalize('NFC').toLocaleLowerCase('de')
                      const isFilterable = (ratedMovieCounts.get(ratedKey) ?? 0) > 1
                      const isSelected = selectedRated === ratedKey
                      return isFilterable ? (
                        <button
                          type="button"
                          data-navigation-role="rated-filter"
                          aria-pressed={isSelected}
                          onClick={() => {
                            setSelectedGenre('')
                            setSelectedPerson('')
                            setSelectedRated(isSelected ? '' : ratedKey)
                          }}
                          className={`inline-flex items-center rounded-full border px-4 py-0.5 transition ${
                            isSelected
                              ? 'border-accent bg-accent text-black'
                              : 'border-white/20 bg-white/[0.04] hover:border-white/25 hover:bg-white/10 hover:text-white'
                          }`}
                        >
                          {selectedMovie.rated}
                        </button>
                      ) : (
                        <span>{selectedMovie.rated}</span>
                      )
                    })())}
                </div>
              )}
            </div>
          </div>
          {selectedMovie.source === 'SERIES' && (
            <nav
              aria-label="Choose episode"
              className="flex flex-wrap content-start justify-center gap-2 md:order-first md:max-h-16 md:justify-start md:overflow-y-auto"
            >
              {selectedMovie.episodes.map((episode) => (
                <button
                  key={episode.id}
                  type="button"
                  aria-label={`Episode ${episode.episodeNumber}`}
                  aria-pressed={selectedMovie.episodeId === episode.id}
                  onClick={() => openEpisode(episode)}
                  className={`shrink-0 rounded-full border min-h-10 px-3 text-xs md:min-h-0 md:px-2.5 md:py-0.5 font-medium transition ${
                    selectedMovie.episodeId === episode.id
                      ? 'border-accent bg-accent text-black'
                      : 'border-white/10 bg-white/[0.04] text-white/65 hover:border-white/25 hover:text-white'
                  }`}
                >
                  {String(episode.episodeNumber).padStart(2, '0')}
                </button>
              ))}
            </nav>
          )}
          <div className={`grid ${
            selectedMovie.director || selectedMovie.actors || selectedMovie.writer
              ? 'gap-5 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]'
              : 'grid-cols-1'
          }`}>
            <section>
              <PlotText
                text={selectedMovie.plot || (selectedMovie.source === 'SERIES'
                  ? 'No further information is available for this series.'
                  : 'No further information is available for this movie.')}
              />
              {selectedMovie.writer && selectedMovie.source !== 'SERIES' && (
                <div className="mt-3 text-sm">
                  <h3 className="mb-1 text-xs uppercase tracking-wider text-white/65">Writer</h3>
                  <div className="flex min-w-0 flex-1 flex-wrap items-center gap-x-1.5 gap-y-1">
                    {renderPeople(selectedMovie.writer, 'writer')}
                  </div>
                </div>
              )}
            </section>

            {(selectedMovie.director || selectedMovie.actors
              || (selectedMovie.source === 'SERIES' && selectedMovie.writer)) && (
              <section className="flex flex-col gap-2 text-sm lg:border-l lg:border-white/10 lg:pl-4">
                {selectedMovie.director && (
                  <div>
                    <h3 className="mb-1 text-xs uppercase tracking-wider text-white/65">Director</h3>
                    <div className="flex min-w-0 flex-1 flex-wrap items-center gap-x-1.5 gap-y-1">
                      {renderPeople(selectedMovie.director, 'director')}
                    </div>
                  </div>
                )}
                {selectedMovie.actors && (
                  <div>
                    <h3 className="mb-1 text-xs uppercase tracking-wider text-white/65">Cast</h3>
                    <div className="flex min-w-0 flex-1 flex-wrap items-center gap-x-1.5 gap-y-1">
                      {renderPeople(selectedMovie.actors, 'actors')}
                    </div>
                  </div>
                )}
                {selectedMovie.source === 'SERIES' && selectedMovie.writer && (
                  <div>
                    <h3 className="mb-1 text-xs uppercase tracking-wider text-white/65">Writer</h3>
                    <div className="flex min-w-0 flex-1 flex-wrap items-center gap-x-1.5 gap-y-1">
                      {renderPeople(selectedMovie.writer, 'writer')}
                    </div>
                  </div>
                )}
              </section>
            )}
          </div>
        </div>
      </div>
    </>
  )
}

export default MovieDetails
