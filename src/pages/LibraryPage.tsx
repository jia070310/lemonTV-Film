import { useSearchParams } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { PosterCard } from '@/components/PosterCard'
import { movieList } from '@/data/mockData'
import { Heart, Clock, FileX } from 'lucide-react'

type TabType = 'favorite' | 'history'

const favoriteMovies = movieList.slice(0, 8)
const historyMovies = movieList.slice(5, 14)

export function LibraryPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = (searchParams.get('tab') as TabType) || 'favorite'

  const setTab = (t: TabType) => {
    setSearchParams({ tab: t })
  }

  const isFavorite = tab === 'favorite'
  const items = isFavorite ? favoriteMovies : historyMovies

  return (
    <div className="h-full flex flex-col bg-background overflow-hidden p-8">
      {/* Header with Tabs */}
      <div className="flex items-center justify-between mb-8">
        <div className="flex items-center gap-2">
          <button
            className={cn(
              'tv-focusable tab-focus flex items-center gap-2 px-6 py-3 rounded-xl text-sm font-medium transition-all',
              isFavorite
                ? 'bg-primary text-primary-foreground'
                : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
            )}
            tabIndex={0}
            onClick={() => setTab('favorite')}
          >
            <Heart size={18} />
            我的收藏
          </button>
          <button
            className={cn(
              'tv-focusable tab-focus flex items-center gap-2 px-6 py-3 rounded-xl text-sm font-medium transition-all',
              !isFavorite
                ? 'bg-primary text-primary-foreground'
                : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
            )}
            tabIndex={0}
            onClick={() => setTab('history')}
          >
            <Clock size={18} />
            观看历史
          </button>
        </div>

        <span className="text-sm text-muted-foreground">
          共 <span className="text-primary font-bold">{items.length}</span> 部
        </span>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto thin-scrollbar">
        {items.length > 0 ? (
          <div className="grid grid-cols-6 gap-4 pb-8">
            {items.map((movie, i) => (
              <div key={`${movie.id}-${i}`} className="relative">
                <PosterCard movie={movie} size="lg" className="w-full" />
                {!isFavorite && (
                  <div className="absolute bottom-0 left-0 right-0 mx-0.5 h-1 bg-muted rounded-full overflow-hidden">
                    <div
                      className="h-full bg-primary rounded-full"
                      style={{ width: `${Math.max(15, Math.min(95, 30 + i * 8))}%` }}
                    />
                  </div>
                )}
              </div>
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
