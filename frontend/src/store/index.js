import { create } from 'zustand'
import { createCatalogSlice } from './catalogSlice'
import { createNavigationSlice } from './navigationSlice'
import { createPlaylistSlice } from './playlistSlice'

export const useCinelodeStore = create((...args) => ({
  ...createCatalogSlice(...args),
  ...createPlaylistSlice(...args),
  ...createNavigationSlice(...args),
}))
