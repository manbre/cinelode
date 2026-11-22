import { Play, Star } from 'lucide-react'
import AwardWins from './AwardWins'
import { getPeople, normalizePersonName } from '../utils/catalog'

// Displays the selected title's metadata and keeps episode/person actions with that view.
function MovieDetails({
  selectedMovie,
  openEpisode,
  personMovieCounts,
  selectedPerson,
  setSelectedPerson,
  setSelectedGenre,
  selectedRated,
  setSelectedRated,
  ratedMovieCounts,
  onStartPlayback,
}) {
  const renderPeople = (value, role) => getPeople({ [role]: value }, role)
    .map((name) => {
      const normalizedName = normalizePersonName(name)
      const personKey = `${role}:${normalizedName}`
      const repeated = (personMovieCounts.get(personKey) ?? 0) > 1
      return repeated ? (
        <button
          key={name}
          type="button"
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
      {selectedMovie.source === 'SERIES' && (
        <nav
          aria-label="Choose episode"
          className="hide-scrollbar flex gap-2 overflow-x-auto px-4 py-2 lg:px-5"
        >
          {selectedMovie.episodes.map((episode) => (
            <button
              key={episode.id}
              type="button"
              aria-label={`Episode ${episode.episodeNumber}`}
              aria-pressed={selectedMovie.episodeId === episode.id}
              onClick={() => openEpisode(episode)}
              className={`shrink-0 rounded-full border px-3 py-1.5 text-xs font-medium transition ${
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

      <div className="pb-2 pt-6 pl-3 pr-5 sm:pl-4 sm:pr-7 lg:pl-5 lg:pr-4">
        <div className="space-y-8">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-3 text-sm text-white/75">
            <span className="inline-flex min-w-0 flex-wrap items-center gap-x-3 gap-y-2">
              {selectedMovie.imdbRating != null && (
                <span className="inline-flex items-center gap-1 px-2 py-1">
                  <Star size={12} fill="currentColor" />
                  {selectedMovie.imdbRating.toFixed(1)}
                </span>
              )}
              {selectedMovie.year && <span>{selectedMovie.year}</span>}
              {selectedMovie.genre && <span>{selectedMovie.genre}</span>}
              {selectedMovie.runtime && <span>{selectedMovie.runtime}</span>}
              {selectedMovie.rated && (selectedMovie.source === 'SERIES' ? (
                <span className="rounded border border-white/20 px-1.5">{selectedMovie.rated}</span>
              ) : (() => {
                  const rated = selectedMovie.rated.trim()
                  const ratedKey = rated.normalize('NFC').toLocaleLowerCase('de')
                  const isFilterable = (ratedMovieCounts.get(ratedKey) ?? 0) > 1
                  const isSelected = selectedRated === ratedKey
                  return isFilterable ? (
                    <button
                      type="button"
                      aria-pressed={isSelected}
                      onClick={() => {
                        setSelectedGenre('')
                        setSelectedPerson('')
                        setSelectedRated((current) => current === ratedKey ? '' : ratedKey)
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
                    <span className="rounded border border-white/20 px-1.5">{selectedMovie.rated}</span>
                  )
                })())}
              <AwardWins content={selectedMovie} />
            </span>
            <button
              type="button"
              onClick={onStartPlayback}
              className="order-first mr-3 inline-flex items-center gap-2 whitespace-nowrap rounded-md border border-accent bg-accent px-6 py-2.5 text-sm font-semibold text-black transition hover:border-accent-hover hover:bg-accent-hover"
            >
              <Play size={14} fill="currentColor" />
              Play
            </button>
          </div>
          <div className={`grid ${
            selectedMovie.director || selectedMovie.actors || selectedMovie.writer
              ? 'grid-cols-[minmax(0,3fr)_minmax(0,2fr)] gap-5'
              : 'grid-cols-1'
          }`}>
            <section>
              <p className="text-sm leading-relaxed text-white/80">
                {selectedMovie.plot || (selectedMovie.source === 'SERIES'
                  ? 'No further information is available for this series.'
                  : 'No further information is available for this movie.')}
              </p>
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
              <section className="flex flex-col gap-2 border-l border-white/10 pl-4 text-sm">
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
