import assert from 'node:assert/strict'
import test from 'node:test'
import { buildPlaybackUrl, formatTime, getPlaybackBase, trackLabel } from './playback.js'

test('builds a preview URL for movies', () => {
  assert.equal(buildPlaybackUrl(
    { source: 'LOCAL', id: 12 },
    {
      isFullPlayback: false,
      selectedAudioTrack: '',
      selectedSubtitleTrack: '',
      playbackStart: 0,
      previewIteration: 2,
    }
  ), '/api/movies/12/play?preview=true&cycle=2')
})

test('wide preview URLs request a device-safe aspect crop', () => {
  assert.equal(buildPlaybackUrl({ source: 'LOCAL', id: 12 }, {
    isFullPlayback: false,
    previewIteration: 2,
    widePreview: true,
  }), '/api/movies/12/play?preview=true&cycle=2&wide=true')
})

test('builds full-playback URLs for series with selected tracks and seek position', () => {
  const episode = { source: 'SERIES', episodeId: 7 }
  assert.equal(getPlaybackBase(episode), '/api/series/episodes/7')
  assert.equal(buildPlaybackUrl(episode, {
    isFullPlayback: true,
    selectedAudioTrack: '2',
    selectedSubtitleTrack: '5',
    playbackStart: 45.1254,
    previewIteration: 0,
  }), '/api/series/episodes/7/play?audioTrack=2&subtitleTrack=5&start=45.125')
})

test('returns no playback URL when no catalog item is selected', () => {
  assert.equal(buildPlaybackUrl(null, {}), '')
})

test('never starts a preview or transcode for an external movie', () => {
  assert.equal(buildPlaybackUrl({ source: 'EXTERNAL', id: 12 }, {}), '')
})

test('formats playback positions, including hours', () => {
  assert.equal(formatTime(65.9), '01:05')
  assert.equal(formatTime(3661), '1:01:01')
})

test('formats invalid and negative positions safely', () => {
  assert.equal(formatTime(Number.NaN), '00:00')
  assert.equal(formatTime(-1), '00:00')
})

test('uses track title when language is unavailable', () => {
  assert.equal(trackLabel({ language: 'und', title: 'Commentary' }, 0), 'Commentary')
})

test('falls back to a numbered label when track metadata is missing', () => {
  assert.equal(trackLabel({}, 1), 'Spur 2')
})
