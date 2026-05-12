import type { Movie } from '@/data/mockData'
import {
  loadPersistedGridMovies,
  loadPersistedHeroMovies,
  persistGridMovies,
  persistHeroMovies,
} from '@/lib/homePersistCache'

/**
 * 会话内存 + localStorage：杀进程后再开 APK 仍能先展示上次数据，后台静默刷新。
 */
type Entry<T> = { data: T; at: number }

let heroEntry: Entry<Movie[]> | null = null
const gridByCategoryIndex = new Map<number, Entry<Movie[]>>()

export function readHeroSessionCache(): Movie[] | null {
  if (heroEntry) return heroEntry.data
  const fromDisk = loadPersistedHeroMovies()
  if (fromDisk != null) {
    heroEntry = { data: fromDisk, at: Date.now() }
    return fromDisk
  }
  return null
}

export function writeHeroSessionCache(movies: Movie[]): void {
  heroEntry = { data: movies, at: Date.now() }
  persistHeroMovies(movies)
}

export function readGridSessionCache(categoryIndex: number): Movie[] | null {
  const mem = gridByCategoryIndex.get(categoryIndex)
  if (mem) return mem.data
  const fromDisk = loadPersistedGridMovies(categoryIndex)
  if (fromDisk != null) {
    gridByCategoryIndex.set(categoryIndex, { data: fromDisk, at: Date.now() })
    return fromDisk
  }
  return null
}

export function writeGridSessionCache(categoryIndex: number, movies: Movie[]): void {
  gridByCategoryIndex.set(categoryIndex, { data: movies, at: Date.now() })
  persistGridMovies(categoryIndex, movies)
}

/** 用于静默刷新：列表顺序与关键展示字段一致则视为同一条数据，避免无意义 setState */
export function homeHeroStripEqual(a: Movie[], b: Movie[]): boolean {
  if (a === b) return true
  if (a.length !== b.length) return false
  for (let i = 0; i < a.length; i++) {
    const x = a[i]
    const y = b[i]
    if (
      x.id !== y.id ||
      x.title !== y.title ||
      x.poster !== y.poster ||
      (x.backdrop ?? '') !== (y.backdrop ?? '') ||
      x.rating !== y.rating ||
      (x.description ?? '') !== (y.description ?? '') ||
      (x.tag ?? '') !== (y.tag ?? '')
    ) {
      return false
    }
  }
  return true
}

export function homeGridListEqual(a: Movie[], b: Movie[]): boolean {
  if (a === b) return true
  if (a.length !== b.length) return false
  for (let i = 0; i < a.length; i++) {
    const x = a[i]
    const y = b[i]
    if (
      x.id !== y.id ||
      x.title !== y.title ||
      x.poster !== y.poster ||
      x.rating !== y.rating ||
      (x.tag ?? '') !== (y.tag ?? '')
    ) {
      return false
    }
  }
  return true
}
