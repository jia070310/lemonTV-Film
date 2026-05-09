import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { PosterCard } from '@/components/PosterCard'
import { heroMovies, movieList } from '@/data/mockData'
import { ArrowLeft, Play, Star, Calendar, MapPin, Clock, RefreshCw } from 'lucide-react'

export function DetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [selectedEpisode, setSelectedEpisode] = useState(1)
  const [currentSource, setCurrentSource] = useState(0)

  const movie = heroMovies.find(m => m.id === id) || heroMovies[0]
  const totalEpisodes = movie.episodes || 1
  const relatedMovies = movieList.filter(m => m.id !== movie.id).slice(0, 7)

  const sources = ['蓝光', '超清', '高清', '标清', '备用源1', '备用源2']

  return (
    <div className="h-full w-full flex flex-col bg-background overflow-hidden">
      {/* Top section: video preview + info */}
      <div className="flex flex-1 min-h-0 overflow-hidden">
        {/* Left: Video preview (16:9), aligned to top */}
        <div className="w-[55%] flex-shrink-0 flex items-start justify-center p-8 pb-4">
          <div className="relative w-full aspect-video rounded-2xl overflow-hidden shadow-[var(--shadow-elevated)]">
            <img
              src={movie.backdrop || movie.poster}
              alt={movie.title}
              className="w-full h-full object-cover"
            />
            {/* Gradient overlays */}
            <div className="absolute inset-0 gradient-hero" />
            <div className="absolute inset-0 bg-gradient-to-t from-background via-transparent to-transparent" />

            {/* Back button */}
            <button
              className="tv-focusable pill-focus absolute top-6 left-6 w-10 h-10 rounded-full bg-background/50 backdrop-blur-sm flex items-center justify-center z-10"
              tabIndex={0}
              onClick={() => navigate(-1)}
              onKeyDown={(e) => { if (e.key === 'Enter') navigate(-1) }}
            >
              <ArrowLeft size={20} className="text-foreground" />
            </button>

            {/* Play button center */}
            <button
              className="tv-focusable absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-20 h-20 rounded-full bg-primary/90 backdrop-blur-sm flex items-center justify-center transition-all duration-300 hover:scale-110 focus-visible:scale-110 focus-visible:shadow-[var(--shadow-glow)]"
              tabIndex={0}
              onClick={() => navigate(`/player/${movie.id}`)}
              onKeyDown={(e) => { if (e.key === 'Enter') navigate(`/player/${movie.id}`) }}
            >
              <Play size={36} className="text-primary-foreground ml-1" fill="currentColor" />
            </button>

            {/* Episode info overlay */}
            <div className="absolute bottom-6 left-6 text-foreground">
              <p className="text-sm text-muted-foreground">
                正在播放: 第 {selectedEpisode} 集
              </p>
            </div>
          </div>
        </div>

        {/* Right: Movie info + episodes + buttons */}
        <div className="flex-1 flex flex-col p-8 pl-0 pb-4 overflow-hidden">
          {/* Movie title and info */}
          <div className="mb-5">
            <h1 className="text-3xl font-bold text-foreground mb-3">{movie.title}</h1>
            <div className="flex items-center gap-4 mb-4">
              <div className="flex items-center gap-1">
                <Star size={16} className="text-primary fill-primary" />
                <span className="text-primary font-bold">{movie.rating}</span>
              </div>
              <div className="flex items-center gap-1 text-muted-foreground text-sm">
                <Calendar size={14} />
                <span>{movie.year}</span>
              </div>
              <div className="flex items-center gap-1 text-muted-foreground text-sm">
                <MapPin size={14} />
                <span>{movie.area}</span>
              </div>
              <span className="bg-secondary px-2 py-0.5 rounded text-xs text-secondary-foreground">
                {movie.genre}
              </span>
              {totalEpisodes > 1 && (
                <div className="flex items-center gap-1 text-muted-foreground text-sm">
                  <Clock size={14} />
                  <span>共 {totalEpisodes} 集</span>
                </div>
              )}
            </div>
            <p className="text-sm text-muted-foreground leading-relaxed line-clamp-3 max-w-[500px]">
              {movie.description || '暂无简介'}
            </p>
          </div>

          {/* Action buttons */}
          <div className="flex items-center gap-3 mb-5">
            <button
              className="tv-focusable pill-focus flex items-center gap-2 px-6 py-2.5 rounded-full bg-primary text-primary-foreground font-medium text-sm"
              tabIndex={0}
              onClick={() => navigate(`/player/${movie.id}`)}
              onKeyDown={(e) => { if (e.key === 'Enter') navigate(`/player/${movie.id}`) }}
            >
              <Play size={16} fill="currentColor" />
              立即播放
            </button>
            <button
              className="tv-focusable pill-focus px-6 py-2.5 rounded-full bg-secondary text-secondary-foreground font-medium text-sm"
              tabIndex={0}
            >
              收藏
            </button>
          </div>

          {/* Source switcher */}
          <div className="mb-5">
            <h3 className="text-base font-medium text-foreground mb-3 flex items-center gap-2">
              <RefreshCw size={14} className="text-primary" />
              播放源
            </h3>
            <div className="flex flex-wrap gap-2">
              {sources.map((source, idx) => (
                <button
                  key={idx}
                  className={cn(
                    'tv-focusable tab-focus px-4 py-2 rounded-full text-sm font-medium transition-all duration-200',
                    idx === currentSource
                      ? 'bg-primary text-primary-foreground'
                      : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
                  )}
                  tabIndex={0}
                  onClick={() => setCurrentSource(idx)}
                >
                  {source}
                </button>
              ))}
            </div>
          </div>

          {/* Episode selector - fixed 2 rows with scroll */}
          {totalEpisodes > 1 && (
            <div className="mb-5">
              <h3 className="text-base font-medium text-foreground mb-3">选集</h3>
              <div className="grid grid-cols-8 gap-2 max-h-[88px] overflow-y-auto thin-scrollbar pr-2">
                {Array.from({ length: totalEpisodes }, (_, i) => i + 1).map(ep => (
                  <button
                    key={ep}
                    className={cn(
                      'tv-focusable tab-focus h-10 rounded-lg text-sm font-medium transition-all duration-200',
                      ep === selectedEpisode
                        ? 'bg-primary text-primary-foreground'
                        : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
                    )}
                    tabIndex={0}
                    onClick={() => setSelectedEpisode(ep)}
                  >
                    {ep}
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Bottom: Related recommendations (full width) */}
      <div className="flex-shrink-0 px-8 pb-6 overflow-hidden">
        <h3 className="text-base font-medium text-foreground mb-3">相关推荐</h3>
        <div className="flex gap-4 overflow-x-auto no-scrollbar pb-2">
          {relatedMovies.map(m => (
            <PosterCard key={m.id} movie={m} size="sm" />
          ))}
        </div>
      </div>
    </div>
  )
}
