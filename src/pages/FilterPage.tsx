import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { useTvSpatialMainEntry, useTvSpatialNode } from '@/tv/spatial'
import { PosterCard } from '@/components/PosterCard'
import { movieList, filterData } from '@/data/mockData'
import type { FilterKey } from '@/data/mockData'
import type { Movie } from '@/data/mockData'
import { Search, ChevronLeft, ChevronRight, Info } from 'lucide-react'

const FILTER_KEYS = Object.keys(filterData) as FilterKey[]
const FILTER_COLS = 5
const FILTER_OPT_COLS = 3

const categoryToTypeMap: Record<string, string> = {
  '电视剧': '全部',
  '电影': '全部',
  '综艺': '全部',
  '动漫': '动画片',
}

function FilterSidebarRow({
  index,
  fk,
  activeFilter,
  filters,
  filterLabels,
  toggleFilter,
}: {
  index: number
  fk: FilterKey
  activeFilter: FilterKey | null
  filters: Record<FilterKey, string>
  filterLabels: Record<FilterKey, string>
  toggleFilter: (key: FilterKey) => void
}) {
  const spatial = useTvSpatialNode(
    `filter-sb-${index}`,
    () => ({
      up: index > 0 ? `filter-sb-${index - 1}` : undefined,
      down:
        index < FILTER_KEYS.length - 1 ? `filter-sb-${index + 1}` : undefined,
      right:
        activeFilter === fk
          ? `filter-opt-${fk}-0`
          : 'filter-grid-0',
    }),
    [index, activeFilter, fk]
  )

  return (
    <button
      type="button"
      {...spatial}
      className={cn(
        'tv-focusable tv-focus-yellow w-full flex flex-col items-start p-3.5 rounded-xl text-left transition-[box-shadow,transform,colors] duration-150 ease-out',
        activeFilter === fk
          ? 'tv-tab-selected'
          : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
      )}
      onClick={() => toggleFilter(fk)}
    >
      <span
        className={cn(
          'text-xs',
          activeFilter === fk ? 'text-white/70' : 'text-muted-foreground'
        )}
      >
        {filterLabels[fk]}
      </span>
      <span className="text-base font-medium mt-0.5">{filters[fk]}</span>
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
          ? `filter-sb-${filterKeyIndex}`
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
  navigate,
}: {
  index: number
  movie: Movie
  total: number
  navigate: (path: string) => void
}) {
  const row = Math.floor(index / FILTER_COLS)
  const col = index % FILTER_COLS
  const lastRowStart = Math.floor((total - 1) / FILTER_COLS) * FILTER_COLS
  const onLastRow = index >= lastRowStart
  const noCellBelow = index + FILTER_COLS >= total

  const spatial = useTvSpatialNode(
    `filter-grid-${index}`,
    () => ({
      up:
        row === 0
          ? 'filter-sb-0'
          : `filter-grid-${index - FILTER_COLS}`,
      down:
        noCellBelow && onLastRow
          ? 'filter-pg-prev'
          : index + FILTER_COLS < total
            ? `filter-grid-${index + FILTER_COLS}`
            : undefined,
      left: col === 0 ? 'filter-sb-0' : `filter-grid-${index - 1}`,
      right:
        col < FILTER_COLS - 1 && index + 1 < total
          ? `filter-grid-${index + 1}`
          : undefined,
    }),
    [index, total]
  )

  return (
    <div
      {...spatial}
      className="poster-focus tv-focusable rounded-lg outline-none"
      onClick={() => navigate(`/detail/${movie.id}`)}
      onKeyDown={(e) => {
        if (e.key === 'Enter') {
          e.preventDefault()
          navigate(`/detail/${movie.id}`)
        }
      }}
    >
      <PosterCard movie={movie} size="lg" focusable={false} className="w-full" />
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
      left: page === 1 ? 'filter-pg-prev' : `filter-pg-${page - 1}`,
      right: page === totalPages ? 'filter-pg-next' : `filter-pg-${page + 1}`,
    }),
    [page, totalPages, lastRowStart]
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
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const categoryParam = searchParams.get('category') || ''

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

  return (
    <div className="h-full w-full flex bg-background overflow-hidden">
      <aside className="w-[260px] h-full bg-card border-r border-border flex flex-col p-6 flex-shrink-0">
        <h2 className="text-xl font-bold text-foreground mb-8 px-1">筛选条件</h2>

        <nav className="flex-1 space-y-3 overflow-y-auto thin-scrollbar pr-1">
          {FILTER_KEYS.map((fk, index) => (
            <FilterSidebarRow
              key={fk}
              index={index}
              fk={fk}
              activeFilter={activeFilter}
              filters={filters}
              filterLabels={filterLabels}
              toggleFilter={toggleFilter}
            />
          ))}
        </nav>

        <div className="mt-6 pt-6 border-t border-border">
          <div className="flex items-center gap-2 text-muted-foreground">
            <Info size={14} />
            <span className="text-xs">按 [返回] 键退出</span>
          </div>
        </div>
      </aside>

      {activeFilter && (
        <div className="absolute left-[260px] top-0 bottom-0 w-[400px] bg-card/95 backdrop-blur-md border-r border-border z-40 p-6 animate-fade-in">
          <h3 className="text-lg font-bold text-foreground mb-4">
            {filterData[activeFilter].title}
          </h3>
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
      )}

      <main className="flex-1 h-full overflow-y-auto p-8">
        <header className="flex items-center justify-between mb-6">
          <div>
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
          </div>
          <div className="relative" data-spatial-arrow-through>
            <Search
              size={18}
              className="absolute left-4 top-1/2 -translate-y-1/2 text-muted-foreground"
            />
            <input
              className="bg-secondary border-none rounded-xl py-3 pl-12 pr-6 w-72 text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary transition-all"
              placeholder="搜索电影、导演、演员..."
              type="text"
            />
          </div>
        </header>

        <div className="grid grid-cols-5 gap-5 mb-8">
          {movieList.map((movie, index) => (
            <FilterGridCell
              key={movie.id}
              index={index}
              movie={movie}
              total={total}
              navigate={navigate}
            />
          ))}
        </div>

        <div className="flex flex-col items-center gap-4 pb-8">
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
