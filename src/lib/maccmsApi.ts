import {
  MACCMS_FILTER_FETCH_PAGE_SIZE,
  MACCMS_HERO_RECOMMEND_LEVEL,
  MACCMS_HOME_GRID_LIMIT,
  MACCMS_HOME_HERO_COUNT,
  absoluteMaccmsAssetUrl,
  maccmsApiUrl,
} from '@/config/maccms'
import { maccmsRequestJson } from '@/lib/maccmsHttp'
import type { HomeNavCategory } from '@/data/maccmsTaxonomy'
import { TYPE_IDS_BY_HOME_CATEGORY, areaMatchesFilter, classMatchesPlot, langMatchesFilter } from '@/data/maccmsTaxonomy'
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
 */
async function fetchVodRowsByDetailIds(ids: string[]): Promise<Map<string, MaccmsVodRow>> {
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
 * 首页卡片区：`ac=list` 轻量分页筛出当前分类最新若干条（不按推荐），
 * 再对最终条目的 `vod_id` 调用 `ac=detail&ids=` 补全海报/略缩图，避免列表接口图字段异常或 WebView 加载失败后被统一替换成同一张占位图。
 */
export async function fetchHomeLatestForCategory(cat: HomeNavCategory): Promise<Movie[]> {
  const allow = new Set(TYPE_IDS_BY_HOME_CATEGORY[cat])
  const out: MaccmsVodRow[] = []
  const seen = new Set<string>()
  let pg = 1
  const pagesize = 100
  const maxPages = 40

  while (out.length < MACCMS_HOME_GRID_LIMIT && pg <= maxPages) {
    const raw = await fetchProvideVod({
      ac: 'list',
      pg,
      pagesize,
      sort_direction: 'desc',
    })
    const list = raw.list ?? []
    for (const r of list) {
      if (!allow.has(Number(r.type_id))) continue
      const id = String(r.vod_id)
      if (seen.has(id)) continue
      seen.add(id)
      out.push(r)
      if (out.length >= MACCMS_HOME_GRID_LIMIT) break
    }
    if (list.length < pagesize) break
    pg += 1
  }

  const ids = out.map(r => String(r.vod_id))
  const detailMap = await fetchVodRowsByDetailIds(ids)
  return out.map(r => {
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

function rowMatchesFilters(row: MaccmsVodRow, f: MaccmsFilterState): boolean {
  const tid = Number(row.type_id)
  if (!f.typeIds.includes(tid)) return false
  if (f.year !== '全部' && String(row.vod_year || '').trim() !== f.year) return false
  if (!areaMatchesFilter(String(row.vod_area || ''), f.area)) return false
  if (!langMatchesFilter(String(row.vod_lang || ''), f.lang)) return false
  if (!classMatchesPlot(String(row.vod_class || ''), f.plot)) return false
  return true
}

/**
 * 测试阶段：按页从接口拉取 videolist，在客户端做类型/剧情/地区等过滤，再切片分页。
 * 数据量大时请改为服务端条件查询或专用接口。
 */
export async function fetchFilteredVodPage(
  f: MaccmsFilterState,
  page: number,
  pageSize: number
): Promise<{ rows: Movie[]; total: number; poolPages: number }> {
  const need = page * pageSize
  const pool: MaccmsVodRow[] = []
  let pg = 1
  const maxPages = 30

  while (pool.length < need + pageSize && pg <= maxPages) {
    const raw = await fetchProvideVod({
      ac: 'videolist',
      pg,
      pagesize: MACCMS_FILTER_FETCH_PAGE_SIZE,
      sort_direction: 'desc',
    })
    const batch = raw.list ?? []
    for (const row of batch) {
      if (rowMatchesFilters(row, f)) pool.push(row)
    }
    if (batch.length < MACCMS_FILTER_FETCH_PAGE_SIZE) break
    pg += 1
  }

  const sorted = sortFiltered(pool, f.sort)
  const total = sorted.length
  const start = (page - 1) * pageSize
  const slice = sorted.slice(start, start + pageSize).map(mapVodRowToMovie)
  return { rows: slice, total, poolPages: pg }
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
