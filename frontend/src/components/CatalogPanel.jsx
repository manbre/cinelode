import { Clapperboard, LogOut, Search, Tv } from 'lucide-react'
import { contentKey } from '../utils/catalog'

// Renders one selectable film or season poster in the catalog.
function MovieCard({ movie, index, selected, playlistPosition, onSelect }) {
  const isSeriesCard = movie.source === 'SERIES'

  return (
    <button
      type="button"
      onClick={onSelect}
      aria-label={isSeriesCard
        ? `${movie.title}, Season ${movie.seasonNumber}`
        : movie.title}
      className={`group w-full min-w-0 justify-self-center scroll-mt-24 rounded-xl text-left transition ${
        selected ? 'text-white' : 'text-white/90'
      }`}
    >
      <div className={`relative aspect-[2/3] overflow-hidden rounded-xl bg-surface-hover shadow-lg transition group-hover:-translate-y-1 ${
        selected ? 'ring-[0.22rem] ring-accent' : ''
      }`}>
        <img
          src={movie.posterUrl}
          alt={isSeriesCard
            ? `Poster: ${movie.title}, Season ${movie.seasonNumber}`
            : `Poster: ${movie.title}`}
          loading={index < 12 ? 'eager' : 'lazy'}
          decoding={index < 12 ? 'sync' : 'async'}
          fetchPriority={index < 8 ? 'high' : 'auto'}
          className="h-full w-full object-cover transition duration-300 group-hover:scale-[1.03]"
          onError={(event) => {
            event.currentTarget.style.visibility = 'hidden'
          }}
        />
        {playlistPosition !== undefined && (
          <span className="absolute right-2 top-2 inline-flex size-7 items-center justify-center rounded-md bg-black/75 text-sm font-bold text-accent backdrop-blur">
            {playlistPosition}
          </span>
        )}
      </div>
    </button>
  )
}

// Owns the catalog's search, filters, loading/error states, and result list.
function CatalogPanel({
  onLogout,
  loading,
  error,
  availableGenres,
  selectedGenre,
  setSelectedGenre,
  setSelectedPerson,
  setSelectedRated,
  selectedDuration,
  setSelectedDuration,
  isSeriesMode,
  changeCatalogMode,
  search,
  setSearch,
  filteredMovies,
  playlistPositions,
  selectedMovie,
  openMovie,
  openSeason,
}) {
  return (
    <section className="hide-scrollbar relative z-10 order-2 min-h-0 flex-1 overflow-y-auto bg-[#151516]/55 px-5 pb-5 pt-0 sm:px-7 lg:order-1 lg:px-8 lg:pl-3 lg:pr-3">
      <div className="sticky top-0 z-20 -mx-5 mb-3 bg-[#151516]/75 px-5 pb-4 pt-5 backdrop-blur-md sm:-mx-7 sm:px-7 lg:-mx-8 lg:px-8">
        <div className="mb-[10.667px] flex justify-center">
          <div className="flex w-full max-w-3xl items-center gap-2">
            <span
              aria-live="polite"
              aria-label={loading ? 'Loading results' : `${filteredMovies.length} results`}
              className="min-w-[2.25rem] shrink-0 text-center text-xs font-medium tabular-nums text-white/60"
            >
              {loading ? '…' : filteredMovies.length}
            </span>
            <button
              type="button"
              aria-pressed={Boolean(selectedDuration)}
              onClick={() => setSelectedDuration(selectedDuration ? '' : true)}
              className={`h-[2.125rem] shrink-0 rounded-full border px-3 py-2 text-[0.721875rem] font-medium transition sm:px-3.5 sm:text-xs ${
                selectedDuration
                  ? 'border-accent bg-accent text-black'
                  : 'border-white/10 bg-white/[0.03] text-white/60 hover:bg-white/10 hover:text-white'
              }`}
            >
              ≤ {isSeriesMode ? 25 : 105} min
            </button>
            <label className="mx-auto flex h-[2.125rem] min-w-0 max-w-lg flex-1 items-center gap-3 rounded-full border border-white/10 bg-white/[0.04] px-4 py-0 text-text-secondary transition focus-within:border-white/25 focus-within:bg-white/[0.07]">
              <Search size={18} className="shrink-0" />
              <input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search"
                aria-label="Search movies"
                className="min-w-0 flex-1 bg-transparent text-sm text-text-primary outline-none placeholder:text-white/40"
              />
            </label>
            <div aria-label="Choose view" className="flex h-[2.125rem] w-[5.75rem] shrink-0 items-center rounded-full border border-white/10 bg-white/[0.03] p-[0.125rem]">
              <button
                type="button"
                onClick={() => changeCatalogMode(false)}
                aria-label="Movies"
                aria-pressed={!isSeriesMode}
                className={`flex flex-1 justify-center rounded-full p-[0.25rem] ${
                  isSeriesMode ? 'text-white/40' : 'bg-accent text-black'
                }`}
              >
                <Clapperboard size={16} />
              </button>
              <button
                type="button"
                onClick={() => changeCatalogMode(true)}
                aria-label="Series"
                aria-pressed={isSeriesMode}
                className={`flex flex-1 justify-center rounded-full p-[0.25rem] ${
                  isSeriesMode ? 'bg-accent text-black' : 'text-white/40'
                }`}
              >
                <Tv size={16} />
              </button>
            </div>
            {onLogout && (
              <button
                type="button"
                onClick={onLogout}
                aria-label="Sign out"
                title="Sign out"
                className="flex h-[2.125rem] w-[2.125rem] shrink-0 items-center justify-center rounded-full border border-white/10 bg-white/[0.03] text-white/60 transition hover:border-white/25 hover:text-white"
              >
                <LogOut size={15} />
              </button>
            )}
          </div>
        </div>

        {!loading && availableGenres.length > 0 && (
          <div
            aria-label="Filter by genre"
            className="hide-scrollbar -mx-1 flex gap-2 overflow-x-auto p-1"
          >
            {['', ...availableGenres].map((genre) => (
              <button
                key={genre || 'all-genres'}
                type="button"
                aria-pressed={selectedGenre === genre}
                onClick={() => {
                  setSelectedPerson('')
                  setSelectedRated('')
                  setSelectedGenre(genre)
                }}
                className={`min-h-10 shrink-0 rounded-full border px-3.5 py-1.5 text-xs font-medium transition ${
                  selectedGenre === genre
                    ? 'border-accent bg-accent text-black'
                    : 'border-white/10 bg-white/[0.04] text-white/65 hover:border-white/25 hover:text-white'
                }`}
              >
                {genre || 'All'}
              </button>
            ))}
          </div>
        )}
      </div>

      {error && (
        <p
          role="alert"
          className="mb-4 rounded-lg border border-red-900 bg-red-950/40 p-3 text-sm text-red-300"
        >
          {error}
        </p>
      )}

      {loading ? (
        <p className="text-sm text-text-secondary">Loading library …</p>
      ) : filteredMovies.length === 0 ? (
        <p className="text-sm text-text-secondary">
          {isSeriesMode ? 'No seasons found.' : 'No movies found.'}
        </p>
      ) : (
        <div className="grid grid-cols-2 gap-x-4 gap-y-4 sm:grid-cols-3 sm:gap-x-5 lg:grid-cols-4 lg:gap-x-2.5 lg:gap-y-4">
          {filteredMovies.map((movie, index) => (
            <MovieCard
              key={movie.id}
              movie={movie}
              index={index}
              selected={selectedMovie?.source === movie.source && selectedMovie?.id === movie.id}
              playlistPosition={playlistPositions.get(contentKey(movie))}
              onSelect={() => movie.source === 'SERIES' ? openSeason(movie) : openMovie(movie)}
            />
          ))}
        </div>
      )}
    </section>
  )
}

export default CatalogPanel
