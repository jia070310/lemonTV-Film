import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useTvSpatialMainEntry, useTvSpatialNode } from '@/tv/spatial'
import { cn } from '@/lib/utils'
import { PosterCard } from '@/components/PosterCard'
import { heroMovies, movieList } from '@/data/mockData'
import type { Movie } from '@/data/mockData'
import {
  ArrowLeft,
  Play,
  Star,
  Calendar,
  MapPin,
  Clock,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react'

const EP_COLS = 8
const EP_ROWS = 2
const EP_PER_PAGE = EP_COLS * EP_ROWS

function episodeProgressLabel(movie: Movie, totalEpisodes: number): string {
  const aired = movie.airedEpisodes
  if (aired != null && aired >= 1 && aired < totalEpisodes) {
    return `更新至${aired}集`
  }
  return `全集${totalEpisodes}集`
}

function DetailEpisodeBtn({
  epIndex,
  pageStart,
  pageEnd,
  hasPrevPage,
  hasNextPage,
  sourcesLen,
  relatedRightmostId,
  selectedEpisode,
  setEpisode,
}: {
  epIndex: number
  pageStart: number
  pageEnd: number
  hasPrevPage: boolean
  hasNextPage: boolean
  sourcesLen: number
  /** 推荐区最右一张卡片（选集最左列 ← 回到推荐） */
  relatedRightmostId: string
  selectedEpisode: number
  setEpisode: (n: number) => void
}) {
  const offset = epIndex - pageStart
  const row = Math.floor(offset / EP_COLS)
  const col = offset % EP_COLS
  const rowsOnPage = Math.ceil((pageEnd - pageStart + 1) / EP_COLS)
  const isLastRow = row === rowsOnPage - 1
  const srcLast = `detail-src-${sourcesLen - 1}`

  const spatial = useTvSpatialNode(
    `detail-ep-${epIndex}`,
    () => {
      let downId: string | undefined
      if (isLastRow) {
        downId = undefined
      } else {
        const below = epIndex + EP_COLS
        if (below <= pageEnd) {
          downId = `detail-ep-${below}`
        } else {
          const row2Start = pageStart + EP_COLS
          if (pageEnd >= row2Start) {
            const row2Len = pageEnd - row2Start + 1
            const targetCol = Math.min(col, Math.max(0, row2Len - 1))
            downId = `detail-ep-${row2Start + targetCol}`
          } else {
            downId = undefined
          }
        }
      }

      return {
      up:
        row === 0
          ? epIndex === pageStart
            ? hasPrevPage
              ? 'detail-ep-page-prev'
              : hasNextPage
                ? 'detail-ep-page-next'
                : srcLast
            : srcLast
          : `detail-ep-${epIndex - EP_COLS}`,
      down: downId,
      left: col === 0 ? relatedRightmostId : `detail-ep-${epIndex - 1}`,
      right:
        epIndex < pageEnd
          ? `detail-ep-${epIndex + 1}`
          : hasNextPage
            ? 'detail-ep-page-next'
            : undefined,
      }
    },
    [
      epIndex,
      pageStart,
      pageEnd,
      hasPrevPage,
      hasNextPage,
      sourcesLen,
      relatedRightmostId,
      row,
      col,
      isLastRow,
    ]
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

function DetailEpisodePagerPrev({
  episodePage,
  pageStart,
  pageEnd,
  lastPage,
  sourcesLen,
  onPrev,
}: {
  episodePage: number
  pageStart: number
  pageEnd: number
  lastPage: number
  sourcesLen: number
  onPrev: () => void
}) {
  const hasNext = episodePage < lastPage
  const srcLast = `detail-src-${sourcesLen - 1}`
  const spatial = useTvSpatialNode(
    'detail-ep-page-prev',
    () => ({
      up: srcLast,
      down: `detail-ep-${pageStart}`,
      left: undefined,
      right: hasNext ? 'detail-ep-page-next' : `detail-ep-${pageEnd}`,
    }),
    [episodePage, pageStart, pageEnd, lastPage, sourcesLen, hasNext]
  )

  return (
    <button
      type="button"
      {...spatial}
      className="tv-focusable tab-focus h-9 w-9 rounded-lg bg-secondary text-secondary-foreground flex items-center justify-center hover:bg-surface-hover"
      onClick={onPrev}
      aria-label="上一页选集"
    >
      <ChevronLeft size={18} />
    </button>
  )
}

function DetailEpisodePagerNext({
  episodePage,
  pageStart,
  pageEnd,
  sourcesLen,
  onNext,
}: {
  episodePage: number
  pageStart: number
  pageEnd: number
  sourcesLen: number
  onNext: () => void
}) {
  const hasPrev = episodePage > 0
  const firstRowLast = Math.min(pageStart + EP_COLS - 1, pageEnd)
  const srcLast = `detail-src-${sourcesLen - 1}`
  const spatial = useTvSpatialNode(
    'detail-ep-page-next',
    () => ({
      up: srcLast,
      down: `detail-ep-${pageStart}`,
      left: hasPrev ? 'detail-ep-page-prev' : `detail-ep-${firstRowLast}`,
      right: undefined,
    }),
    [episodePage, pageStart, pageEnd, sourcesLen, hasPrev, firstRowLast]
  )

  return (
    <button
      type="button"
      {...spatial}
      className="tv-focusable tab-focus h-9 w-9 rounded-lg bg-secondary text-secondary-foreground flex items-center justify-center hover:bg-surface-hover"
      onClick={onNext}
      aria-label="下一页选集"
    >
      <ChevronRight size={18} />
    </button>
  )
}

function DetailRelatedCard({
  index,
  movie,
  relatedLen,
}: {
  index: number
  movie: Movie
  relatedLen: number
}) {
  const spatial = useTvSpatialNode(
    `detail-rel-${index}`,
    () => ({
      up: 'detail-play',
      left: index > 0 ? `detail-rel-${index - 1}` : 'detail-back',
      right: index < relatedLen - 1 ? `detail-rel-${index + 1}` : 'detail-src-0',
    }),
    [index, relatedLen]
  )

  return (
    <PosterCard
      movie={movie}
      size="xs"
      focusable={false}
      posterShellProps={{ ...spatial }}
      className="flex-shrink-0"
    />
  )
}

export function DetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [selectedEpisode, setSelectedEpisode] = useState(1)
  const [currentSource, setCurrentSource] = useState(0)
  const [episodePage, setEpisodePage] = useState(0)

  useTvSpatialMainEntry('detail-back')

  const movie = heroMovies.find(m => m.id === id) || heroMovies[0]
  const totalEpisodes = movie.episodes || 1

  useEffect(() => {
    setEpisodePage(0)
  }, [movie.id])

  useEffect(() => {
    if (totalEpisodes <= 1) return
    const idx = selectedEpisode - 1
    setEpisodePage(Math.floor(idx / EP_PER_PAGE))
  }, [selectedEpisode, totalEpisodes])

  const relatedMovies = movieList.filter(m => m.id !== movie.id).slice(0, 3)
  const relatedLen = relatedMovies.length
  const relatedRightmostId =
    relatedLen > 0 ? `detail-rel-${relatedLen - 1}` : 'detail-back'
  /** 无推荐卡时从返回/大播放按下落到立即播放 */
  const relatedFirstDownId = relatedLen > 0 ? 'detail-rel-0' : 'detail-playbtn'

  useEffect(() => {
    const id = requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        document
          .querySelector<HTMLElement>('[data-spatial-id="detail-back"]')
          ?.focus({ preventScroll: true })
      })
    })
    return () => cancelAnimationFrame(id)
  }, [movie.id])

  const spatialBack = useTvSpatialNode(
    'detail-back',
    () => ({
      left: 'nav-0',
      right: 'detail-play',
      down: relatedFirstDownId,
    }),
    [relatedFirstDownId]
  )
  const spatialPlay = useTvSpatialNode(
    'detail-play',
    () => ({
      left: 'detail-back',
      right: 'detail-playbtn',
      down: relatedFirstDownId,
    }),
    [relatedFirstDownId]
  )
  const spatialPlayBtn = useTvSpatialNode(
    'detail-playbtn',
    () => ({
      up: undefined,
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
      up: undefined,
      down: 'detail-src-0',
    }),
    []
  )

  const sources = ['蓝光', '超清', '高清', '标清', '备用源1', '备用源2']
  const sourcesLen = sources.length

  const episodeLastPage =
    totalEpisodes > 1 ? Math.max(0, Math.ceil(totalEpisodes / EP_PER_PAGE) - 1) : 0
  const pageStart = totalEpisodes > 1 ? episodePage * EP_PER_PAGE : 0
  const pageEnd =
    totalEpisodes > 1 ? Math.min(pageStart + EP_PER_PAGE - 1, totalEpisodes - 1) : 0
  const showEpisodePager = totalEpisodes > EP_PER_PAGE
  const hasPrevEpisodePage = episodePage > 0
  const hasNextEpisodePage = episodePage < episodeLastPage

  /** 播放源 / 选集向下落地：单集时进推荐首张（无推荐则立即播放）；多集时先进翻页或首集格 */
  const episodeSectionEntryId =
    totalEpisodes <= 1
      ? relatedLen > 0
        ? 'detail-rel-0'
        : 'detail-playbtn'
      : showEpisodePager && hasPrevEpisodePage
        ? 'detail-ep-page-prev'
        : showEpisodePager && hasNextEpisodePage
          ? 'detail-ep-page-next'
          : `detail-ep-${pageStart}`

  return (
    <div
      id="detail-page-anchor"
      className="min-h-full w-full flex flex-col bg-background"
    >
      <div className="flex flex-shrink-0 items-start">
        <div className="w-[55%] flex-shrink-0 flex flex-col p-8 pb-4 pr-5 min-w-0">
          <div className="relative w-full aspect-video rounded-2xl overflow-hidden shadow-[var(--shadow-elevated)] flex-shrink-0">
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
              className="tv-focusable absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-20 h-20 rounded-full bg-primary/90 backdrop-blur-sm flex items-center justify-center transition-[transform,box-shadow] duration-150 ease-out hover:scale-110 focus-visible:scale-110"
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

          <div className="mt-4 w-full min-w-0">
            <h3 className="text-sm font-medium text-foreground mb-2">相关推荐</h3>
            <div className="flex gap-2.5 justify-start flex-wrap">
              {relatedMovies.map((m, i) => (
                <DetailRelatedCard
                  key={m.id}
                  index={i}
                  movie={m}
                  relatedLen={relatedMovies.length}
                />
              ))}
            </div>
          </div>
        </div>

        <div className="flex-1 flex flex-col min-w-0 p-8 pl-0 pb-4">
          <div id="detail-right-anchor" className="mb-5">
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
            <p className="text-sm text-muted-foreground leading-relaxed max-w-[520px]">
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
                  episodeSectionEntryId={episodeSectionEntryId}
                  currentSource={currentSource}
                  setCurrentSource={setCurrentSource}
                />
              ))}
            </div>
          </div>

          {totalEpisodes > 1 && (
            <div id="detail-episodes-anchor" className="mb-5">
              <h3 className="text-base font-medium text-foreground mb-3">选集</h3>
              <div className="flex items-center justify-between gap-3 mb-3">
                <p className="text-sm text-muted-foreground">{episodeProgressLabel(movie, totalEpisodes)}</p>
                {showEpisodePager && (
                  <div className="flex items-center gap-2 flex-shrink-0">
                    {episodePage > 0 && (
                      <DetailEpisodePagerPrev
                        episodePage={episodePage}
                        pageStart={pageStart}
                        pageEnd={pageEnd}
                        lastPage={episodeLastPage}
                        sourcesLen={sourcesLen}
                        onPrev={() => setEpisodePage(p => Math.max(0, p - 1))}
                      />
                    )}
                    {episodePage < episodeLastPage && (
                      <DetailEpisodePagerNext
                        episodePage={episodePage}
                        pageStart={pageStart}
                        pageEnd={pageEnd}
                        sourcesLen={sourcesLen}
                        onNext={() =>
                          setEpisodePage(p => Math.min(episodeLastPage, p + 1))
                        }
                      />
                    )}
                  </div>
                )}
              </div>
              <div id="detail-ep-scroll" className="flex flex-col gap-2">
                {Array.from({ length: EP_ROWS }, (_, row) => (
                  <div key={row} className="grid grid-cols-8 gap-2">
                    {Array.from({ length: EP_COLS }, (_, c) => {
                      const epIndex = pageStart + row * EP_COLS + c
                      if (epIndex > pageEnd) {
                        return <div key={c} className="h-10" aria-hidden />
                      }
                      return (
                        <DetailEpisodeBtn
                          key={epIndex}
                          epIndex={epIndex}
                          pageStart={pageStart}
                          pageEnd={pageEnd}
                          hasPrevPage={episodePage > 0}
                          hasNextPage={episodePage < episodeLastPage}
                          sourcesLen={sourcesLen}
                          relatedRightmostId={relatedRightmostId}
                          selectedEpisode={selectedEpisode}
                          setEpisode={setSelectedEpisode}
                        />
                      )
                    })}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function SourceChip({
  label,
  index,
  sourcesLen,
  episodeSectionEntryId,
  currentSource,
  setCurrentSource,
}: {
  label: string
  index: number
  sourcesLen: number
  episodeSectionEntryId: string
  currentSource: number
  setCurrentSource: (n: number) => void
}) {
  const spatial = useTvSpatialNode(
    `detail-src-${index}`,
    () => ({
      up: 'detail-playbtn',
      down: episodeSectionEntryId,
      left: index === 0 ? 'detail-play' : `detail-src-${index - 1}`,
      right:
        index === sourcesLen - 1
          ? episodeSectionEntryId
          : `detail-src-${index + 1}`,
    }),
    [index, sourcesLen, episodeSectionEntryId]
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
