import test from 'node:test'
import assert from 'node:assert/strict'
import {
  getTvKeyAction,
  isBlockedVerticalNavigation,
  isTextCursorAtBoundary,
  isWriterRightNavigation,
  isWriterUpNavigation,
  isRightmostInRow,
  isTopmostInColumn,
} from './tvNavigation.js'

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

test('recognizes the right edge of a poster row without treating the next row as a right neighbor', () => {
  const source = {left: 10, top: 20, right: 110, bottom: 170}

  assert.equal(isRightmostInRow(source, [
    {left: 120, top: 20, right: 220, bottom: 170},
  ]), false)
  assert.equal(isRightmostInRow(source, [
    {left: 10, top: 180, right: 110, bottom: 330},
  ]), true)
  assert.equal(isRightmostInRow(source, []), true)
})

test('recognizes the top of a poster column without treating the next column as an upper neighbor', () => {
  const source = {left: 10, top: 180, right: 110, bottom: 330}

  assert.equal(isTopmostInColumn(source, [
    {left: 10, top: 20, right: 110, bottom: 170},
  ]), false)
  assert.equal(isTopmostInColumn(source, [
    {left: 120, top: 20, right: 220, bottom: 170},
  ]), true)
  assert.equal(isTopmostInColumn(source, []), true)
})

test('moves focus out of the search field only when horizontal cursor movement reaches an edge', () => {
  assert.equal(isTextCursorAtBoundary('left', 0, 0, 5), true)
  assert.equal(isTextCursorAtBoundary('left', 2, 2, 5), false)
  assert.equal(isTextCursorAtBoundary('right', 5, 5, 5), true)
  assert.equal(isTextCursorAtBoundary('right', 2, 2, 5), false)
  assert.equal(isTextCursorAtBoundary('right', 1, 4, 5), false)
})

test('blocks only vertical navigation directly between playback actions and the rated filter', () => {
  assert.equal(isBlockedVerticalNavigation('up', 'playback-action', 'rated-filter'), true)
  assert.equal(isBlockedVerticalNavigation('down', 'rated-filter', 'playback-action'), true)
  assert.equal(isBlockedVerticalNavigation('down', 'cast', 'rated-filter'), false)
  assert.equal(isBlockedVerticalNavigation('left', 'playback-action', 'rated-filter'), false)
})

test('maps upward navigation from a writer chip to the primary play action', () => {
  assert.equal(isWriterUpNavigation('up', 'writer'), true)
  assert.equal(isWriterUpNavigation('up', 'cast'), false)
  assert.equal(isWriterUpNavigation('down', 'writer'), false)
})

test('maps rightward navigation from a writer chip to the cast group', () => {
  assert.equal(isWriterRightNavigation('right', 'writer'), true)
  assert.equal(isWriterRightNavigation('right', 'cast'), false)
  assert.equal(isWriterRightNavigation('left', 'writer'), false)
})
