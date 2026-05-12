import {
  MACCMS_FILTER_FETCH_PAGE_SIZE,
  MACCMS_FILTER_LIST_REQ_MS,
  MACCMS_FILTER_MAX_PAGES_PER_TYPE,
  MACCMS_HERO_RECOMMEND_LEVEL,
  MACCMS_HOME_GRID_LIMIT,
  MACCMS_HOME_HERO_COUNT,
  absoluteMaccmsAssetUrl,
  maccmsApiUrl,
} from '@/config/maccms'
import { maccmsRequestJson } from '@/lib/maccmsHttp'
import type { HomeNavCategory } from '@/data/maccmsTaxonomy'
import {
  getHomeListQueryTypeIds,
  areaMatchesFilter,
  classMatchesPlot,
  langMatchesFilter,
} from '@/data/maccmsTaxonomy'
import type { Movie } from '@/data/mockData'

export type MaccmsProvideListResponse = {
  code: number
  msg?: string
  page?: number
  pagecount?: number
  limit?: number | string
  total?: number
  list?: MaccmsVodRow[]
}

export type MaccmsVodRow = {
  vod_id: number
  vod_name?: string
  type_id?: number
  type_name?: string
  vod_time?: string | number
  vod_pic?: string
  vod_pic_thumb?: string
  vod_pic_slide?: string
  vod_score?: string | number
  vod_year?: string
  vod_area?: string
  vod_lang?: string
  vod_class?: string
  vod_tag?: string
  vod_blurb?: string
  vod_content?: string
  vod_level?: string | number
  vod_hits?: number
  vod_remarks?: string
  vod_play_from?: string
  vod_play_url?: string
  vod_serial?: string
  vod_isend?: number
}

function stripHtml(s: string): string {
  return s.replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim()
}

export function vodTimeToMs(t: MaccmsVodRow['vod_time']): number {
  if (t == null) return 0
  if (typeof t === 'number') return t * (t < 2e10 ? 1000 : 1)
  const ms = Date.parse(String(t).replace(/-/g, '/'))
  return Number.isFinite(ms) ? ms : 0
}

function parseVodScore(raw: MaccmsVodRow['vod_score']): number {
  if (raw == null || raw === '') return 0
  const n = parseFloat(String(raw).replace(/[^\d.]/g, ''))
  if (!Number.isFinite(n) || n <= 0) return 0
  return Math.min(10, n)
}

export function mapVodRowToMovie(row: MaccmsVodRow): Movie {
  const id = String(row.vod_id)
  const title = row.vod_name?.trim() || '未命名'
  const posterRaw =
    row.vod_pic?.trim() ||
    row.vod_pic_thumb?.trim() ||
    row.vod_pic_slide?.trim() ||
    ''
  const poster = absoluteMaccmsAssetUrl(posterRaw)
  const slideRaw = row.vod_pic_slide?.trim()
  const backdrop =
    slideRaw && slideRaw.length > 0 ? absoluteMaccmsAssetUrl(slideRaw) : undefined
  const rating = parseVodScore(row.vod_score)
  const year = (row.vod_year || '').trim() || '—'
  const genre =
    (row.vod_class || '').split(/[,，]/)[0]?.trim() ||
    (row.type_name || '').trim() ||
    '—'
  const area = (row.vod_area || '').trim() || '—'
  const rawDesc = (row.vod_blurb || row.vod_content || '').trim()
  const description = rawDesc ? stripHtml(rawDesc).slice(0, 800) : undefined
  const tag = (row.vod_remarks || row.vod_tag || '').trim() || undefined

  return {
    id,
    title,
    poster,
    backdrop,
    rating,
    year,
    genre,
    area,
    description,
    tag,
  }
}

/**
 * 首页顶部轮播：与 UI 约定一致——
 * - `poster`：竖版/常规封面（`vod_pic` → `vod_pic_thumb`），给未展开条用；
 * - `backdrop`：横版幻灯（`vod_pic_slide`），给当前焦点展开条用。
 * 下方卡片区仍用 {@link mapVodRowToMovie}。
 */
export function mapVodRowToHeroMovie(row: MaccmsVodRow): Movie {
  const base = mapVodRowToMovie(row)
  const vertRaw = row.vod_pic?.trim() || row.vod_pic_thumb?.trim() || ''
  const posterVertical = vertRaw ? absoluteMaccmsAssetUrl(vertRaw) : base.poster
  const slideRaw = row.vod_pic_slide?.trim()
  const backdropHorizontal =
    slideRaw && slideRaw.length > 0 ? absoluteMaccmsAssetUrl(slideRaw) : undefined
  return {
    ...base,
    poster: posterVertical,
    backdrop: backdropHorizontal,
  }
}

function buildProvideVodQuery(params: Record<string, string | number | undefined>): string {
  const sp = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === '') continue
    sp.set(k, String(v))
  }
  return sp.toString()
}

export async function fetchProvideVod(params: Record<string, string | number | undefined>): Promise<MaccmsProvideListResponse> {
  const q = buildProvideVodQuery(params)
  const url = maccmsApiUrl(`/api.php/provide/vod/?${q}`)
  const data = await maccmsRequestJson<MaccmsProvideListResponse>(url)
  if (data.code !== 1) {
    throw new Error(data.msg || 'MACCMS 接口返回异常')
  }
  return data
}

/**
 * 首页海报：固定条数，仅 `ac=videolist` 中含 `vod_level === N` 的数据（后台推荐 9），
 * 按 `vod_time` 新→旧排序；图片用 {@link mapVodRowToHeroMovie} 优先横版 `vod_pic_slide`。
 * 与下方「最新列表」数据源、逻辑完全分离。
 */
export async function fetchHomeHeroMovies(
  heroCount: number = MACCMS_HOME_HERO_COUNT
): Promise<Movie[]> {
  const level = MACCMS_HERO_RECOMMEND_LEVEL
  const picked: MaccmsVodRow[] = []
  const seen = new Set<string>()
  let pg = 1
  const pagesize = 100
  const maxPages = 50

  while (picked.length < heroCount && pg <= maxPages) {
    const raw = await fetchProvideVod({
      ac: 'videolist',
      pg,
      pagesize,
      sort_direction: 'desc',
    })
    const list = raw.list ?? []
    for (const r of list) {
      if (Number(r.vod_level) !== level) continue
      const id = String(r.vod_id)
      if (seen.has(id)) continue
      seen.add(id)
      picked.push(r)
      if (picked.length >= heroCount) break
    }
    if (list.length < pagesize) break
    pg += 1
  }

  picked.sort((a, b) => vodTimeToMs(b.vod_time) - vodTimeToMs(a.vod_time))
  return picked.slice(0, heroCount).map(mapVodRowToHeroMovie)
}

/**
 * 批量拉详情（含完整海报字段），避免首页卡片区整页 videolist 体积过大。
 * 筛选页等对 `ac=list` 结果补封面时也可复用。
 */
export async function fetchVodRowsByDetailIds(ids: string[]): Promise<Map<string, MaccmsVodRow>> {
  const map = new Map<string, MaccmsVodRow>()
  if (ids.length === 0) return map
  const chunkSize = 18
  for (let i = 0; i < ids.length; i += chunkSize) {
    const chunk = ids.slice(i, i + chunkSize).filter(Boolean)
    if (chunk.length === 0) continue
    const raw = await fetchProvideVod({
      ac: 'detail',
      ids: chunk.join(','),
    })
    for (const r of raw.list ?? []) {
      map.set(String(r.vod_id), r)
    }
  }
  return map
}

/**
 * 首页卡片区：按当前主类下各子类 `type_id` 分别请求 `ac=list&t=子类ID`（与 CMS 一致），
 * 合并去重后按 `vod_time` 取最新若干条；避免「全站 list 前几页全是电影/电视剧」导致综艺等永远筛不到。
 * 再对 `vod_id` 批量 `ac=detail` 补全海报。
 */
export async function fetchHomeLatestForCategory(cat: HomeNavCategory): Promise<Movie[]> {
  const typeIds = getHomeListQueryTypeIds(cat)
  const allow = new Set(typeIds)
  const limit = MACCMS_HOME_GRID_LIMIT
  const pagesize = Math.min(
    100,
    Math.max(limit, Math.ceil(limit / Math.max(1, typeIds.length)) + 8)
  )

  const seen = new Set<string>()
  const pool: MaccmsVodRow[] = []

  const lists = await Promise.all(
    typeIds.map(async (tid) => {
      try {
        const raw = await fetchProvideVod({
          ac: 'list',
          t: tid,
          pg: 1,
          pagesize,
          sort_direction: 'desc',
        })
        return raw.list ?? []
      } catch {
        return []
      }
    })
  )

  for (const list of lists) {
    for (const r of list) {
      if (!allow.has(Number(r.type_id))) continue
      const id = String(r.vod_id)
      if (seen.has(id)) continue
      seen.add(id)
      pool.push(r)
    }
  }

  pool.sort((a, b) => vodTimeToMs(b.vod_time) - vodTimeToMs(a.vod_time))
  const out = pool.slice(0, limit)

  const ids = out.map((r) => String(r.vod_id))
  const detailMap = await fetchVodRowsByDetailIds(ids)
  return out.map((r) => {
    const full = detailMap.get(String(r.vod_id))
    return mapVodRowToMovie(full ?? r)
  })
}

export type MaccmsFilterState = {
  homeCategory: HomeNavCategory
  typeLabel: string
  plot: string
  area: string
  lang: string
  year: string
  sort: string
  /** 允许的 type_id，已由「类型」解析 */
  typeIds: number[]
}

function sortFiltered(rows: MaccmsVodRow[], sort: string): MaccmsVodRow[] {
  const next = [...rows]
  if (sort === '人气排序') {
    next.sort((a, b) => (b.vod_hits || 0) - (a.vod_hits || 0))
  } else if (sort === '评分排序') {
    next.sort((a, b) => Number(b.vod_score || 0) - Number(a.vod_score || 0))
  } else {
    next.sort((a, b) => vodTimeToMs(b.vod_time) - vodTimeToMs(a.vod_time))
  }
  return next
}

/**
 * 列表接口 `ac=list` 常为瘦身字段，缺年份/地区/语言/剧情类时不能硬判为不匹配，
 * 否则除「类型」外任一筛选都会导致全库被滤成 0 条。
 * 有值则按条件匹配；无值则跳过该维度（与详情不一致的少量误收可接受）。
 */
function rowMatchesFilters(row: MaccmsVodRow, f: MaccmsFilterState): boolean {
  const tid = Number(row.type_id)
  if (!f.typeIds.includes(tid)) return false

  if (f.year !== '全部') {
    const y = String(row.vod_year || '').trim()
    if (y && y !== f.year) return false
  }

  if (f.area !== '全部') {
    const a = String(row.vod_area || '').trim()
    if (a && !areaMatchesFilter(a, f.area)) return false
  }

  if (f.lang !== '全部') {
    const lang = String(row.vod_lang || '').trim()
    if (lang && !langMatchesFilter(lang, f.lang)) return false
  }

  if (f.plot !== '全部') {
    const cls = String(row.vod_class || '').replace(/\s/g, '')
    if (cls && !classMatchesPlot(cls, f.plot)) return false
  }

  return true
}

function withRequestTimeout<T>(p: Promise<T>, ms: number): Promise<T> {
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error('timeout')), ms)
    p.then(
      (v) => {
        clearTimeout(t)
        resolve(v)
      },
      (e) => {
        clearTimeout(t)
        reject(e)
      }
    )
  })
}

async function fetchListPageTimed(
  tid: number,
  pg: number,
  pagesize: number
): Promise<MaccmsProvideListResponse> {
  return withRequestTimeout(
    fetchProvideVod({
      ac: 'list',
      t: tid,
      pg,
      pagesize,
      sort_direction: 'desc',
    }),
    MACCMS_FILTER_LIST_REQ_MS
  )
}

export type FilterCatalogContinuation = {
  pool: MaccmsVodRow[]
  seen: Set<string>
  exhausted: Map<number, boolean>
  /** 下一波要请求的 list 页码（各子类同页） */
  nextPg: number
  /** 各子类首波接口里 `total` 之和（常见字段；无则保持 0） */
  apiTotalSum: number
  totalsCaptured: boolean
}

export type FilterCatalogPartial = {
  sorted: MaccmsVodRow[]
  apiTotalSum: number
  truncated: boolean
  exhaustedAll: boolean
  done: boolean
}

export function createEmptyFilterContinuation(
  f: MaccmsFilterState
): FilterCatalogContinuation {
  const exhausted = new Map<number, boolean>()
  for (const tid of f.typeIds) exhausted.set(tid, false)
  return {
    pool: [],
    seen: new Set(),
    exhausted,
    nextPg: 1,
    apiTotalSum: 0,
    totalsCaptured: false,
  }
}

/**
 * 按波次合并 `ac=list`，本地过滤排序；达到 `targetSortedCount` 后可暂停，用 {@link FilterCatalogContinuation} 续拉。
 * 首波会为各子类累加接口返回的 `total` 作为条数提示（子类互斥时相加接近 CMS 总量）。
 */
export async function advanceFilterCatalog(
  f: MaccmsFilterState,
  cont: FilterCatalogContinuation | null,
  targetSortedCount: number,
  opts?: {
    signal?: AbortSignal
    onPartial?: (p: FilterCatalogPartial) => void
  }
): Promise<{
  continuation: FilterCatalogContinuation
  sorted: MaccmsVodRow[]
  apiTotalSum: number
  truncated: boolean
  exhaustedAll: boolean
}> {
  const state = cont ?? createEmptyFilterContinuation(f)
  const allow = new Set(f.typeIds)
  if (allow.size === 0) {
    const empty = createEmptyFilterContinuation(f)
    opts?.onPartial?.({
      sorted: [],
      apiTotalSum: 0,
      truncated: false,
      exhaustedAll: true,
      done: true,
    })
    return {
      continuation: empty,
      sorted: [],
      apiTotalSum: 0,
      truncated: false,
      exhaustedAll: true,
    }
  }

  const pagesize = MACCMS_FILTER_FETCH_PAGE_SIZE
  const maxPg = MACCMS_FILTER_MAX_PAGES_PER_TYPE
  let truncated = false
  const { pool, seen, exhausted } = state

  const mergeBatch = (batch: MaccmsVodRow[]) => {
    for (const row of batch) {
      const tid = Number(row.type_id)
      if (!allow.has(tid)) continue
      const id = String(row.vod_id)
      if (seen.has(id)) continue
      if (!rowMatchesFilters(row, f)) continue
      seen.add(id)
      pool.push(row)
    }
  }

  const emit = (done: boolean, exhaustedAllFlag: boolean) => {
    const sorted = sortFiltered(pool, f.sort)
    opts?.onPartial?.({
      sorted,
      apiTotalSum: state.apiTotalSum,
      truncated: done ? truncated : false,
      exhaustedAll: exhaustedAllFlag,
      done,
    })
  }

  const finish = (exhaustedAllFlag: boolean) => {
    const sorted = sortFiltered(pool, f.sort)
    emit(true, exhaustedAllFlag)
    return {
      continuation: state,
      sorted,
      apiTotalSum: state.apiTotalSum,
      truncated,
      exhaustedAll: exhaustedAllFlag,
    }
  }

  for (let pg = state.nextPg; pg <= maxPg; pg++) {
    if (opts?.signal?.aborted) {
      state.nextPg = pg
      return finish(f.typeIds.every((t) => exhausted.get(t)))
    }

    const activeTypes = f.typeIds.filter((tid) => !exhausted.get(tid))
    if (activeTypes.length === 0) {
      state.nextPg = pg
      return finish(true)
    }

    const settled = await Promise.allSettled(
      activeTypes.map((tid) => fetchListPageTimed(tid, pg, pagesize))
    )

    if (!state.totalsCaptured) {
      for (let i = 0; i < activeTypes.length; i++) {
        const r = settled[i]
        if (r.status !== 'fulfilled') continue
        const raw = r.value
        if (raw.code !== 1) continue
        const tv = Number(raw.total)
        if (Number.isFinite(tv) && tv > 0) state.apiTotalSum += tv
      }
      state.totalsCaptured = true
    }

    for (let i = 0; i < activeTypes.length; i++) {
      const tid = activeTypes[i]
      const r = settled[i]
      if (r.status !== 'fulfilled') {
        exhausted.set(tid, true)
        continue
      }
      const raw = r.value
      if (raw.code !== 1) {
        exhausted.set(tid, true)
        continue
      }
      const batch = raw.list ?? []
      mergeBatch(batch)
      if (batch.length < pagesize) exhausted.set(tid, true)
    }

    state.nextPg = pg + 1

    const sorted = sortFiltered(pool, f.sort)
    emit(false, false)

    if (sorted.length >= targetSortedCount) {
      return finish(f.typeIds.every((t) => exhausted.get(t)))
    }

    if (f.typeIds.every((t) => exhausted.get(t))) {
      return finish(true)
    }
  }

  truncated = f.typeIds.some((t) => !exhausted.get(t))
  return finish(f.typeIds.every((t) => exhausted.get(t)))
}

/**
 * 一次拉满（续传直到无更多或达上限）；筛选页优先用 {@link advanceFilterCatalog} 分段加载。
 */
export async function fetchFilteredCatalog(
  f: MaccmsFilterState,
  opts?: {
    signal?: AbortSignal
    onPartial?: (rows: Movie[], info: { truncated: boolean; done: boolean }) => void
  }
): Promise<{ rows: Movie[]; total: number; truncated: boolean }> {
  const r = await advanceFilterCatalog(f, null, Number.MAX_SAFE_INTEGER, {
    signal: opts?.signal,
    onPartial: opts?.onPartial
      ? (p) =>
          opts.onPartial!(p.sorted.map(mapVodRowToMovie), {
            truncated: p.truncated,
            done: p.done,
          })
      : undefined,
  })
  const rows = r.sorted.map(mapVodRowToMovie)
  const total = r.apiTotalSum > 0 ? r.apiTotalSum : r.sorted.length
  return { rows, total, truncated: r.truncated }
}

export async function fetchVodDetailById(vodId: string): Promise<MaccmsVodRow | null> {
  const raw = await fetchProvideVod({
    ac: 'detail',
    ids: vodId,
  })
  const row = raw.list?.[0]
  return row ?? null
}

export type ParsedPlayEpisode = { label: string; url: string }

/** 取第一个播放分组下的分集链接 */
export function parseEpisodesFromPlayUrl(vodPlayUrl: string): ParsedPlayEpisode[] {
  if (!vodPlayUrl) return []
  const firstGroup = vodPlayUrl.split('$$$')[0] || ''
  const chunks = firstGroup.split('#').filter(Boolean)
  const out: ParsedPlayEpisode[] = []
  for (const ch of chunks) {
    const dollar = ch.indexOf('$')
    if (dollar === -1) {
      if (ch.startsWith('http')) out.push({ label: '正片', url: ch })
      continue
    }
    const label = ch.slice(0, dollar).trim() || '正片'
    const url = ch.slice(dollar + 1).trim()
    if (url) out.push({ label, url })
  }
  return out
}

/** 多播放组：vod_play_from 与 vod_play_url 以 $$$ 对齐 */
export function parsePlaySources(row: MaccmsVodRow): {
  name: string
  episodes: ParsedPlayEpisode[]
}[] {
  const fromRaw = (row.vod_play_from || '').trim()
  const urlRaw = (row.vod_play_url || '').trim()
  if (!urlRaw) return []
  const froms = fromRaw ? fromRaw.split('$$$') : []
  const urls = urlRaw.split('$$$')
  const n = Math.max(urls.length, froms.length, 1)
  const bundles: { name: string; episodes: ParsedPlayEpisode[] }[] = []
  for (let i = 0; i < n; i++) {
    const name = (froms[i] || `线路${i + 1}`).trim() || `线路${i + 1}`
    let episodes = parseEpisodesFromPlayUrl(urls[i] || '')
    if (episodes.length === 0 && urls[i]?.trim().startsWith('http')) {
      episodes = [{ label: '正片', url: urls[i].trim() }]
    }
    if (episodes.some(e => e.url)) bundles.push({ name, episodes })
  }
  return bundles
}

export function enrichMovieFromDetailRow(base: Movie, row: MaccmsVodRow): Movie {
  const content = (row.vod_content || '').trim()
  const description = content ? stripHtml(content).slice(0, 2000) : base.description
  const slide = row.vod_pic_slide?.trim()
  const tag = (row.vod_remarks || row.vod_tag || '').trim() || base.tag
  const bundles = parsePlaySources(row)
  const epCount = bundles[0]?.episodes.length ?? 0
  const serial = String(row.vod_serial || '').trim()
  let airedEpisodes: number | undefined
  if (serial && /^\d+$/.test(serial) && epCount > 0) {
    const n = parseInt(serial, 10)
    if (n >= 1) airedEpisodes = Math.min(n, epCount)
  }
  return {
    ...base,
    description,
    backdrop:
      slide && slide.length > 0 ? absoluteMaccmsAssetUrl(slide) : base.backdrop,
    tag,
    episodes: epCount > 1 ? epCount : base.episodes,
    airedEpisodes,
  }
}
