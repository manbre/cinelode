export async function openExternalMovie(movie, { fetchUrl, openWindow, nativeBridge }) {
  const popup = nativeBridge ? null : openWindow()
  if (!nativeBridge && !popup) {
    throw new Error('The browser blocked the new window. Allow pop-ups and try again.')
  }

  try {
    const response = await fetchUrl(`/api/movies/${encodeURIComponent(movie.id)}/external-url`)
    if (!response.ok) {
      throw new Error('The external movie link could not be loaded.')
    }
    const data = await response.json()
    if (typeof data.url !== 'string') {
      throw new Error('The external movie link is invalid.')
    }
    const url = new URL(data.url)
    if (!['http:', 'https:'].includes(url.protocol) || !url.hostname || url.username || url.password) {
      throw new Error('The external movie link is invalid.')
    }
    if (nativeBridge) {
      nativeBridge.postMessage(JSON.stringify({ type: 'cinelode-open-external', url: url.href }))
    } else {
      popup.location.replace(url.href)
    }
  } catch (error) {
    popup?.close()
    throw error
  }
}
