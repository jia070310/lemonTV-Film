import type { ComponentPropsWithoutRef } from 'react'
import { cn } from '@/lib/utils'
import type { Movie } from '@/data/mockData'
import { useNavigate } from 'react-router-dom'

interface PosterCardProps {
  movie: Movie
  size?: 'xs' | 'sm' | 'md' | 'lg'
  showInfo?: boolean
  /** When false, parent can pass posterShellProps to put spatial focus on the cover only */
  focusable?: boolean
  /** Spread onto the poster (cover) node — use with focusable={false} for TV spatial */
  posterShellProps?: Omit<ComponentPropsWithoutRef<'div'>, 'children'>
  className?: string
}

export function PosterCard({
  movie,
  size = 'md',
  showInfo = true,
  focusable = true,
  posterShellProps,
  className,
}: PosterCardProps) {
  const navigate = useNavigate()

  const sizeClasses = {
    xs: 'w-[112px]',
    sm: 'w-[140px]',
    md: 'w-[180px]',
    lg: 'w-[200px]',
  }

  const rawShell = !focusable ? posterShellProps : undefined
  const {
    className: shellClass,
    onKeyDown: shellOnKeyDown,
    tabIndex: _omitTab,
    ...shellRest
  } = rawShell ?? {}

  const posterShellInteractive = focusable || Boolean(posterShellProps)

  const go = () => navigate(`/detail/${movie.id}`)

  return (
    <div
      className={cn(
        'group flex flex-shrink-0 cursor-pointer flex-col',
        sizeClasses[size],
        className
      )}
      onClick={go}
    >
      <div
        {...shellRest}
        tabIndex={posterShellInteractive ? 0 : -1}
        className={cn(
          posterShellInteractive && 'poster-focus tv-focusable',
          'relative aspect-[2/3] overflow-hidden rounded-lg tv-card outline-none',
          shellClass
        )}
        onKeyDown={(e) => {
          shellOnKeyDown?.(e)
          if (e.defaultPrevented) return
          if (posterShellInteractive && e.key === 'Enter') {
            e.preventDefault()
            go()
          }
        }}
      >
        <img
          src={movie.poster}
          alt={movie.title}
          className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-110"
          loading="lazy"
        />
        <div className="absolute right-2 top-2 rounded border border-primary/30 bg-background/70 px-1.5 py-0.5 text-xs font-bold text-primary backdrop-blur-sm">
          {movie.rating}
        </div>
        {movie.tag && (
          <div
            className={cn(
              'absolute left-2 top-2 rounded px-1.5 py-0.5 text-[10px] font-bold',
              movie.tag === '热门'
                ? 'bg-primary text-primary-foreground'
                : 'bg-blue-600 text-foreground'
            )}
          >
            {movie.tag}
          </div>
        )}
        <div className="gradient-card absolute inset-x-0 bottom-0 h-1/3" />
      </div>
      {showInfo && (
        <div className={cn('mt-1.5 px-0.5', size === 'xs' && 'mt-1')}>
          <h3
            className={cn(
              'truncate font-medium text-foreground',
              size === 'xs' ? 'text-xs' : 'text-sm'
            )}
          >
            {movie.title}
          </h3>
          <p
            className={cn(
              'mt-0.5 text-muted-foreground',
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
