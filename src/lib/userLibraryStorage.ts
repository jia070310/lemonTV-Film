import type { Movie } from '@/data/mockData'

export const LIBRARY_CHANGED_EVENT = 'lemon-library-changed'

const FAVORITES_KEY = 'lemonTv.library.favorites.v1'
const HISTORY_KEY = 'lemonTv.library.history.v1'
const MAX_HISTORY = 120

export type WatchHistoryItem = {
  movie: Movie
  /** 0–100，当前集内观看进度（海报条展示） */
  progress: number
  updatedAt: number
  /** 上次播放线路索引，与播放页 `src` 一致 */
  sourceIndex?: number
  /** 上次观看集数（1-based），与播放页 `ep` 一致 */
  episodeIndex?: number
}

/** 从播放记录进入全屏播放并续播（秒级进度由 `playbackProgressStorage`） */
export function resumePlayerHref(
  movieId: string,
  opts?: { sourceIndex?: number; episodeIndex?: number }
): string {
  const ep =
    opts?.episodeIndex != null && Number.isFinite(opts.episodeIndex) && opts.episodeIndex >= 1
      ? Math.floor(opts.episodeIndex)
      : 1
  const src =
    opts?.sourceIndex != null && Number.isFinite(opts.sourceIndex) && opts.sourceIndex >= 0
      ? Math.floor(opts.sourceIndex)
      : 0
  const sp = new URLSearchParams()
  sp.set('ep', String(ep))
  sp.set('src', String(src))
  return `/player/${movieId}?${sp.toString()}`
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
      const sourceIndex =
        typeof o.sourceIndex === 'number' && Number.isFinite(o.sourceIndex) && o.sourceIndex >= 0
          ? Math.floor(o.sourceIndex)
          : undefined
      const episodeIndex =
        typeof o.episodeIndex === 'number' && Number.isFinite(o.episodeIndex) && o.episodeIndex >= 1
          ? Math.floor(o.episodeIndex)
          : undefined
      out.push({
        movie: o.movie as Movie,
        progress: Math.min(100, Math.max(0, progress)),
        updatedAt,
        ...(sourceIndex !== undefined ? { sourceIndex } : {}),
        ...(episodeIndex !== undefined ? { episodeIndex } : {}),
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

/** 批量取消收藏（勾选删除） */
export function removeFavoritesByIds(movieIds: string[]): void {
  if (movieIds.length === 0) return
  const drop = new Set(movieIds)
  const list = getFavorites().filter((m) => !drop.has(m.id))
  writeFavorites(list)
}

export function getWatchHistory(): WatchHistoryItem[] {
  if (typeof localStorage === 'undefined') return []
  return parseHistory(localStorage.getItem(HISTORY_KEY)).sort(
    (a, b) => b.updatedAt - a.updatedAt
  )
}

export function upsertWatchHistory(
  movie: Movie,
  progress: number,
  playback?: { sourceIndex: number; episodeIndex: number }
): void {
  if (typeof localStorage === 'undefined') return
  const p = Math.min(100, Math.max(0, progress))
  const list = parseHistory(localStorage.getItem(HISTORY_KEY))
  const existing = list.find((x) => x.movie.id === movie.id)
  const filtered = list.filter((x) => x.movie.id !== movie.id)
  const sourceIndex =
    playback != null
      ? Math.max(0, Math.floor(playback.sourceIndex))
      : existing?.sourceIndex != null
        ? existing.sourceIndex
        : 0
  const episodeIndex =
    playback != null
      ? Math.max(1, Math.floor(playback.episodeIndex))
      : existing?.episodeIndex != null
        ? existing.episodeIndex
        : 1
  const row: WatchHistoryItem = {
    movie,
    progress: p,
    updatedAt: Date.now(),
    sourceIndex,
    episodeIndex,
  }
  const next: WatchHistoryItem[] = [row, ...filtered]
  try {
    localStorage.setItem(HISTORY_KEY, JSON.stringify(next.slice(0, MAX_HISTORY)))
  } catch {
    return
  }
  notifyLibraryChanged()
}

/** 清空播放记录列表（不改动收藏与其它本地数据） */
export function clearWatchHistory(): void {
  if (typeof localStorage === 'undefined') return
  try {
    localStorage.setItem(HISTORY_KEY, JSON.stringify([]))
  } catch {
    return
  }
  notifyLibraryChanged()
}
