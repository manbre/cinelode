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
