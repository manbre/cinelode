import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

test('text sizes increase without changing the layout root size', () => {
  const css = readFileSync(new URL('../index.css', import.meta.url), 'utf8')
  assert.match(css, /--text-xs:\s*0\.7875rem;/)
  assert.match(css, /--text-sm:\s*0\.91875rem;/)
  assert.match(css, /--text-base:\s*1\.05rem;/)
  assert.match(css, /--text-2xl:\s*1\.575rem;/)
  assert.match(css, /--text-4xl:\s*2\.3625rem;/)
  assert.match(css, /body\s*\{[^}]*font-size:\s*1\.05rem;/)
  assert.match(css, /html\s*\{\s*font-size:\s*clamp\(16px,\s*1\.25vw,\s*32px\);/)
})

test('IMDb rating displays its star and value without a border', () => {
  const details = readFileSync(new URL('../components/MovieDetails.jsx', import.meta.url), 'utf8')
  assert.match(details, /selectedMovie\.imdbRating != null && \(\s*<span className="inline-flex items-center gap-1">\s*<Star[^>]*\/>\s*\{selectedMovie\.imdbRating\.toFixed\(1\)\}/)
})

test('browser previews share the same large-screen layout without changing full playback', () => {
  const player = readFileSync(new URL('../components/VideoPlayer.jsx', import.meta.url), 'utf8')
  const details = readFileSync(new URL('../components/MovieDetails.jsx', import.meta.url), 'utf8')
  const home = readFileSync(new URL('../pages/Home.jsx', import.meta.url), 'utf8')
  assert.match(player, /const useVegaPreview = !isFullPlayback && isNativeWebView\(\)/)
  assert.match(player, /useVegaPreview\s*\?\s*'-top-2 aspect-video min-h-0 lg:top-0'\s*:\s*'aspect-video min-h-\[17\.5rem\] sm:min-h-\[21\.25rem\]'/)
  assert.match(player, /!isFullPlayback\s*\?\s*useVegaPreview\s*\?\s*'preview-layout-player lg:aspect-auto lg:h-full lg:min-h-0'\s*:\s*'preview-layout-player \[clip-path:inset\(0\)\] lg:aspect-auto lg:h-full lg:min-h-0'\s*:\s*''/)
  assert.match(player, /!isFullPlayback\s*\?\s*'preview-video'\s*:\s*'inset-0 size-full'/)
  assert.match(player, /selectedMovie\.posterUrl && \(\s*<img[\s\S]*?className="absolute inset-0 size-full object-contain"/)
  assert.match(details, /className="space-y-6"/)
  assert.match(details, /className="pb-2 pt-6 pl-3 pr-5 sm:pl-4 sm:pr-7 lg:pl-5 lg:pr-4"/)
  const css = readFileSync(new URL('../index.css', import.meta.url), 'utf8')
  assert.match(css, /\.preview-video\s*\{[^}]*object-fit:\s*cover;[^}]*object-position:\s*50% 15%;[^}]*transform:\s*scale\(1\.02\);/)
  assert.doesNotMatch(player, /new ResizeObserver/)
  assert.doesNotMatch(player, /border-4 border-red-500/)
  assert.doesNotMatch(css, /\.vega-preview-video/)
  assert.match(home, /!isFullPlayback\s*\?\s*'details-panel'\s*:\s*'lg:overflow-y-auto'/)
  assert.match(home, /className="movie-info min-h-0 lg:overflow-y-auto"/)
})

test('browser previews retain the responsive grid and scrollable details', () => {
  const css = readFileSync(new URL('../index.css', import.meta.url), 'utf8')
  assert.match(css, /@media \(min-width: 1024px\)[\s\S]*?\.details-panel\s*\{[^}]*grid-template-rows:\s*52\.5%\s+minmax\(0,\s*1fr\)/)
  assert.doesNotMatch(css, /browser-preview-overlap|preview-layout-player\s*\+\s*\.movie-info\s*\{[^}]*transform/)
  assert.match(css, /\.details-panel\s*>\s*\.movie-info\s*\{[^}]*overflow-y:\s*auto/)
})
