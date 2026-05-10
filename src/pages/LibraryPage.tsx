import { useSearchParams } from 'react-router-dom'
import { gridDownNeighborIndex } from '@/lib/gridSpatialNav'
import { cn } from '@/lib/utils'
import { useTvSpatialMainEntry, useTvSpatialNode } from '@/tv/spatial'
import { PosterCard } from '@/components/PosterCard'
import { movieList } from '@/data/mockData'
import type { Movie } from '@/data/mockData'
import { Heart, Clock, FileX } from 'lucide-react'

type TabType = 'favorite' | 'history'

const GRID_COLS = 6

const favoriteMovies = movieList.slice(0, 8)
const historyMovies = movieList.slice(5, 14)

function LibraryTabBtn({
  which,
  active,
  onSelect,
  icon,
  label,
}: {
  which: 0 | 1
  active: boolean
  onSelect: () => void
  icon: React.ReactNode
  label: string
}) {
  const spatial = useTvSpatialNode(
    `library-tab-${which}`,
    () => ({
      left: which === 1 ? 'library-tab-0' : undefined,
      right: which === 0 ? 'library-tab-1' : undefined,
      down: 'library-grid-0',
    }),
    [which]
  )

  return (
    <button
      type="button"
      {...spatial}
      className={cn(
        'tv-focusable tab-focus flex items-center gap-2 px-6 py-3 rounded-xl text-sm font-medium transition-[box-shadow,transform,colors] duration-150 ease-out',
        active
          ? 'bg-primary text-primary-foreground'
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
  showProgress,
}: {
  index: number
  movie: Movie
  total: number
  tabUpId: string
  showProgress: boolean
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
      {showProgress && (
        <div className="pointer-events-none absolute bottom-0 left-0 right-0 z-0 mx-0.5 h-1 overflow-hidden rounded-full bg-muted">
          <div
            className="h-full rounded-full bg-primary"
            style={{ width: `${Math.max(15, Math.min(95, 30 + index * 8))}%` }}
          />
        </div>
      )}
    </div>
  )
}

export function LibraryPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = (searchParams.get('tab') as TabType) || 'favorite'

  const setTab = (t: TabType) => {
    setSearchParams({ tab: t })
  }

  const isFavorite = tab === 'favorite'
  const items = isFavorite ? favoriteMovies : historyMovies
  const tabUpId = isFavorite ? 'library-tab-0' : 'library-tab-1'

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
          />
          <LibraryTabBtn
            which={1}
            active={!isFavorite}
            onSelect={() => setTab('history')}
            icon={<Clock size={18} />}
            label="观看历史"
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
                showProgress={!isFavorite}
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
              {isFavorite ? '在详情页点击收藏按钮添加' : '开始观看影片后自动记录'}
            </p>
          </div>
        )}
      </div>
    </div>
  )
}
