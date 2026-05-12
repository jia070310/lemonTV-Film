import { useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { useTvSpatialMainEntry, useTvSpatialNode } from '@/tv/spatial'
import { queryFocusableSpatial } from '@/tv/spatial/spatialFocus'
import { PosterCard } from '@/components/PosterCard'
import { gridDownNeighborIndex } from '@/lib/gridSpatialNav'
import {
  FILTER_TYPE_TO_IDS,
  MACCMS_FILTER_KEYS,
  getFilterLabels,
  getFilterOptionsForCategory,
  getHomeListQueryTypeIds,
  parseHomeCategory,
  type MaccmsFilterKey,
} from '@/data/maccmsTaxonomy'
import type { Movie } from '@/data/mockData'
import type {
  FilterCatalogContinuation,
  MaccmsVodRow,
} from '@/lib/maccmsApi'
import {
  advanceFilterCatalog,
  fetchVodRowsByDetailIds,
  filterListTotalUsesClientCount,
  mapVodRowToMovie,
} from '@/lib/maccmsApi'
import { ChevronLeft, ChevronRight, Info, X } from 'lucide-react'

const FILTER_COLS = 5
const FILTER_OPT_COLS = 3
const FILTER_PAGE_SIZE = 20
/** 首次进入筛选：预合并条数（页） */
const FILTER_INITIAL_PREFETCH_PAGES = 5
/** 接近已加载末尾时，每次多合并的页数 */
const FILTER_CHUNK_PREFETCH_PAGES = 3

/** 与 Layout.tsx 中 navItems 顺序一致：电视剧=1、电影=2、综艺=3、动漫=4 */
const FILTER_CATEGORY_TO_NAV_INDEX: Record<string, number> = {
  电视剧: 1,
  电影: 2,
  综艺: 3,
  动漫: 4,
}

function FilterSidebarRow({
  index,
  fk,
  activeFilter,
  filters,
  filterLabels,
  toggleFilter,
  navLeftId,
}: {
  index: number
  fk: MaccmsFilterKey
  activeFilter: MaccmsFilterKey | null
  filters: Record<MaccmsFilterKey, string>
  filterLabels: Record<MaccmsFilterKey, string>
  toggleFilter: (key: MaccmsFilterKey) => void
  /** 左侧全局分类栏 spatial id，例如 nav-1 */
  navLeftId: string
}) {
  const spatial = useTvSpatialNode(
    `filter-sb-${index}`,
    () => ({
      left: navLeftId,
      up: index > 0 ? `filter-sb-${index - 1}` : undefined,
      down:
        index < MACCMS_FILTER_KEYS.length - 1 ? `filter-sb-${index + 1}` : undefined,
      right:
        activeFilter === fk
          ? `filter-opt-${fk}-0`
          : 'filter-grid-0',
    }),
    [index, activeFilter, fk, navLeftId]
  )

  return (
    <button
      type="button"
      {...spatial}
      className={cn(
        'filter-sidebar-btn tv-focusable w-full min-h-[3.25rem] flex flex-col items-start justify-center gap-0.5 rounded-lg px-2 py-3 text-left transition-[box-shadow,background-color,color,border-color] duration-150 ease-out',
        activeFilter === fk
          ? 'tv-tab-selected'
          : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
      )}
      onClick={() => toggleFilter(fk)}
    >
      <span className="text-xs font-medium leading-snug line-clamp-2 break-all">
        {filters[fk]}
      </span>
      <span
        className={cn(
          'text-[10px] leading-tight',
          activeFilter === fk ? 'text-white/70' : 'text-muted-foreground'
        )}
      >
        {filterLabels[fk]}
      </span>
    </button>
  )
}

function FilterModalClose({
  activeFilter,
  optionsLen,
  onClose,
}: {
  activeFilter: MaccmsFilterKey
  optionsLen: number
  onClose: () => void
}) {
  /** 与选项网格首行最右格相连，避免左键焦点落到侧栏跑出弹窗 */
  const firstRowRightIdx = Math.min(FILTER_OPT_COLS - 1, Math.max(0, optionsLen - 1))
  const spatial = useTvSpatialNode(
    'filter-modal-close',
    () => ({
      left: `filter-opt-${activeFilter}-${firstRowRightIdx}`,
      down: `filter-opt-${activeFilter}-0`,
    }),
    [activeFilter, firstRowRightIdx]
  )

  return (
    <button
      type="button"
      {...spatial}
      className="tv-focusable tab-focus flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-secondary text-secondary-foreground transition-colors hover:bg-surface-hover"
      onClick={onClose}
      aria-label="关闭"
    >
      <X size={20} className="text-foreground" strokeWidth={2} />
    </button>
  )
}

function FilterOverlayOption({
  activeFilter,
  opt,
  optIndex,
  optionsLen,
  filters,
  selectFilter,
}: {
  activeFilter: MaccmsFilterKey
  opt: string
  optIndex: number
  optionsLen: number
  filters: Record<MaccmsFilterKey, string>
  selectFilter: (key: MaccmsFilterKey, value: string) => void
}) {
  const row = Math.floor(optIndex / FILTER_OPT_COLS)
  const col = optIndex % FILTER_OPT_COLS
  const idPrefix = `filter-opt-${activeFilter}`
  const rowEndIdx = Math.min(row * FILTER_OPT_COLS + FILTER_OPT_COLS - 1, optionsLen - 1)
  const rowStartIdx = row * FILTER_OPT_COLS
  const downWrapId = `${idPrefix}-${col}`
  const downNeighbor =
    optIndex + FILTER_OPT_COLS < optionsLen
      ? `${idPrefix}-${optIndex + FILTER_OPT_COLS}`
      : downWrapId === `${idPrefix}-${optIndex}`
        ? 'filter-modal-close'
        : downWrapId

  const spatial = useTvSpatialNode(
    `${idPrefix}-${optIndex}`,
    () => ({
      left:
        col === 0 ? `${idPrefix}-${rowEndIdx}` : `${idPrefix}-${optIndex - 1}`,
      right:
        col < FILTER_OPT_COLS - 1 && optIndex + 1 < optionsLen
          ? `${idPrefix}-${optIndex + 1}`
          : `${idPrefix}-${rowStartIdx}`,
      up:
        row === 0
          ? 'filter-modal-close'
          : `${idPrefix}-${optIndex - FILTER_OPT_COLS}`,
      down: downNeighbor,
    }),
    [activeFilter, optIndex, optionsLen, row, col, rowEndIdx, rowStartIdx, downNeighbor]
  )

  return (
    <button
      type="button"
      {...spatial}
      className={cn(
        'tv-focusable tab-focus py-2.5 px-2 rounded-lg text-sm font-medium transition-all duration-200 text-center',
        filters[activeFilter] === opt
          ? 'tv-tab-selected'
          : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
      )}
      onClick={() => selectFilter(activeFilter, opt)}
    >
      {opt}
    </button>
  )
}

function FilterGridCell({
  index,
  movie,
  total,
  footerDownId,
}: {
  index: number
  movie: Movie
  total: number
  /** 最后一行按下进入页脚的首个可聚焦节点（上一页禁用时不能指向 filter-pg-prev） */
  footerDownId: string
}) {
  const row = Math.floor(index / FILTER_COLS)
  const col = index % FILTER_COLS
  const lastRowStart = Math.floor((total - 1) / FILTER_COLS) * FILTER_COLS
  const onLastRow = index >= lastRowStart

  const spatial = useTvSpatialNode(
    `filter-grid-${index}`,
    () => {
      const downIdx = gridDownNeighborIndex(index, total, FILTER_COLS)
      return {
      /* 与详情页卡片区一致：网格顶行再上键不离开网格（用左键回筛选侧栏） */
      up:
        row === 0
          ? undefined
          : `filter-grid-${index - FILTER_COLS}`,
      down:
        downIdx !== undefined
          ? `filter-grid-${downIdx}`
          : onLastRow
            ? footerDownId
            : undefined,
      left: col === 0 ? 'filter-sb-0' : `filter-grid-${index - 1}`,
      right:
        col < FILTER_COLS - 1 && index + 1 < total
          ? `filter-grid-${index + 1}`
          : undefined,
      }
    },
    [index, total, footerDownId, onLastRow, row, col]
  )

  return (
    <div className="scroll-my-10 rounded-lg outline-none [&_h3]:text-base [&_p]:text-[13px]">
      <PosterCard
        movie={movie}
        size="lg"
        focusable={false}
        posterShellProps={{ ...spatial }}
        className="w-full"
      />
    </div>
  )
}

function FilterPgPrev({
  lastRowStart,
  disabled,
  onPrev,
  firstPagerSpatialId,
}: {
  lastRowStart: number
  disabled: boolean
  onPrev: () => void
  /** 当前窗口内第一个页码按钮的 spatial id，例如 filter-pg-3 */
  firstPagerSpatialId: string
}) {
  const spatial = useTvSpatialNode(
    'filter-pg-prev',
    () => ({
      up: `filter-grid-${lastRowStart}`,
      right: firstPagerSpatialId,
    }),
    [lastRowStart, firstPagerSpatialId]
  )

  return (
    <button
      type="button"
      {...spatial}
      className="tv-focusable pill-focus w-10 h-10 flex items-center justify-center rounded-full bg-secondary disabled:opacity-40"
      disabled={disabled}
      onClick={onPrev}
    >
      <ChevronLeft size={20} className="text-foreground" />
    </button>
  )
}

function FilterPgNum({
  page,
  windowPages,
  windowIndex,
  currentPage,
  totalPages,
  lastRowStart,
  setPage,
}: {
  page: number
  /** 当前分页器上可见的页码（滑动窗口），用于 TV 左右邻接 */
  windowPages: number[]
  windowIndex: number
  currentPage: number
  totalPages: number
  lastRowStart: number
  setPage: (p: number) => void
}) {
  const spatial = useTvSpatialNode(
    `filter-pg-${page}`,
    () => ({
      up: `filter-grid-${lastRowStart}`,
      left:
        windowIndex === 0
          ? page > 1
            ? 'filter-pg-prev'
            : undefined
          : `filter-pg-${windowPages[windowIndex - 1]}`,
      right:
        windowIndex === windowPages.length - 1
          ? page < totalPages
            ? 'filter-pg-next'
            : undefined
          : `filter-pg-${windowPages[windowIndex + 1]}`,
    }),
    [page, totalPages, lastRowStart, currentPage, windowPages, windowIndex]
  )

  return (
    <button
      type="button"
      {...spatial}
      className={cn(
        'tv-focusable tab-focus w-10 h-10 rounded-xl flex items-center justify-center text-sm font-medium transition-all',
        page === currentPage
          ? 'tv-tab-selected'
          : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
      )}
      onClick={() => setPage(page)}
    >
      {page}
    </button>
  )
}

function FilterPgNext({
  lastRowStart,
  disabled,
  onNext,
  lastPagerSpatialId,
}: {
  lastRowStart: number
  disabled: boolean
  onNext: () => void
  lastPagerSpatialId: string
}) {
  const spatial = useTvSpatialNode(
    'filter-pg-next',
    () => ({
      up: `filter-grid-${lastRowStart}`,
      left: lastPagerSpatialId,
    }),
    [lastRowStart, lastPagerSpatialId]
  )

  return (
    <button
      type="button"
      {...spatial}
      className="tv-focusable pill-focus w-10 h-10 flex items-center justify-center rounded-full bg-secondary disabled:opacity-40"
      disabled={disabled}
      onClick={onNext}
    >
      <ChevronRight size={20} className="text-foreground" />
    </button>
  )
}

export function FilterPage() {
  const [searchParams] = useSearchParams()
  const categoryParam = searchParams.get('category') || ''
  const homeCat = useMemo(
    () => parseHomeCategory(categoryParam || null),
    [categoryParam]
  )

  const filterNavLeftId =
    categoryParam && FILTER_CATEGORY_TO_NAV_INDEX[categoryParam] != null
      ? `nav-${FILTER_CATEGORY_TO_NAV_INDEX[categoryParam]}`
      : 'nav-1'

  const filterModalData = useMemo(
    () => getFilterOptionsForCategory(homeCat),
    [homeCat]
  )

  const defaultFilters = useMemo(
    (): Record<MaccmsFilterKey, string> => ({
      type: '全部',
      plot: '全部',
      area: '全部',
      lang: '全部',
      year: '全部',
      sort: '时间排序',
    }),
    []
  )

  const [filters, setFilters] = useState<Record<MaccmsFilterKey, string>>(defaultFilters)
  const [activeFilter, setActiveFilter] = useState<MaccmsFilterKey | null>(null)
  const [currentPage, setCurrentPage] = useState(1)
  const [catalogRows, setCatalogRows] = useState<MaccmsVodRow[]>([])
  const [displayTotal, setDisplayTotal] = useState(0)
  const [catalogTruncated, setCatalogTruncated] = useState(false)
  const [catalogExhausted, setCatalogExhausted] = useState(false)
  const [listLoading, setListLoading] = useState(false)
  const [listError, setListError] = useState<string | null>(null)
  const loadReqGen = useRef(0)
  const filterContRef = useRef<FilterCatalogContinuation | null>(null)
  /** `ac=list` 常无封面，用 `ac=detail` 按当前可见条补全后叠加上去 */
  const [detailOverlay, setDetailOverlay] = useState<Record<string, Movie>>({})

  /** 剧情/地区/语言：官方采集接口不把这三项计入 SQL，`total` 不可用，条数随本地筛选结果走 */
  const listTotalUsesClientFilter = useMemo(
    () =>
      filterListTotalUsesClientCount({
        plot: filters.plot,
        area: filters.area,
        lang: filters.lang,
      }),
    [filters.plot, filters.area, filters.lang]
  )

  useEffect(() => {
    setFilters(defaultFilters)
    setCurrentPage(1)
  }, [homeCat, defaultFilters])

  useEffect(() => {
    const id = ++loadReqGen.current
    const ac = new AbortController()
    setListLoading(true)
    setListError(null)
    setDetailOverlay({})
    filterContRef.current = null
    setCatalogRows([])
    setDisplayTotal(0)
    setCatalogExhausted(false)
    setCatalogTruncated(false)

    const typeIds =
      filters.type === '全部'
        ? getHomeListQueryTypeIds(homeCat)
        : FILTER_TYPE_TO_IDS[homeCat][filters.type] ??
          FILTER_TYPE_TO_IDS[homeCat]['全部']

    const f = {
      homeCategory: homeCat,
      typeLabel: filters.type,
      plot: filters.plot,
      area: filters.area,
      lang: filters.lang,
      year: filters.year,
      sort: filters.sort,
      typeIds,
    }

    ;(async () => {
      try {
        const initTarget = FILTER_INITIAL_PREFETCH_PAGES * FILTER_PAGE_SIZE
        const r = await advanceFilterCatalog(f, null, initTarget, {
          signal: ac.signal,
          onPartial: (p) => {
            if (loadReqGen.current !== id) return
            setCatalogRows(p.sorted)
            if (p.apiTotalSum > 0) setDisplayTotal(p.apiTotalSum)
            if (p.sorted.length > 0 || p.done) setListLoading(false)
            if (p.done) {
              setCatalogTruncated(p.truncated)
              setCatalogExhausted(p.exhaustedAll)
              if (p.apiTotalSum <= 0) setDisplayTotal(p.sorted.length)
              else if (p.exhaustedAll) {
                setDisplayTotal((d) => (d > p.sorted.length ? p.sorted.length : d))
              }
            }
          },
        })
        if (loadReqGen.current !== id) return
        filterContRef.current = r.continuation
        setCatalogRows(r.sorted)
        if (r.apiTotalSum > 0) setDisplayTotal(r.apiTotalSum)
        else setDisplayTotal(r.sorted.length)
        if (r.exhaustedAll) {
          setDisplayTotal((d) =>
            d <= 0 ? r.sorted.length : Math.min(d, r.sorted.length)
          )
        }
        setCatalogTruncated(r.truncated)
        setCatalogExhausted(r.exhaustedAll)
      } catch (e) {
        if (loadReqGen.current !== id) return
        filterContRef.current = null
        setCatalogRows([])
        setDisplayTotal(0)
        setCatalogTruncated(false)
        setCatalogExhausted(true)
        setListError(e instanceof Error ? e.message : '加载失败')
      } finally {
        if (loadReqGen.current === id) setListLoading(false)
      }
    })()
    return () => {
      ac.abort()
    }
  }, [
    homeCat,
    filters.type,
    filters.plot,
    filters.area,
    filters.lang,
    filters.year,
    filters.sort,
  ])

  const catalogMovies = useMemo(
    () => catalogRows.map(mapVodRowToMovie),
    [catalogRows]
  )

  const statsTotal = useMemo(() => {
    if (displayTotal > 0) return displayTotal
    return catalogRows.length
  }, [displayTotal, catalogRows.length])

  const detailFetchKey = useMemo(() => {
    const start = (currentPage - 1) * FILTER_PAGE_SIZE
    const slice = catalogMovies.slice(start, start + FILTER_PAGE_SIZE * 2)
    return `${currentPage}:${slice.map(m => m.id).join(',')}`
  }, [catalogMovies, currentPage])

  useEffect(() => {
    const idx = detailFetchKey.indexOf(':')
    const idsPart = idx >= 0 ? detailFetchKey.slice(idx + 1) : ''
    const ids = [...new Set(idsPart.split(',').filter(Boolean))]
    if (ids.length === 0) return

    let cancelled = false
    ;(async () => {
      try {
        const map = await fetchVodRowsByDetailIds(ids)
        if (cancelled) return
        setDetailOverlay((prev) => {
          const next = { ...prev }
          for (const id of ids) {
            const row = map.get(id)
            if (row) next[id] = mapVodRowToMovie(row)
          }
          return next
        })
      } catch {
        /* 忽略详情失败 */
      }
    })()
    return () => {
      cancelled = true
    }
  }, [detailFetchKey])

  useEffect(() => {
    const id = loadReqGen.current
    if (listLoading) return
    const loaded = catalogRows.length
    if (loaded === 0) return

    const cap = listTotalUsesClientFilter
      ? Number.MAX_SAFE_INTEGER
      : displayTotal > 0
        ? displayTotal
        : Number.MAX_SAFE_INTEGER
    if (catalogExhausted && loaded >= cap) return

    const loadedPages = Math.max(1, Math.ceil(loaded / FILTER_PAGE_SIZE))
    const needRows = currentPage * FILTER_PAGE_SIZE
    const nearBoundary = currentPage >= loadedPages - 1
    const needFill = needRows > loaded && !catalogExhausted

    if (!nearBoundary && !needFill) return

    const target = Math.min(
      cap,
      Math.max(
        needRows,
        loaded + FILTER_CHUNK_PREFETCH_PAGES * FILTER_PAGE_SIZE
      )
    )
    if (target <= loaded) return

    const typeIds =
      filters.type === '全部'
        ? getHomeListQueryTypeIds(homeCat)
        : FILTER_TYPE_TO_IDS[homeCat][filters.type] ??
          FILTER_TYPE_TO_IDS[homeCat]['全部']

    const f = {
      homeCategory: homeCat,
      typeLabel: filters.type,
      plot: filters.plot,
      area: filters.area,
      lang: filters.lang,
      year: filters.year,
      sort: filters.sort,
      typeIds,
    }

    let cancelled = false
    ;(async () => {
      try {
        const r = await advanceFilterCatalog(f, filterContRef.current, target, {
          onPartial: (p) => {
            if (loadReqGen.current !== id || cancelled) return
            setCatalogRows(p.sorted)
            if (p.apiTotalSum > 0) setDisplayTotal(p.apiTotalSum)
          },
        })
        if (loadReqGen.current !== id || cancelled) return
        filterContRef.current = r.continuation
        setCatalogRows(r.sorted)
        setCatalogTruncated(r.truncated)
        setCatalogExhausted(r.exhaustedAll)
        if (r.exhaustedAll) {
          setDisplayTotal((d) =>
            d <= 0 ? r.sorted.length : Math.min(d, r.sorted.length)
          )
        }
      } catch {
        /* 续载失败忽略 */
      }
    })()
    return () => {
      cancelled = true
    }
  }, [
    listLoading,
    currentPage,
    catalogRows.length,
    displayTotal,
    catalogExhausted,
    homeCat,
    filters.type,
    filters.plot,
    filters.area,
    filters.lang,
    filters.year,
    filters.sort,
    listTotalUsesClientFilter,
  ])

  useEffect(() => {
    const tp = Math.max(1, Math.ceil(statsTotal / FILTER_PAGE_SIZE))
    if (currentPage > tp) setCurrentPage(tp)
  }, [statsTotal, currentPage])

  const listMovies = useMemo(() => {
    const start = (currentPage - 1) * FILTER_PAGE_SIZE
    return catalogMovies.slice(start, start + FILTER_PAGE_SIZE).map((m) => {
      const o = detailOverlay[m.id]
      return o ? { ...m, ...o } : m
    })
  }, [catalogMovies, currentPage, detailOverlay])

  const total = listMovies.length
  const totalPages = Math.max(1, Math.ceil(statsTotal / FILTER_PAGE_SIZE))

  const FILTER_PAGER_WINDOW = 9
  const pagerPages = useMemo(() => {
    const n = totalPages
    if (n <= FILTER_PAGER_WINDOW) {
      return Array.from({ length: n }, (_, i) => i + 1)
    }
    let lo = Math.max(1, currentPage - Math.floor(FILTER_PAGER_WINDOW / 2))
    let hi = Math.min(n, lo + FILTER_PAGER_WINDOW - 1)
    lo = Math.max(1, hi - FILTER_PAGER_WINDOW + 1)
    return Array.from({ length: hi - lo + 1 }, (_, i) => lo + i)
  }, [totalPages, currentPage])

  const firstPagerSpatialId = `filter-pg-${pagerPages[0]}`
  const lastPagerSpatialId = `filter-pg-${pagerPages[pagerPages.length - 1]}`

  const lastRowStart = total > 0 ? Math.floor((total - 1) / FILTER_COLS) * FILTER_COLS : 0

  /** 禁用按钮无法聚焦：第 1 页时「上一页」不可用，网格下移须落到当前窗口首个页码 */
  const gridFooterDownId =
    currentPage > 1 ? 'filter-pg-prev' : firstPagerSpatialId

  const mainEntry =
    activeFilter != null ? `filter-opt-${activeFilter}-0` : 'filter-sb-0'
  useTvSpatialMainEntry(mainEntry)

  const filterLabels = getFilterLabels()

  const selectFilter = (key: MaccmsFilterKey, value: string) => {
    setFilters(prev => ({ ...prev, [key]: value }))
    setActiveFilter(null)
    setCurrentPage(1)
  }

  const toggleFilter = (key: MaccmsFilterKey) => {
    setActiveFilter(activeFilter === key ? null : key)
  }

  /** 弹窗挂载后焦点落到第一个选项（与 spatial 主入口一致） */
  useEffect(() => {
    if (!activeFilter) return
    let cancelled = false
    const entryId = `filter-opt-${activeFilter}-0`
    const focusFirst = (): void => {
      if (cancelled) return
      queryFocusableSpatial(entryId)?.focus({ preventScroll: true })
    }
    const id = requestAnimationFrame(() => {
      requestAnimationFrame(focusFirst)
    })
    return () => {
      cancelled = true
      cancelAnimationFrame(id)
    }
  }, [activeFilter])

  useEffect(() => {
    if (!activeFilter) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault()
        setActiveFilter(null)
        return
      }
      if (e.key === 'Backspace') {
        const t = e.target as HTMLElement
        if (
          t.tagName === 'INPUT' ||
          t.tagName === 'TEXTAREA' ||
          t.isContentEditable
        ) {
          return
        }
        e.preventDefault()
        setActiveFilter(null)
      }
    }
    window.addEventListener('keydown', onKey, true)
    return () => window.removeEventListener('keydown', onKey, true)
  }, [activeFilter])

  return (
    <div className="h-full w-full flex bg-background overflow-hidden">
      <aside className="flex h-full w-[168px] shrink-0 flex-col border-r border-border bg-card px-3 py-5">
        <h2 className="mb-5 px-0.5 text-base font-bold text-foreground leading-tight">
          筛选条件
        </h2>

        <nav className="flex-1 space-y-2.5 overflow-y-auto thin-scrollbar px-0.5 py-2 pr-0.5">
          {MACCMS_FILTER_KEYS.map((fk, index) => (
            <FilterSidebarRow
              key={fk}
              index={index}
              fk={fk}
              activeFilter={activeFilter}
              filters={filters}
              filterLabels={filterLabels}
              toggleFilter={toggleFilter}
              navLeftId={filterNavLeftId}
            />
          ))}
        </nav>

        <div className="mt-4 border-t border-border pt-4">
          <div className="flex items-center gap-2 text-muted-foreground">
            <Info size={14} />
            <span className="text-xs">按 [返回] 键退出</span>
          </div>
        </div>
      </aside>

      {activeFilter && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 p-6 backdrop-blur-sm animate-fade-in"
          role="presentation"
          onClick={() => setActiveFilter(null)}
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="filter-modal-title"
            className="relative w-full max-w-lg rounded-2xl border border-border bg-card p-6 shadow-[var(--shadow-elevated)]"
            onClick={e => e.stopPropagation()}
          >
            <div className="mb-5 flex items-start justify-between gap-4">
              <h3
                id="filter-modal-title"
                className="min-w-0 flex-1 text-lg font-bold leading-snug text-foreground"
              >
                {filterModalData[activeFilter].title}
              </h3>
              <FilterModalClose
                activeFilter={activeFilter}
                optionsLen={filterModalData[activeFilter].options.length}
                onClose={() => setActiveFilter(null)}
              />
            </div>
            <div className="grid grid-cols-3 gap-2">
              {filterModalData[activeFilter].options.map((opt, optIndex) => (
                <FilterOverlayOption
                  key={opt}
                  activeFilter={activeFilter}
                  opt={opt}
                  optIndex={optIndex}
                  optionsLen={filterModalData[activeFilter].options.length}
                  filters={filters}
                  selectFilter={selectFilter}
                />
              ))}
            </div>
          </div>
        </div>
      )}

      <main
        id="filter-page-main-scroll"
        className="flex-1 min-h-0 h-full overflow-y-auto overflow-x-hidden px-7 pt-8 pb-36 scroll-pt-24 scroll-pb-24"
      >
        <header className="mb-6">
          <h2 className="text-2xl font-bold text-foreground mb-1">
            {categoryParam ? `${categoryParam}筛选` : '影片发现'}
          </h2>
          <div className="flex flex-wrap items-center gap-2 text-muted-foreground text-sm">
            <span>
              {filters.type} · {filters.plot} · {filters.area} · {filters.lang} ·{' '}
              {filters.year} · {filters.sort}
            </span>
            <span>/</span>
            <span>
              {listLoading
                ? '加载中…'
                : listError
                  ? listError
                  : `共 ${statsTotal} 条${
                      catalogTruncated
                        ? '（部分分类已达拉取页数上限）'
                        : listTotalUsesClientFilter && !catalogExhausted
                          ? '（已加载，翻页将继续合并）'
                          : ''
                    }`}
            </span>
          </div>
        </header>

        <div className="mb-8 grid grid-cols-5 gap-6">
          {listMovies.map((movie, index) => (
            <FilterGridCell
              key={`${movie.id}-${index}`}
              index={index}
              movie={movie}
              total={total}
              footerDownId={gridFooterDownId}
            />
          ))}
        </div>

        {statsTotal > 0 && (
          <div className="flex flex-col items-center gap-4 pb-16">
            <div className="flex items-center gap-3">
              <FilterPgPrev
                lastRowStart={lastRowStart}
                disabled={currentPage === 1}
                firstPagerSpatialId={firstPagerSpatialId}
                onPrev={() => setCurrentPage(p => Math.max(1, p - 1))}
              />
              {pagerPages.map((page, idx) => (
                <FilterPgNum
                  key={page}
                  page={page}
                  windowPages={pagerPages}
                  windowIndex={idx}
                  currentPage={currentPage}
                  totalPages={totalPages}
                  lastRowStart={lastRowStart}
                  setPage={setCurrentPage}
                />
              ))}
              <FilterPgNext
                lastRowStart={lastRowStart}
                disabled={currentPage === totalPages}
                lastPagerSpatialId={lastPagerSpatialId}
                onNext={() =>
                  setCurrentPage(p => Math.min(totalPages, p + 1))
                }
              />
            </div>
            <span className="text-muted-foreground text-sm">
              第 {currentPage} 页，共 {totalPages} 页
            </span>
          </div>
        )}
      </main>
    </div>
  )
}
