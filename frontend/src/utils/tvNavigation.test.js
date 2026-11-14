import test from 'node:test'
import assert from 'node:assert/strict'
import { getTvKeyAction } from './tvNavigation.js'

test('maps the remote D-pad to native focus navigation', () => {
  assert.deepEqual(
    ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].map(getTvKeyAction),
    ['up', 'down', 'left', 'right'],
  )
})

test('maps select, back, and play/pause without duplicating activation', () => {
  assert.equal(getTvKeyAction('Enter'), 'activate')
  assert.equal(getTvKeyAction(' '), 'activate')
  assert.equal(getTvKeyAction('Escape'), 'back')
  assert.equal(getTvKeyAction('BrowserBack'), 'back')
  assert.equal(getTvKeyAction('MediaPlayPause'), 'playPause')
  assert.equal(getTvKeyAction('UnrelatedKey'), null)
})
