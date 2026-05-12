import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { gridDownNeighborIndex } from '@/lib/gridSpatialNav'
import { cn } from '@/lib/utils'
import { useTvSpatialMainEntry, useTvSpatialNode } from '@/tv/spatial'
import { PosterCard } from '@/components/PosterCard'
import type { Movie } from '@/data/mockData'
import {
  getFavorites,
  getWatchHistory,
  LIBRARY_CHANGED_EVENT,
  type WatchHistoryItem,
} from '@/lib/userLibraryStorage'
import { Heart, Clock, FileX } from 'lucide-react'

type TabType = 'favorite' | 'history'

const GRID_COLS = 6

function LibraryTabBtn({
  which,
  active,
  onSelect,
  icon,
  label,
  hasGrid,
}: {
  which: 0 | 1
  active: boolean
  onSelect: () => void
  icon: React.ReactNode
  label: string
  hasGrid: boolean
}) {
  const spatial = useTvSpatialNode(
    `library-tab-${which}`,
    () => ({
      left: which === 1 ? 'library-tab-0' : undefined,
      right: which === 0 ? 'library-tab-1' : undefined,
      down: hasGrid ? 'library-grid-0' : undefined,
    }),
    [which, hasGrid]
  )

  return (
    <button
      type="button"
      {...spatial}
      className={cn(
        'tv-focusable tab-focus flex items-center gap-2 px-6 py-3 rounded-xl text-sm font-medium transition-[box-shadow,transform,colors] duration-150 ease-out',
        active
          ? 'tv-tab-selected'
          : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
      )}
      onClick={onSelect}
    >
      {icon}
      {label}
    </button>
  )
}

function LibraryGridCell({
  index,
  movie,
  total,
  tabUpId,
  progressPercent,
}: {
  index: number
  movie: Movie
  total: number
  tabUpId: string
  /** 有值时显示底部进度条（观看历史） */
  progressPercent?: number
}) {
  const row = Math.floor(index / GRID_COLS)
  const col = index % GRID_COLS

  const spatial = useTvSpatialNode(
    `library-grid-${index}`,
    () => {
      const downIdx = gridDownNeighborIndex(index, total, GRID_COLS)
      return {
        up: row === 0 ? tabUpId : `library-grid-${index - GRID_COLS}`,
        down: downIdx !== undefined ? `library-grid-${downIdx}` : undefined,
        left: col === 0 ? 'nav-0' : `library-grid-${index - 1}`,
        right:
          col < GRID_COLS - 1 && index + 1 < total
            ? `library-grid-${index + 1}`
            : undefined,
      }
    },
    [index, total, tabUpId]
  )

  const pct =
    progressPercent != null
      ? Math.round(Math.min(100, Math.max(0, progressPercent)))
      : undefined

  return (
    <div className="library-poster-cell relative">
      <div className="relative z-[1]">
        <PosterCard
          movie={movie}
          size="lg"
          focusable={false}
          posterShellProps={{ ...spatial }}
          className="w-full"
        />
      </div>
      {pct != null && (
        <div className="pointer-events-none absolute bottom-0 left-0 right-0 z-0 mx-0.5 h-1 overflow-hidden rounded-full bg-muted">
          <div
            className="h-full rounded-full bg-primary"
            style={{ width: `${pct}%` }}
          />
        </div>
      )}
    </div>
  )
}

export function LibraryPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = (searchParams.get('tab') as TabType) || 'favorite'

  const [favorites, setFavorites] = useState<Movie[]>(() => getFavorites())
  const [history, setHistory] = useState<WatchHistoryItem[]>(() => getWatchHistory())

  const setTab = (t: TabType) => {
    setSearchParams({ tab: t })
  }

  const isFavorite = tab === 'favorite'
  const tabUpId = isFavorite ? 'library-tab-0' : 'library-tab-1'

  const refresh = useCallback(() => {
    setFavorites(getFavorites())
    setHistory(getWatchHistory())
  }, [])

  useEffect(() => {
    refresh()
  }, [tab, refresh])

  useEffect(() => {
    const fn = () => refresh()
    window.addEventListener(LIBRARY_CHANGED_EVENT, fn)
    return () => window.removeEventListener(LIBRARY_CHANGED_EVENT, fn)
  }, [refresh])

  const items: Movie[] = isFavorite ? favorites : history.map((h) => h.movie)
  const hasGrid = items.length > 0

  useTvSpatialMainEntry('library-tab-0')

  return (
    <div className="flex h-full flex-col overflow-hidden bg-background p-8">
      <div className="flex items-center justify-between mb-8">
        <div className="flex items-center gap-2">
          <LibraryTabBtn
            which={0}
            active={isFavorite}
            onSelect={() => setTab('favorite')}
            icon={<Heart size={18} />}
            label="我的收藏"
            hasGrid={isFavorite && hasGrid}
          />
          <LibraryTabBtn
            which={1}
            active={!isFavorite}
            onSelect={() => setTab('history')}
            icon={<Clock size={18} />}
            label="观看历史"
            hasGrid={!isFavorite && hasGrid}
          />
        </div>

        <span className="text-sm text-muted-foreground">
          共 <span className="text-primary font-bold">{items.length}</span> 部
        </span>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto overflow-x-hidden thin-scrollbar scroll-py-10 px-0.5 py-3">
        {items.length > 0 ? (
          <div className="grid grid-cols-6 gap-4 pb-10">
            {items.map((movie, i) => (
              <LibraryGridCell
                key={`${movie.id}-${i}`}
                index={i}
                movie={movie}
                total={items.length}
                tabUpId={tabUpId}
                progressPercent={
                  !isFavorite ? history.find((h) => h.movie.id === movie.id)?.progress : undefined
                }
              />
            ))}
          </div>
        ) : (
          <div className="flex flex-col items-center justify-center h-full text-muted-foreground">
            <FileX size={56} className="mb-4 opacity-20" />
            <p className="text-lg">
              {isFavorite ? '暂无收藏影片' : '暂无观看记录'}
            </p>
            <p className="text-sm mt-2 opacity-50">
              {isFavorite ? '在详情页点击收藏按钮添加' : '在播放页观看后会自动记录'}
            </p>
          </div>
        )}
      </div>
    </div>
  )
}
