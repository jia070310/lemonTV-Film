import type { Movie } from '@/data/mockData'

export const LIBRARY_CHANGED_EVENT = 'lemon-library-changed'

const FAVORITES_KEY = 'lemonTv.library.favorites.v1'
const HISTORY_KEY = 'lemonTv.library.history.v1'
const MAX_HISTORY = 120

export type WatchHistoryItem = {
  movie: Movie
  /** 0–100，当前集内观看进度 */
  progress: number
  updatedAt: number
}

function isMovieish(x: unknown): x is Movie {
  if (!x || typeof x !== 'object') return false
  const o = x as Record<string, unknown>
  return typeof o.id === 'string' && typeof o.title === 'string'
}

function parseMovieList(raw: string | null): Movie[] {
  if (raw == null || raw === '') return []
  try {
    const v = JSON.parse(raw) as unknown
    if (!Array.isArray(v) || !v.every(isMovieish)) return []
    return v as Movie[]
  } catch {
    return []
  }
}

function parseHistory(raw: string | null): WatchHistoryItem[] {
  if (raw == null || raw === '') return []
  try {
    const v = JSON.parse(raw) as unknown
    if (!Array.isArray(v)) return []
    const out: WatchHistoryItem[] = []
    for (const row of v) {
      if (!row || typeof row !== 'object') continue
      const o = row as Record<string, unknown>
      if (!isMovieish(o.movie)) continue
      const progress = typeof o.progress === 'number' ? o.progress : 0
      const updatedAt = typeof o.updatedAt === 'number' ? o.updatedAt : 0
      out.push({
        movie: o.movie as Movie,
        progress: Math.min(100, Math.max(0, progress)),
        updatedAt,
      })
    }
    return out
  } catch {
    return []
  }
}

function notifyLibraryChanged(): void {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent(LIBRARY_CHANGED_EVENT))
}

function writeFavorites(list: Movie[]): void {
  if (typeof localStorage === 'undefined') return
  try {
    localStorage.setItem(FAVORITES_KEY, JSON.stringify(list))
  } catch {
    // ignore
  }
  notifyLibraryChanged()
}

export function getFavorites(): Movie[] {
  if (typeof localStorage === 'undefined') return []
  return parseMovieList(localStorage.getItem(FAVORITES_KEY))
}

export function isFavorite(movieId: string): boolean {
  return getFavorites().some((m) => m.id === movieId)
}

/** @returns 操作后是否已收藏 */
export function toggleFavorite(movie: Movie): boolean {
  const list = getFavorites()
  const idx = list.findIndex((m) => m.id === movie.id)
  if (idx >= 0) {
    list.splice(idx, 1)
    writeFavorites(list)
    return false
  }
  const next = [movie, ...list.filter((m) => m.id !== movie.id)]
  writeFavorites(next)
  return true
}

export function getWatchHistory(): WatchHistoryItem[] {
  if (typeof localStorage === 'undefined') return []
  return parseHistory(localStorage.getItem(HISTORY_KEY)).sort(
    (a, b) => b.updatedAt - a.updatedAt
  )
}

export function upsertWatchHistory(movie: Movie, progress: number): void {
  if (typeof localStorage === 'undefined') return
  const p = Math.min(100, Math.max(0, progress))
  const list = parseHistory(localStorage.getItem(HISTORY_KEY))
  const filtered = list.filter((x) => x.movie.id !== movie.id)
  const next: WatchHistoryItem[] = [
    { movie, progress: p, updatedAt: Date.now() },
    ...filtered,
  ]
  try {
    localStorage.setItem(HISTORY_KEY, JSON.stringify(next.slice(0, MAX_HISTORY)))
  } catch {
    return
  }
  notifyLibraryChanged()
}
