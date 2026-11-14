import assert from 'node:assert/strict'
import test from 'node:test'
import { getAwardWins } from './awards.js'

test('returns confirmed Oscar and Primetime Emmy wins with their counts', () => {
  assert.deepEqual(getAwardWins({ oscarWins: 3, primetimeEmmyWins: 12 }), [
    { type: 'oscar', count: 3 },
    { type: 'emmy', count: 12 },
  ])
})

test('omits missing, zero, negative and invalid counts', () => {
  for (const count of [undefined, null, 0, -1, 1.5, '2', NaN, Infinity]) {
    assert.deepEqual(getAwardWins({ oscarWins: count, primetimeEmmyWins: count }), [])
  }
})

test('preserves one award when the other is not available', () => {
  assert.deepEqual(getAwardWins({ oscarWins: 1 }), [{ type: 'oscar', count: 1 }])
  assert.deepEqual(getAwardWins({ primetimeEmmyWins: 2 }), [{ type: 'emmy', count: 2 }])
})
