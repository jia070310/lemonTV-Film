import { cn } from '@/lib/utils'
import type { Movie } from '@/data/mockData'
import { useNavigate } from 'react-router-dom'

interface PosterCardProps {
  movie: Movie
  size?: 'xs' | 'sm' | 'md' | 'lg'
  showInfo?: boolean
  /** When false, parent handles focus ring (avoids nested focusables). Default true. */
  focusable?: boolean
  className?: string
}

export function PosterCard({ movie, size = 'md', showInfo = true, focusable = true, className }: PosterCardProps) {
  const navigate = useNavigate()

  const sizeClasses = {
    xs: 'w-[112px]',
    sm: 'w-[140px]',
    md: 'w-[180px]',
    lg: 'w-[200px]',
  }

  return (
    <div
      className={cn(
        focusable && 'poster-focus tv-focusable',
        'flex-shrink-0 cursor-pointer group',
        sizeClasses[size],
        className
      )}
      tabIndex={focusable ? 0 : -1}
      onClick={() => navigate(`/detail/${movie.id}`)}
      onKeyDown={(e) => { if (e.key === 'Enter') navigate(`/detail/${movie.id}`) }}
    >
      <div className="relative aspect-[2/3] rounded-lg overflow-hidden tv-card">
        <img
          src={movie.poster}
          alt={movie.title}
          className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-110"
          loading="lazy"
        />
        {/* Rating badge */}
        <div className="absolute top-2 right-2 bg-background/70 backdrop-blur-sm px-1.5 py-0.5 rounded text-xs font-bold text-primary border border-primary/30">
          {movie.rating}
        </div>
        {/* Tag badge */}
        {movie.tag && (
          <div className={cn(
            'absolute top-2 left-2 px-1.5 py-0.5 rounded text-[10px] font-bold',
            movie.tag === '热门' ? 'bg-primary text-primary-foreground' : 'bg-blue-600 text-foreground'
          )}>
            {movie.tag}
          </div>
        )}
        {/* Bottom gradient */}
        <div className="absolute inset-x-0 bottom-0 h-1/3 gradient-card" />
      </div>
      {showInfo && (
        <div className={cn('mt-1.5 px-0.5', size === 'xs' && 'mt-1')}>
          <h3
            className={cn(
              'font-medium text-foreground truncate',
              size === 'xs' ? 'text-xs' : 'text-sm'
            )}
          >
            {movie.title}
          </h3>
          <p
            className={cn(
              'text-muted-foreground mt-0.5',
              size === 'xs' ? 'text-[10px] leading-tight' : 'text-xs'
            )}
          >
            {movie.year} · {movie.genre}
          </p>
        </div>
      )}
    </div>
  )
}
