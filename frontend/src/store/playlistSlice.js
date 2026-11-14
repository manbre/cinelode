import { contentKey } from '../utils/catalog'

export const createPlaylistSlice = (set) => ({
  playlist: [],
  togglePlaylistItem: (movie) => set((state) => {
    const key = contentKey(movie)
    const existingIndex = state.playlist.findIndex((item) => contentKey(item) === key)
    if (existingIndex >= 0) {
      return {
        playlist: state.playlist.filter((_, index) => index !== existingIndex),
      }
    }
    return { playlist: [...state.playlist, movie] }
  }),
  clearPlaylist: () => set({ playlist: [] }),
})
