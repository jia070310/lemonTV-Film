import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { PosterCard } from '@/components/PosterCard'
import { movieList, filterData } from '@/data/mockData'
import type { FilterKey } from '@/data/mockData'
import { Search, ChevronLeft, ChevronRight, Info } from 'lucide-react'

// Map category names to filter type options
const categoryToTypeMap: Record<string, string> = {
  '电视剧': '全部',
  '电影': '全部',
  '综艺': '全部',
  '动漫': '动画片',
}

export function FilterPage() {
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

  return (
    <div className="h-full w-full flex bg-background overflow-hidden">
      {/* Left Sidebar */}
      <aside className="w-[260px] h-full bg-card border-r border-border flex flex-col p-6 flex-shrink-0">
        <h2 className="text-xl font-bold text-foreground mb-8 px-1">筛选条件</h2>

        <nav className="flex-1 space-y-3 overflow-y-auto thin-scrollbar pr-1">
          {(Object.keys(filterData) as FilterKey[]).map(key => (
            <button
              key={key}
              className={cn(
                'tv-focusable tv-focus-yellow w-full flex flex-col items-start p-3.5 rounded-xl text-left transition-all duration-300',
                activeFilter === key
                  ? 'bg-primary text-primary-foreground'
                  : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
              )}
              tabIndex={0}
              onClick={() => setActiveFilter(activeFilter === key ? null : key)}
            >
              <span className={cn(
                'text-xs',
                activeFilter === key ? 'text-primary-foreground/70' : 'text-muted-foreground'
              )}>
                {filterLabels[key]}
              </span>
              <span className="text-base font-medium mt-0.5">{filters[key]}</span>
            </button>
          ))}
        </nav>

        <div className="mt-6 pt-6 border-t border-border">
          <div className="flex items-center gap-2 text-muted-foreground">
            <Info size={14} />
            <span className="text-xs">按 [返回] 键退出</span>
          </div>
        </div>
      </aside>

      {/* Filter Options Dropdown (overlay on content area) */}
      {activeFilter && (
        <div className="absolute left-[260px] top-0 bottom-0 w-[400px] bg-card/95 backdrop-blur-md border-r border-border z-40 p-6 animate-fade-in">
          <h3 className="text-lg font-bold text-foreground mb-4">
            {filterData[activeFilter].title}
          </h3>
          <div className="grid grid-cols-3 gap-2">
            {filterData[activeFilter].options.map(opt => (
              <button
                key={opt}
                className={cn(
                  'tv-focusable tab-focus py-2.5 px-2 rounded-lg text-sm font-medium transition-all duration-200 text-center',
                  filters[activeFilter] === opt
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
                )}
                tabIndex={0}
                onClick={() => selectFilter(activeFilter, opt)}
              >
                {opt}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Main Content */}
      <main className="flex-1 h-full overflow-y-auto p-8">
        {/* Top search bar and info */}
        <header className="flex items-center justify-between mb-6">
          <div>
            <h2 className="text-2xl font-bold text-foreground mb-1">
              {categoryParam ? `${categoryParam}筛选` : '影片发现'}
            </h2>
            <div className="flex items-center gap-2 text-muted-foreground text-sm">
              <span>{filters.type} · {filters.year} · {filters.area}</span>
              <span>/</span>
              <span>找到 2,480 部影片</span>
            </div>
          </div>
          <div className="relative">
            <Search size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-muted-foreground" />
            <input
              className="bg-secondary border-none rounded-xl py-3 pl-12 pr-6 w-72 text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary transition-all"
              placeholder="搜索电影、导演、演员..."
              type="text"
            />
          </div>
        </header>

        {/* Movie Grid */}
        <div className="grid grid-cols-5 gap-5 mb-8">
          {movieList.map(movie => (
            <PosterCard key={movie.id} movie={movie} size="lg" className="w-full" />
          ))}
        </div>

        {/* Pagination */}
        <div className="flex flex-col items-center gap-4 pb-8">
          <div className="flex items-center gap-3">
            <button
              className="tv-focusable pill-focus w-10 h-10 flex items-center justify-center rounded-full bg-secondary"
              tabIndex={0}
              onClick={() => setCurrentPage(Math.max(1, currentPage - 1))}
              disabled={currentPage === 1}
            >
              <ChevronLeft size={20} className="text-foreground" />
            </button>
            {Array.from({ length: totalPages }, (_, i) => i + 1).map(page => (
              <button
                key={page}
                className={cn(
                  'tv-focusable tab-focus w-10 h-10 rounded-xl flex items-center justify-center text-sm font-medium transition-all',
                  page === currentPage
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
                )}
                tabIndex={0}
                onClick={() => setCurrentPage(page)}
              >
                {page}
              </button>
            ))}
            <button
              className="tv-focusable pill-focus w-10 h-10 flex items-center justify-center rounded-full bg-secondary"
              tabIndex={0}
              onClick={() => setCurrentPage(Math.min(totalPages, currentPage + 1))}
              disabled={currentPage === totalPages}
            >
              <ChevronRight size={20} className="text-foreground" />
            </button>
          </div>
          <span className="text-muted-foreground text-sm">第 {currentPage} 页，共 {totalPages} 页</span>
        </div>
      </main>
    </div>
  )
}
