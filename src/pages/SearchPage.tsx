import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTvSpatialMainEntry, useTvSpatialNode } from '@/tv/spatial'
import { PosterCard } from '@/components/PosterCard'
import { movieList } from '@/data/mockData'
import type { Movie } from '@/data/mockData'
import { Search, Clock, X } from 'lucide-react'

const searchHistory = ['科幻', '动作', '星际穿越', '2026', '热门']
const GRID_COLS = 6

function SearchHistoryChip({
  index,
  tag,
  total,
  onPick,
}: {
  index: number
  tag: string
  total: number
  onPick: (tag: string) => void
}) {
  const spatial = useTvSpatialNode(
    `search-chip-${index}`,
    () => ({
      left: index > 0 ? `search-chip-${index - 1}` : undefined,
      right: index < total - 1 ? `search-chip-${index + 1}` : undefined,
    }),
    [index, total]
  )

  return (
    <button
      type="button"
      {...spatial}
      className="tv-focusable pill-focus px-5 py-2.5 rounded-full bg-secondary text-sm text-secondary-foreground hover:bg-surface-hover transition-all"
      onClick={() => onPick(tag)}
    >
      {tag}
    </button>
  )
}

function SearchResultCell({
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
  const row = Math.floor(index / GRID_COLS)
  const col = index % GRID_COLS

  const spatial = useTvSpatialNode(
    `search-grid-${index}`,
    () => ({
      up:
        row === 0 ? undefined : `search-grid-${index - GRID_COLS}`,
      down:
        index + GRID_COLS < total
          ? `search-grid-${index + GRID_COLS}`
          : undefined,
      left: col === 0 ? 'nav-0' : `search-grid-${index - 1}`,
      right:
        col < GRID_COLS - 1 && index + 1 < total
          ? `search-grid-${index + 1}`
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

export function SearchPage() {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<typeof movieList>([])
  const [hasSearched, setHasSearched] = useState(false)

  const handleSearch = (q: string) => {
    setQuery(q)
    if (!q.trim()) {
      setResults([])
      setHasSearched(false)
      return
    }
    const filtered = movieList.filter(
      m =>
        m.title.toLowerCase().includes(q.toLowerCase()) ||
        m.genre.toLowerCase().includes(q.toLowerCase()) ||
        m.year.includes(q)
    )
    setResults(filtered)
    setHasSearched(true)
  }

  const clearSearch = () => {
    setQuery('')
    setResults([])
    setHasSearched(false)
  }

  const mainEntry =
    hasSearched && results.length > 0 ? 'search-grid-0' : 'search-chip-0'
  useTvSpatialMainEntry(mainEntry)

  const histLen = searchHistory.length

  return (
    <div className="h-full flex flex-col bg-background overflow-hidden p-8">
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-foreground mb-6">搜索影片</h2>
        <div className="relative max-w-2xl" data-spatial-arrow-through>
          <Search
            size={22}
            className="absolute left-5 top-1/2 -translate-y-1/2 text-muted-foreground"
          />
          <input
            className="tv-focusable w-full bg-secondary border-none rounded-2xl py-4 pl-14 pr-14 text-lg text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary transition-all"
            placeholder="搜索电影、电视剧、综艺、动漫..."
            type="text"
            value={query}
            onChange={(e) => handleSearch(e.target.value)}
            tabIndex={0}
          />
          {query && (
            <button
              type="button"
              className="absolute right-5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
              onClick={clearSearch}
              tabIndex={0}
            >
              <X size={20} />
            </button>
          )}
        </div>
      </div>

      {!hasSearched && (
        <div className="mb-8">
          <div className="flex items-center gap-2 text-muted-foreground mb-4">
            <Clock size={16} />
            <span className="text-sm font-medium">搜索历史</span>
          </div>
          <div className="flex flex-wrap gap-3">
            {searchHistory.map((tag, i) => (
              <SearchHistoryChip
                key={tag}
                index={i}
                tag={tag}
                total={histLen}
                onPick={handleSearch}
              />
            ))}
          </div>
        </div>
      )}

      {hasSearched && (
        <div className="flex-1 overflow-y-auto thin-scrollbar">
          {results.length > 0 ? (
            <>
              <p className="text-muted-foreground text-sm mb-4">
                找到{' '}
                <span className="text-primary font-bold">{results.length}</span>{' '}
                部相关影片
              </p>
              <div className="grid grid-cols-6 gap-4 pb-8">
                {results.map((movie, i) => (
                  <SearchResultCell
                    key={movie.id}
                    index={i}
                    movie={movie}
                    total={results.length}
                    navigate={navigate}
                  />
                ))}
              </div>
            </>
          ) : (
            <div className="flex flex-col items-center justify-center h-full text-muted-foreground">
              <Search size={48} className="mb-4 opacity-30" />
              <p className="text-lg">未找到相关影片</p>
              <p className="text-sm mt-2 opacity-60">请尝试其他关键词</p>
            </div>
          )}
        </div>
      )}

      {!hasSearched && (
        <div className="flex-1 flex flex-col items-center justify-center text-muted-foreground">
          <Search size={56} className="mb-4 opacity-20" />
          <p className="text-lg">输入关键词开始搜索</p>
          <p className="text-sm mt-2 opacity-50">支持影片名称、类型、年份等</p>
        </div>
      )}
    </div>
  )
}
