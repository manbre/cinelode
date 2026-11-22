import assert from 'node:assert/strict'
import test from 'node:test'
import {
  compareMovies,
  getInitialLibrarySelection,
  getNextPlaybackSelection,
  getPeople,
  isMovie,
  normalizePersonName,
} from './catalog.js'

test('includes external movies in the movie catalog', () => {
  assert.equal(isMovie({ source: 'EXTERNAL' }), true)
  assert.equal(isMovie({ source: 'LOCAL' }), true)
  assert.equal(isMovie({ source: 'SERIES' }), false)
})

test('sorts entries in the same franchise by their numeric franchise number', () => {
  const movies = [
    { franchise: 'Saga', franchiseNumber: '10', title: 'Finale', year: 2020 },
    { franchise: 'Saga', franchiseNumber: '2', title: 'Beginning', year: 2000 },
  ]

  assert.deepEqual(movies.sort(compareMovies).map((movie) => movie.franchiseNumber), ['2', '10'])
})

test('sorts franchise groups before standalone titles', () => {
  const movies = [
    { title: 'Zebra', year: 2020 },
    { franchise: 'Alpha', title: 'First', year: 2000 },
  ]

  assert.deepEqual(movies.sort(compareMovies).map((movie) => movie.title), ['First', 'Zebra'])
})

test('uses the first sorted movie as the initial library selection', () => {
  const movies = [
    { source: 'SERIES', id: 1, title: 'A Series' },
    { source: 'LOCAL', id: 2, title: 'Zebra', year: 2020 },
    { source: 'EXTERNAL', id: 3, title: 'Alpha', year: 2000 },
  ]

  assert.equal(getInitialLibrarySelection(movies).id, 3)
  assert.equal(getInitialLibrarySelection([{ source: 'SERIES', id: 1 }]), null)
})

test('normalizes names before comparison', () => {
  assert.equal(normalizePersonName('Mu\u0308ller'), normalizePersonName('Müller'))
})

test('splits, trims, and omits empty people names', () => {
  assert.deepEqual(getPeople({ actors: ' Alice, , Bob ' }, 'actors'), ['Alice', 'Bob'])
})

test('advances movies in catalog order when no playlist exists', () => {
  const movies = [
    { source: 'LOCAL', id: 1, title: 'A' },
    { source: 'LOCAL', id: 2, title: 'B' },
  ]

  assert.equal(
    getNextPlaybackSelection({
      selectedContent: movies[0],
      playlist: [],
      localMovies: movies,
      seriesSeasons: [],
    }).id,
    2
  )
})

test('advances episodes and then the next season without a playlist', () => {
  const seasons = [
    {
      source: 'SERIES',
      id: 10,
      title: 'Show',
      seasonNumber: 1,
      episodes: [{ id: 101, episodeNumber: 1 }, { id: 102, episodeNumber: 2 }],
    },
    {
      source: 'SERIES',
      id: 11,
      title: 'Show',
      seasonNumber: 2,
      episodes: [{ id: 201, episodeNumber: 1 }],
    },
  ]
  const selected = { ...seasons[0], episodeId: 102, episodeNumber: 2 }

  assert.deepEqual(
    getNextPlaybackSelection({
      selectedContent: selected,
      playlist: [],
      localMovies: [],
      seriesSeasons: seasons,
    }),
    { ...seasons[1], episodeId: 201, episodeNumber: 1 }
  )
})

test('starts the next series after the final season', () => {
  const seasons = [
    {
      source: 'SERIES',
      id: 20,
      title: 'First Show',
      seasonNumber: 1,
      episodes: [{ id: 201, episodeNumber: 1 }],
    },
    {
      source: 'SERIES',
      id: 30,
      title: 'Next Show',
      seasonNumber: 1,
      episodes: [{ id: 301, episodeNumber: 1 }],
    },
  ]

  assert.deepEqual(
    getNextPlaybackSelection({
      selectedContent: { ...seasons[0], episodeId: 201, episodeNumber: 1 },
      playlist: [],
      localMovies: [],
      seriesSeasons: seasons,
    }),
    { ...seasons[1], episodeId: 301, episodeNumber: 1 }
  )
})
