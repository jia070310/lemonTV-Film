import type { Movie } from '@/data/mockData'

/** 离开首页再返回时复用数据，避免整页「加载中」直到接口再次返回 */
const TTL_MS = 5 * 60 * 1000

type Entry<T> = { data: T; at: number }

let heroEntry: Entry<Movie[]> | null = null
const gridByCategoryIndex = new Map<number, Entry<Movie[]>>()

function fresh(at: number): boolean {
  return Date.now() - at <= TTL_MS
}

export function readHeroSessionCache(): Movie[] | null {
  if (!heroEntry || !fresh(heroEntry.at)) return null
  return heroEntry.data
}

export function writeHeroSessionCache(movies: Movie[]): void {
  heroEntry = { data: movies, at: Date.now() }
}

export function readGridSessionCache(categoryIndex: number): Movie[] | null {
  const e = gridByCategoryIndex.get(categoryIndex)
  if (!e || !fresh(e.at)) return null
  return e.data
}

export function writeGridSessionCache(categoryIndex: number, movies: Movie[]): void {
  gridByCategoryIndex.set(categoryIndex, { data: movies, at: Date.now() })
}
