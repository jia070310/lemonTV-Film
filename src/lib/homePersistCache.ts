import type { Movie } from '@/data/mockData'

const HERO_KEY = 'lemonTv.home.hero.v1'
const gridKey = (categoryIndex: number) => `lemonTv.home.grid.v1.${categoryIndex}`

function isMovieish(x: unknown): x is Movie {
  if (!x || typeof x !== 'object') return false
  const o = x as Record<string, unknown>
  return typeof o.id === 'string' && typeof o.title === 'string'
}

function parseMovieList(raw: string | null): Movie[] | null {
  if (raw == null || raw === '') return null
  try {
    const v = JSON.parse(raw) as unknown
    if (!Array.isArray(v) || !v.every(isMovieish)) return null
    return v as Movie[]
  } catch {
    return null
  }
}

/** 冷启动 / 杀进程后再开 APK：读出上次成功写入的首页海报条 */
export function loadPersistedHeroMovies(): Movie[] | null {
  if (typeof localStorage === 'undefined') return null
  return parseMovieList(localStorage.getItem(HERO_KEY))
}

export function persistHeroMovies(movies: Movie[]): void {
  if (typeof localStorage === 'undefined') return
  try {
    localStorage.setItem(HERO_KEY, JSON.stringify(movies))
  } catch {
    // 配额满或隐私模式：忽略
  }
}

export function loadPersistedGridMovies(categoryIndex: number): Movie[] | null {
  if (typeof localStorage === 'undefined') return null
  return parseMovieList(localStorage.getItem(gridKey(categoryIndex)))
}

export function persistGridMovies(categoryIndex: number, movies: Movie[]): void {
  if (typeof localStorage === 'undefined') return
  try {
    localStorage.setItem(gridKey(categoryIndex), JSON.stringify(movies))
  } catch {
    // ignore
  }
}
