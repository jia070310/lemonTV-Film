import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { useTvSpatialMainEntry, useTvSpatialNode } from '@/tv/spatial'
import { PosterCard } from '@/components/PosterCard'
import { gridDownNeighborIndex } from '@/lib/gridSpatialNav'
import { movieList, filterData } from '@/data/mockData'
import type { FilterKey } from '@/data/mockData'
import type { Movie } from '@/data/mockData'
import { ChevronLeft, ChevronRight, Info, X } from 'lucide-react'

const FILTER_KEYS = Object.keys(filterData) as FilterKey[]
const FILTER_COLS = 5
const FILTER_OPT_COLS = 3

const categoryToTypeMap: Record<string, string> = {
  '电视剧': '全部',
  '电影': '全部',
  '综艺': '全部',
  '动漫': '动画片',
}

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
  fk: FilterKey
  activeFilter: FilterKey | null
  filters: Record<FilterKey, string>
  filterLabels: Record<FilterKey, string>
  toggleFilter: (key: FilterKey) => void
  /** 左侧全局分类栏 spatial id，例如 nav-1 */
  navLeftId: string
}) {
  const spatial = useTvSpatialNode(
    `filter-sb-${index}`,
    () => ({
      left: navLeftId,
      up: index > 0 ? `filter-sb-${index - 1}` : undefined,
      down:
        index < FILTER_KEYS.length - 1 ? `filter-sb-${index + 1}` : undefined,
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
        'filter-sidebar-btn tv-focusable tv-focus-yellow w-full flex flex-col items-start gap-0 rounded-md px-2.5 py-1.5 text-left transition-[box-shadow,background-color,color,border-color] duration-150 ease-out',
        activeFilter === fk
          ? 'tv-tab-selected'
          : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
      )}
      onClick={() => toggleFilter(fk)}
    >
      <span
        className={cn(
          'text-[10px] leading-tight',
          activeFilter === fk ? 'text-white/70' : 'text-muted-foreground'
        )}
      >
        {filterLabels[fk]}
      </span>
      <span className="text-xs font-medium leading-tight">{filters[fk]}</span>
    </button>
  )
}

function FilterModalClose({
  activeFilter,
  filterKeyIndex,
  onClose,
}: {
  activeFilter: FilterKey
  filterKeyIndex: number
  onClose: () => void
}) {
  const spatial = useTvSpatialNode(
    'filter-modal-close',
    () => ({
      left: `filter-sb-${filterKeyIndex}`,
      down: `filter-opt-${activeFilter}-0`,
    }),
    [activeFilter, filterKeyIndex]
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
  filterKeyIndex,
  opt,
  optIndex,
  optionsLen,
  filters,
  selectFilter,
}: {
  activeFilter: FilterKey
  filterKeyIndex: number
  opt: string
  optIndex: number
  optionsLen: number
  filters: Record<FilterKey, string>
  selectFilter: (key: FilterKey, value: string) => void
}) {
  const row = Math.floor(optIndex / FILTER_OPT_COLS)
  const col = optIndex % FILTER_OPT_COLS
  const idPrefix = `filter-opt-${activeFilter}`

  const spatial = useTvSpatialNode(
    `${idPrefix}-${optIndex}`,
    () => ({
      left:
        col === 0
          ? `filter-sb-${filterKeyIndex}`
          : `${idPrefix}-${optIndex - 1}`,
      right:
        col < FILTER_OPT_COLS - 1 && optIndex + 1 < optionsLen
          ? `${idPrefix}-${optIndex + 1}`
          : undefined,
      up:
        row === 0
          ? 'filter-modal-close'
          : `${idPrefix}-${optIndex - FILTER_OPT_COLS}`,
      down:
        optIndex + FILTER_OPT_COLS < optionsLen
          ? `${idPrefix}-${optIndex + FILTER_OPT_COLS}`
          : undefined,
    }),
    [activeFilter, filterKeyIndex, optIndex, optionsLen]
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
}: {
  lastRowStart: number
  disabled: boolean
  onPrev: () => void
}) {
  const spatial = useTvSpatialNode(
    'filter-pg-prev',
    () => ({
      up: `filter-grid-${lastRowStart}`,
      right: 'filter-pg-1',
    }),
    [lastRowStart]
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
  currentPage,
  totalPages,
  lastRowStart,
  setPage,
}: {
  page: number
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
        page === 1
          ? currentPage > 1
            ? 'filter-pg-prev'
            : undefined
          : `filter-pg-${page - 1}`,
      right:
        page === totalPages
          ? currentPage < totalPages
            ? 'filter-pg-next'
            : undefined
          : `filter-pg-${page + 1}`,
    }),
    [page, totalPages, lastRowStart, currentPage]
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
  totalPages,
  disabled,
  onNext,
}: {
  lastRowStart: number
  totalPages: number
  disabled: boolean
  onNext: () => void
}) {
  const spatial = useTvSpatialNode(
    'filter-pg-next',
    () => ({
      up: `filter-grid-${lastRowStart}`,
      left: `filter-pg-${totalPages}`,
    }),
    [lastRowStart, totalPages]
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

  const filterNavLeftId =
    categoryParam && FILTER_CATEGORY_TO_NAV_INDEX[categoryParam] != null
      ? `nav-${FILTER_CATEGORY_TO_NAV_INDEX[categoryParam]}`
      : 'nav-1'

  const initialType = categoryToTypeMap[categoryParam] || '全部'

  const [filters, setFilters] = useState<Record<FilterKey, string>>({
    type: initialType,
    plot: '全部',
    area: '全部',
    lang: '全部',
    year: '全部',
    sort: '人气排序',
  })
  const [activeFilter, setActiveFilter] = useState<FilterKey | null>(null)
  const [currentPage, setCurrentPage] = useState(1)
  const totalPages = 6

  const total = movieList.length
  const lastRowStart = Math.floor((total - 1) / FILTER_COLS) * FILTER_COLS

  /** 禁用按钮无法聚焦：第 1 页时「上一页」不可用，网格下移须落到页码 1 */
  const gridFooterDownId =
    currentPage > 1 ? 'filter-pg-prev' : 'filter-pg-1'

  const mainEntry =
    activeFilter != null ? `filter-opt-${activeFilter}-0` : 'filter-sb-0'
  useTvSpatialMainEntry(mainEntry)

  const filterLabels: Record<FilterKey, string> = {
    type: '类型',
    plot: '剧情',
    area: '地区',
    lang: '语言',
    year: '年份',
    sort: '排序',
  }

  const selectFilter = (key: FilterKey, value: string) => {
    setFilters(prev => ({ ...prev, [key]: value }))
    setActiveFilter(null)
    setCurrentPage(1)
  }

  const toggleFilter = (key: FilterKey) => {
    setActiveFilter(activeFilter === key ? null : key)
  }

  const activeFilterKeyIndex =
    activeFilter != null ? FILTER_KEYS.indexOf(activeFilter) : -1

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
      <aside className="flex h-full w-[212px] shrink-0 flex-col border-r border-border bg-card px-4 py-5">
        <h2 className="mb-6 px-0.5 text-lg font-bold text-foreground">筛选条件</h2>

        <nav className="flex-1 space-y-2 overflow-y-auto thin-scrollbar px-0.5 py-2 pr-1">
          {FILTER_KEYS.map((fk, index) => (
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
                {filterData[activeFilter].title}
              </h3>
              <FilterModalClose
                activeFilter={activeFilter}
                filterKeyIndex={activeFilterKeyIndex}
                onClose={() => setActiveFilter(null)}
              />
            </div>
            <div className="grid grid-cols-3 gap-2">
              {filterData[activeFilter].options.map((opt, optIndex) => (
                <FilterOverlayOption
                  key={opt}
                  activeFilter={activeFilter}
                  filterKeyIndex={activeFilterKeyIndex}
                  opt={opt}
                  optIndex={optIndex}
                  optionsLen={filterData[activeFilter].options.length}
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
          <div className="flex items-center gap-2 text-muted-foreground text-sm">
            <span>
              {filters.type} · {filters.year} · {filters.area}
            </span>
            <span>/</span>
            <span>找到 2,480 部影片</span>
          </div>
        </header>

        <div className="mb-8 grid grid-cols-5 gap-6">
          {movieList.map((movie, index) => (
            <FilterGridCell
              key={movie.id}
              index={index}
              movie={movie}
              total={total}
              footerDownId={gridFooterDownId}
            />
          ))}
        </div>

        <div className="flex flex-col items-center gap-4 pb-16">
          <div className="flex items-center gap-3">
            <FilterPgPrev
              lastRowStart={lastRowStart}
              disabled={currentPage === 1}
              onPrev={() => setCurrentPage(p => Math.max(1, p - 1))}
            />
            {Array.from({ length: totalPages }, (_, i) => i + 1).map(page => (
              <FilterPgNum
                key={page}
                page={page}
                currentPage={currentPage}
                totalPages={totalPages}
                lastRowStart={lastRowStart}
                setPage={setCurrentPage}
              />
            ))}
            <FilterPgNext
              lastRowStart={lastRowStart}
              totalPages={totalPages}
              disabled={currentPage === totalPages}
              onNext={() =>
                setCurrentPage(p => Math.min(totalPages, p + 1))
              }
            />
          </div>
          <span className="text-muted-foreground text-sm">
            第 {currentPage} 页，共 {totalPages} 页
          </span>
        </div>
      </main>
    </div>
  )
}
