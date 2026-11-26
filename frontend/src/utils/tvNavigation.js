const KEY_ACTIONS = {
  ArrowUp: 'up',
  ArrowDown: 'down',
  ArrowLeft: 'left',
  ArrowRight: 'right',
  Enter: 'activate',
  ' ': 'activate',
  Escape: 'back',
  BrowserBack: 'back',
  GoBack: 'back',
  MediaPlayPause: 'playPause',
  PlayPause: 'playPause',
  MediaFastForward: 'fastForward',
  FastForward: 'fastForward',
  MediaRewind: 'rewind',
  Rewind: 'rewind',
  MediaTrackNext: 'fastForward',
  MediaTrackPrevious: 'rewind',
}

export function getTvKeyAction(key) {
  return KEY_ACTIONS[key] ?? null
}

export function isRightmostInRow(source, cards) {
  return !cards.some((card) => card.left > source.left
    && card.top < source.bottom
    && card.bottom > source.top)
}

export function isTopmostInColumn(source, cards) {
  return !cards.some((card) => card.top < source.top
    && card.left < source.right
    && card.right > source.left)
}

export function isTextCursorAtBoundary(direction, selectionStart, selectionEnd, valueLength) {
  return (direction === 'left' && selectionStart === 0 && selectionEnd === 0)
    || (direction === 'right'
      && selectionStart === valueLength
      && selectionEnd === valueLength)
}

export function isBlockedVerticalNavigation(direction, sourceRole, targetRole) {
  const isVertical = direction === 'up' || direction === 'down'
  const isPlaybackRatedPair = (sourceRole === 'playback-action' && targetRole === 'rated-filter')
    || (sourceRole === 'rated-filter' && targetRole === 'playback-action')
  return isVertical && isPlaybackRatedPair
}

export function isWriterUpNavigation(direction, personRole) {
  return direction === 'up' && personRole === 'writer'
}

export function isWriterRightNavigation(direction, personRole) {
  return direction === 'right' && personRole === 'writer'
}

export function handleModalDirection(dialog, activeElement, direction, focusInDirection) {
  if (!dialog?.open) return false
  if (dialog.contains(activeElement)) {
    focusInDirection(direction, dialog)
  } else {
    dialog.querySelector('button:not([disabled])')?.focus()
  }
  return true
}
