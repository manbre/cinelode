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
  assert.match(player, /useVegaPreview\s*\?\s*'-top-2 aspect-video min-h-0 md:top-0'\s*:\s*'aspect-video min-h-0'/)
  assert.match(player, /!isFullPlayback\s*\?\s*useVegaPreview\s*\?\s*'preview-layout-player md:aspect-auto md:h-full md:min-h-0'\s*:\s*'preview-layout-player \[clip-path:inset\(0\)\] md:aspect-auto md:h-full md:min-h-0'\s*:\s*''/)
  assert.match(player, /!isFullPlayback\s*\?\s*'preview-video'\s*:\s*'inset-0 size-full'/)
  assert.match(player, /selectedMovie\.posterUrl && \(\s*<img[\s\S]*?className="absolute inset-0 size-full object-contain"/)
  assert.match(details, /className="mx-auto max-w-md px-4 pb-2 pt-6 md:mx-0 md:max-w-none md:pr-7 lg:pl-5 lg:pr-4"/)
  const css = readFileSync(new URL('../index.css', import.meta.url), 'utf8')
  assert.match(css, /\.preview-video\s*\{[^}]*object-fit:\s*cover;[^}]*object-position:\s*50% 15%;[^}]*transform:\s*scale\(1\.02\);/)
  assert.doesNotMatch(player, /new ResizeObserver/)
  assert.doesNotMatch(player, /border-4 border-red-500/)
  assert.doesNotMatch(css, /\.vega-preview-video/)
  assert.match(home, /!isFullPlayback\s*\?\s*'details-panel'/)
  assert.match(home, /className="movie-info min-h-0 md:overflow-y-auto"/)
})

test('browser previews retain the responsive grid and scrollable details', () => {
  const css = readFileSync(new URL('../index.css', import.meta.url), 'utf8')
  assert.match(css, /@media \(min-width: 768px\)[\s\S]*?\.details-panel\s*\{[^}]*grid-template-rows:\s*min\(52\.5%, 31vw\)\s+minmax\(0,\s*1fr\)/)
  assert.doesNotMatch(css, /browser-preview-overlap|preview-layout-player\s*\+\s*\.movie-info\s*\{[^}]*transform/)
  assert.match(css, /\.details-panel\s*>\s*\.movie-info\s*\{[^}]*overflow-y:\s*auto/)
})

test('tablets get two panels and the poster count follows the catalog width', () => {
  const css = readFileSync(new URL('../index.css', import.meta.url), 'utf8')
  assert.match(css, /@media \(min-width: 768px\)\s*\{\s*\.layout-frame\s*\{[^}]*grid-template-columns:\s*minmax\(0, 45fr\) minmax\(0, 55fr\)/)
  assert.match(css, /\.catalog-grid\s*\{[^}]*grid-template-columns:\s*repeat\(auto-fill, minmax/)
})

test('mobile details show the episode picker before the full plot and credits', () => {
  const details = readFileSync(new URL('../components/MovieDetails.jsx', import.meta.url), 'utf8')
  assert.match(details, /data-primary-play="true"[\s\S]*aria-label="Choose episode"[\s\S]*<PlotText/)
  assert.match(details, /<span className="md:hidden">\{text\}<\/span>/)
  assert.doesNotMatch(details, /More info|Less info/)
})

test('mobile playlist control follows restart in the playback actions', () => {
  const details = readFileSync(new URL('../components/MovieDetails.jsx', import.meta.url), 'utf8')
  assert.match(details, /onClick=\{onRestartPlayback\}[\s\S]*?onClick=\{onTogglePlaylist\}[\s\S]*?md:hidden[\s\S]*?\{playlistAction\}[\s\S]*?Choose episode/)
})

test('Play and Resume reserve the same space beneath the button', () => {
  const details = readFileSync(new URL('../components/MovieDetails.jsx', import.meta.url), 'utf8')
  assert.match(details, /<\/button>\s*<p\s+id=\{remainingTimeId\}\s+aria-hidden=\{!canResume\}\s+className="mt-1 min-h-5/)
})
