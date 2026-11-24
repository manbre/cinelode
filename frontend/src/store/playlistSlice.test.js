import assert from 'node:assert/strict'
import test from 'node:test'
import { createPlaylistSlice } from './playlistSlice.js'

function createStore() {
  let state
  const set = (update) => {
    const changes = typeof update === 'function' ? update(state) : update
    state = { ...state, ...changes }
  }
  state = createPlaylistSlice(set)
  return { getState: () => state }
}

test('does not add external titles to the playlist', () => {
  const store = createStore()

  store.getState().togglePlaylistItem({ source: 'EXTERNAL', id: 4 })

  assert.deepEqual(store.getState().playlist, [])
})

test('still toggles local titles in the playlist', () => {
  const store = createStore()
  const movie = { source: 'LOCAL', id: 7 }

  store.getState().togglePlaylistItem(movie)
  assert.deepEqual(store.getState().playlist, [movie])

  store.getState().togglePlaylistItem(movie)
  assert.deepEqual(store.getState().playlist, [])
})
