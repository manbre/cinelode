import assert from 'node:assert/strict'
import test from 'node:test'
import { openExternalMovie } from './externalPlayback.js'

const movie = { id: 42, source: 'EXTERNAL' }

test('opens the authenticated external link in a new browser window', async () => {
  let destination
  let endpoint
  await openExternalMovie(movie, {
    openWindow: () => ({ location: { replace: (url) => { destination = url } } }),
    fetchUrl: async (url) => {
      endpoint = url
      return { ok: true, json: async () => ({ url: 'https://example.com/watch' }) }
    },
  })
  assert.equal(endpoint, '/api/movies/42/external-url')
  assert.equal(destination, 'https://example.com/watch')
})

test('passes the URL to the native bridge without opening a browser window', async () => {
  let message
  await openExternalMovie(movie, {
    nativeBridge: { postMessage: (data) => { message = JSON.parse(data) } },
    fetchUrl: async () => ({ ok: true, json: async () => ({ url: 'https://example.com/watch' }) }),
  })
  assert.deepEqual(message, { type: 'cinelode-open-external', url: 'https://example.com/watch' })
})

test('closes the window and reports invalid URLs and failed requests', async () => {
  for (const response of [
    { ok: false },
    { ok: true, json: async () => ({ url: 'javascript:alert(1)' }) },
    { ok: true, json: async () => ({ url: 'https://user:password@example.com/' }) },
    { ok: true, json: async () => ({ url: null }) },
  ]) {
    let closed = false
    await assert.rejects(openExternalMovie(movie, {
      openWindow: () => ({ close: () => { closed = true } }),
      fetchUrl: async () => response,
    }))
    assert.equal(closed, true)
  }
})

test('reports a blocked popup without fetching the URL', async () => {
  await assert.rejects(openExternalMovie(movie, {
    openWindow: () => null,
    fetchUrl: () => assert.fail('must not fetch'),
  }), /blocked/)
})
