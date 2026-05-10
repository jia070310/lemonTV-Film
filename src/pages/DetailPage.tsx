import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useTvSpatialMainEntry, useTvSpatialNode } from '@/tv/spatial'
import { cn } from '@/lib/utils'
import { PosterCard } from '@/components/PosterCard'
import { heroMovies, movieList } from '@/data/mockData'
import type { Movie } from '@/data/mockData'
import { ArrowLeft, Play, Star, Calendar, MapPin, Clock, RefreshCw } from 'lucide-react'

const EP_COLS = 8

function DetailEpisodeBtn({
  epIndex,
  totalEpisodes,
  sourcesLen,
  selectedEpisode,
  setEpisode,
}: {
  epIndex: number
  totalEpisodes: number
  sourcesLen: number
  selectedEpisode: number
  setEpisode: (n: number) => void
}) {
  const row = Math.floor(epIndex / EP_COLS)
  const col = epIndex % EP_COLS
  const lastRowStart = Math.floor((totalEpisodes - 1) / EP_COLS) * EP_COLS
  const onBottomRow =
    epIndex >= lastRowStart && epIndex + EP_COLS >= totalEpisodes

  const spatial = useTvSpatialNode(
    `detail-ep-${epIndex}`,
    () => ({
      up:
        row === 0
          ? `detail-src-${sourcesLen - 1}`
          : `detail-ep-${epIndex - EP_COLS}`,
      down: onBottomRow
        ? 'detail-rel-0'
        : epIndex + EP_COLS < totalEpisodes
          ? `detail-ep-${epIndex + EP_COLS}`
          : undefined,
      left:
        col === 0
          ? `detail-src-${sourcesLen - 1}`
          : `detail-ep-${epIndex - 1}`,
      right:
        col < EP_COLS - 1 && epIndex + 1 < totalEpisodes
          ? `detail-ep-${epIndex + 1}`
          : undefined,
    }),
    [epIndex, totalEpisodes, sourcesLen]
  )

  const ep = epIndex + 1

  return (
    <button
      type="button"
      {...spatial}
      className={cn(
        'tv-focusable tab-focus h-10 rounded-lg text-sm font-medium transition-all duration-200',
        ep === selectedEpisode
          ? 'bg-primary text-primary-foreground'
          : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
      )}
      onClick={() => setEpisode(ep)}
    >
      {ep}
    </button>
  )
}

function DetailRelatedCard({
  index,
  movie,
  relatedLen,
  totalEpisodes,
  sourcesLen,
  navigate,
}: {
  index: number
  movie: Movie
  relatedLen: number
  totalEpisodes: number
  sourcesLen: number
  navigate: (path: string) => void
}) {
  const spatial = useTvSpatialNode(
    `detail-rel-${index}`,
    () => ({
      up:
        totalEpisodes > 1
          ? `detail-ep-${totalEpisodes - 1}`
          : `detail-src-${sourcesLen - 1}`,
      left: index > 0 ? `detail-rel-${index - 1}` : undefined,
      right: index < relatedLen - 1 ? `detail-rel-${index + 1}` : undefined,
    }),
    [index, relatedLen, totalEpisodes, sourcesLen]
  )

  return (
    <div
      {...spatial}
      className="poster-focus tv-focusable flex-shrink-0 rounded-lg outline-none"
      onClick={() => navigate(`/detail/${movie.id}`)}
      onKeyDown={(e) => {
        if (e.key === 'Enter') {
          e.preventDefault()
          navigate(`/detail/${movie.id}`)
        }
      }}
    >
      <PosterCard movie={movie} size="sm" focusable={false} />
    </div>
  )
}

export function DetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [selectedEpisode, setSelectedEpisode] = useState(1)
  const [currentSource, setCurrentSource] = useState(0)

  useTvSpatialMainEntry('detail-back')

  const spatialBack = useTvSpatialNode(
    'detail-back',
    () => ({
      left: 'nav-0',
      right: 'detail-play',
      down: 'detail-playbtn',
    }),
    []
  )
  const spatialPlay = useTvSpatialNode(
    'detail-play',
    () => ({
      left: 'detail-back',
      right: 'detail-playbtn',
      down: 'detail-playbtn',
    }),
    []
  )
  const spatialPlayBtn = useTvSpatialNode(
    'detail-playbtn',
    () => ({
      up: 'detail-play',
      left: 'detail-play',
      right: 'detail-fav',
      down: 'detail-src-0',
    }),
    []
  )
  const spatialFav = useTvSpatialNode(
    'detail-fav',
    () => ({
      left: 'detail-playbtn',
      right: 'detail-src-0',
      up: 'detail-play',
      down: 'detail-src-0',
    }),
    []
  )

  const movie = heroMovies.find(m => m.id === id) || heroMovies[0]
  const totalEpisodes = movie.episodes || 1
  const relatedMovies = movieList.filter(m => m.id !== movie.id).slice(0, 7)

  const sources = ['蓝光', '超清', '高清', '标清', '备用源1', '备用源2']
  const sourcesLen = sources.length

  return (
    <div
      id="detail-page-anchor"
      className="h-full w-full flex flex-col bg-background overflow-hidden"
    >
      <div className="flex flex-1 min-h-0 overflow-hidden">
        <div className="w-[55%] flex-shrink-0 flex items-start justify-center p-8 pb-4">
          <div className="relative w-full aspect-video rounded-2xl overflow-hidden shadow-[var(--shadow-elevated)]">
            <img
              src={movie.backdrop || movie.poster}
              alt={movie.title}
              className="w-full h-full object-cover"
            />
            <div className="absolute inset-0 gradient-hero" />
            <div className="absolute inset-0 bg-gradient-to-t from-background via-transparent to-transparent" />

            <button
              type="button"
              {...spatialBack}
              className="tv-focusable pill-focus absolute top-6 left-6 w-10 h-10 rounded-full bg-background/50 backdrop-blur-sm flex items-center justify-center z-10"
              onClick={() => navigate(-1)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') navigate(-1)
              }}
            >
              <ArrowLeft size={20} className="text-foreground" />
            </button>

            <button
              type="button"
              {...spatialPlay}
              className="tv-focusable absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-20 h-20 rounded-full bg-primary/90 backdrop-blur-sm flex items-center justify-center transition-[transform,box-shadow] duration-150 ease-out hover:scale-110 focus-visible:scale-110 focus-visible:shadow-[var(--shadow-glow)]"
              onClick={() => navigate(`/player/${movie.id}`)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') navigate(`/player/${movie.id}`)
              }}
            >
              <Play size={36} className="text-primary-foreground ml-1" fill="currentColor" />
            </button>

            <div className="absolute bottom-6 left-6 text-foreground">
              <p className="text-sm text-muted-foreground">
                正在播放: 第 {selectedEpisode} 集
              </p>
            </div>
          </div>
        </div>

        <div
          id="detail-right-anchor"
          className="flex-1 flex flex-col p-8 pl-0 pb-4 overflow-hidden"
        >
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

          <div className="flex items-center gap-3 mb-5">
            <button
              type="button"
              {...spatialPlayBtn}
              className="tv-focusable pill-focus flex items-center gap-2 px-6 py-2.5 rounded-full bg-primary text-primary-foreground font-medium text-sm"
              onClick={() => navigate(`/player/${movie.id}`)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') navigate(`/player/${movie.id}`)
              }}
            >
              <Play size={16} fill="currentColor" />
              立即播放
            </button>
            <button
              type="button"
              {...spatialFav}
              className="tv-focusable pill-focus px-6 py-2.5 rounded-full bg-secondary text-secondary-foreground font-medium text-sm"
            >
              收藏
            </button>
          </div>

          <div id="detail-sources-anchor" className="mb-5">
            <h3 className="text-base font-medium text-foreground mb-3 flex items-center gap-2">
              <RefreshCw size={14} className="text-primary" />
              播放源
            </h3>
            <div className="flex flex-wrap gap-2">
              {sources.map((source, idx) => (
                <SourceChip
                  key={source}
                  label={source}
                  index={idx}
                  sourcesLen={sourcesLen}
                  totalEpisodes={totalEpisodes}
                  currentSource={currentSource}
                  setCurrentSource={setCurrentSource}
                />
              ))}
            </div>
          </div>

          {totalEpisodes > 1 && (
            <div id="detail-episodes-anchor" className="mb-5">
              <h3 className="text-base font-medium text-foreground mb-3">选集</h3>
              <div
                id="detail-ep-scroll"
                className="grid grid-cols-8 gap-2 max-h-[88px] overflow-y-auto thin-scrollbar pr-2"
              >
                {Array.from({ length: totalEpisodes }, (_, i) => i).map(epIndex => (
                  <DetailEpisodeBtn
                    key={epIndex}
                    epIndex={epIndex}
                    totalEpisodes={totalEpisodes}
                    sourcesLen={sourcesLen}
                    selectedEpisode={selectedEpisode}
                    setEpisode={setSelectedEpisode}
                  />
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      <div className="flex-shrink-0 px-8 pb-6 overflow-hidden">
        <h3 className="text-base font-medium text-foreground mb-3">相关推荐</h3>
        <div className="flex gap-4 overflow-x-auto no-scrollbar pb-2">
          {relatedMovies.map((m, i) => (
            <DetailRelatedCard
              key={m.id}
              index={i}
              movie={m}
              relatedLen={relatedMovies.length}
              totalEpisodes={totalEpisodes}
              sourcesLen={sourcesLen}
              navigate={navigate}
            />
          ))}
        </div>
      </div>
    </div>
  )
}

function SourceChip({
  label,
  index,
  sourcesLen,
  totalEpisodes,
  currentSource,
  setCurrentSource,
}: {
  label: string
  index: number
  sourcesLen: number
  totalEpisodes: number
  currentSource: number
  setCurrentSource: (n: number) => void
}) {
  const spatial = useTvSpatialNode(
    `detail-src-${index}`,
    () => ({
      up: 'detail-playbtn',
      down: totalEpisodes > 1 ? 'detail-ep-0' : 'detail-rel-0',
      left: index === 0 ? 'detail-fav' : `detail-src-${index - 1}`,
      right:
        index === sourcesLen - 1
          ? totalEpisodes > 1
            ? 'detail-ep-0'
            : 'detail-rel-0'
          : `detail-src-${index + 1}`,
    }),
    [index, sourcesLen, totalEpisodes]
  )

  return (
    <button
      type="button"
      {...spatial}
      className={cn(
        'tv-focusable tab-focus px-4 py-2 rounded-full text-sm font-medium transition-all duration-200',
        index === currentSource
          ? 'bg-primary text-primary-foreground'
          : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
      )}
      onClick={() => setCurrentSource(index)}
    >
      {label}
    </button>
  )
}
