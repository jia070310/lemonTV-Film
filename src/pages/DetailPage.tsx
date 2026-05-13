import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { useTvSpatialMainEntry, useTvSpatialNode } from '@/tv/spatial'
import { cn } from '@/lib/utils'
import { PosterCard } from '@/components/PosterCard'
import type { Movie } from '@/data/mockData'
import {
  enrichMovieFromDetailRow,
  fetchProvideVod,
  fetchVodDetailById,
  mapVodRowToMovie,
  parsePlaySources,
} from '@/lib/maccmsApi'
import { isFavorite, LIBRARY_CHANGED_EVENT, toggleFavorite, upsertWatchHistory } from '@/lib/userLibraryStorage'
import {
  getEpisodePlaybackProgress,
  getLatestEpisodeProgressForVod,
  resumePositionSecFromRecord,
  saveEpisodePlaybackProgress,
} from '@/lib/playbackProgressStorage'
import {
  getPlaybackSettings,
  parseSpeedToNumber,
} from '@/lib/playbackSettingsStorage'
import { isWebPlayableUrl } from '@/lib/playbackVideoUrl'
import {
  ArrowLeft,
  Maximize2,
  Star,
  Calendar,
  MapPin,
  Clock,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  Volume2,
  VolumeX,
} from 'lucide-react'

const EP_COLS = 8
const EP_ROWS = 2
const EP_PER_PAGE = EP_COLS * EP_ROWS

/** 1×1 透明 PNG，避免 video 默认海报/灰底大播放按钮闪一下 */
const VIDEO_TRANSPARENT_POSTER =
  'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='

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
          ? 'tv-tab-selected'
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

function DetailDescToggleButton({
  expanded,
  onToggle,
}: {
  expanded: boolean
  onToggle: () => void
}) {
  const spatial = useTvSpatialNode(
    'detail-desc-toggle',
    () => ({
      down: 'detail-playbtn',
      up: undefined,
      left: undefined,
      right: undefined,
    }),
    []
  )

  return (
    <button
      type="button"
      {...spatial}
      className="tv-focusable mt-2 rounded-md px-1 py-1 text-sm font-medium text-primary outline-offset-2 hover:underline"
      onClick={onToggle}
      onKeyDown={(e) => {
        if (e.key === 'Enter') {
          e.preventDefault()
          onToggle()
        }
      }}
    >
      {expanded ? '收起' : '展开全文'}
    </button>
  )
}

function DetailSynopsis({
  movieId,
  text,
  onExpandableChange,
}: {
  movieId: string
  text: string | undefined
  onExpandableChange: (expandable: boolean) => void
}) {
  const display = (text ?? '').trim() || '暂无简介'
  const [expanded, setExpanded] = useState(false)
  const [canExpand, setCanExpand] = useState(false)
  const pRef = useRef<HTMLParagraphElement>(null)

  useLayoutEffect(() => {
    setExpanded(false)
  }, [movieId, display])

  useLayoutEffect(() => {
    if (display === '暂无简介') {
      setCanExpand(false)
      onExpandableChange(false)
      return
    }
    const id = requestAnimationFrame(() => {
      const el = pRef.current
      if (!el) {
        onExpandableChange(false)
        return
      }
      const more = el.scrollHeight > el.clientHeight + 2
      setCanExpand(more)
      onExpandableChange(more)
    })
    return () => cancelAnimationFrame(id)
  }, [display, movieId, onExpandableChange])

  return (
    <div className="max-w-[520px]">
      <p
        ref={pRef}
        className={cn(
          'text-sm text-muted-foreground leading-relaxed break-words',
          !expanded && 'line-clamp-2'
        )}
      >
        {display}
      </p>
      {canExpand && (
        <DetailDescToggleButton
          expanded={expanded}
          onToggle={() => setExpanded((v) => !v)}
        />
      )}
    </div>
  )
}

function DetailRelatedCard({
  index,
  movie,
  relatedLen,
  relatedUpId,
}: {
  index: number
  movie: Movie
  relatedLen: number
  /** 从推荐区向上：可播预览时先到静音，否则到全屏角标 */
  relatedUpId: string
}) {
  const spatial = useTvSpatialNode(
    `detail-rel-${index}`,
    () => ({
      up: relatedUpId,
      left: index > 0 ? `detail-rel-${index - 1}` : 'detail-back',
      right: index < relatedLen - 1 ? `detail-rel-${index + 1}` : 'detail-src-0',
    }),
    [index, relatedLen, relatedUpId]
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
  const location = useLocation()
  const [selectedEpisode, setSelectedEpisode] = useState(1)
  const [currentSource, setCurrentSource] = useState(0)
  const [episodePage, setEpisodePage] = useState(0)
  const [movie, setMovie] = useState<Movie | null>(null)
  const [playBundles, setPlayBundles] = useState<
    { name: string; episodes: { label: string; url: string }[] }[]
  >([{ name: '加载中', episodes: [{ label: '正片', url: '' }] }])
  const [relatedMovies, setRelatedMovies] = useState<Movie[]>([])
  const [loadError, setLoadError] = useState<string | null>(null)
  const [synopsisExpandable, setSynopsisExpandable] = useState(false)
  const [favorited, setFavorited] = useState(false)
  /** 详情预览：真正起播前保持遮罩并隐藏 video，避免 Android WebView 绘制系统大播放图标 */
  const [previewUiLoading, setPreviewUiLoading] = useState(true)
  /** 首帧已上屏后再显示 video 层，避免加载中与首帧之间闪原生大播放图标 */
  const [previewFirstFrameReady, setPreviewFirstFrameReady] = useState(false)
  /** 详情预览区：默认静音（浏览器策略），用户可点喇叭开声 */
  const [previewMuted, setPreviewMuted] = useState(true)

  const onSynopsisExpandableChange = useCallback((v: boolean) => {
    setSynopsisExpandable(v)
  }, [])

  useTvSpatialMainEntry('detail-back')

  useEffect(() => {
    if (!id) return
    let cancelled = false
    setLoadError(null)
    setSynopsisExpandable(false)
    ;(async () => {
      try {
        const row = await fetchVodDetailById(id)
        if (cancelled) return
        if (!row) {
          setLoadError('未找到影片')
          setMovie(null)
          setRelatedMovies([])
          return
        }
        const base = mapVodRowToMovie(row)
        const enriched = enrichMovieFromDetailRow(base, row)
        setMovie(enriched)
        setFavorited(isFavorite(enriched.id))
        const bundles = parsePlaySources(row)
        setPlayBundles(
          bundles.length > 0
            ? bundles
            : [{ name: '默认', episodes: [{ label: '正片', url: '' }] }]
        )
        const latest = getLatestEpisodeProgressForVod(enriched.id)
        if (latest && bundles.length > 0) {
          const src = Math.min(Math.max(0, latest.sourceIndex), bundles.length - 1)
          const eps = bundles[src]?.episodes ?? []
          const epMax = Math.max(1, eps.length)
          const ep = Math.min(Math.max(1, latest.episodeIndex), epMax)
          setCurrentSource(src)
          setSelectedEpisode(ep)
        } else {
          setCurrentSource(0)
          setSelectedEpisode(1)
        }
        setEpisodePage(0)
        const tid = Number(row.type_id)
        if (tid) {
          /** videolist 含完整海报字段；list 精简接口在部分环境下封面易异常 */
          const rel = await fetchProvideVod({
            ac: 'videolist',
            t: tid,
            pg: 1,
            pagesize: 20,
            sort_direction: 'desc',
          })
          if (cancelled) return
          const relList = (rel.list ?? [])
            .filter(r => String(r.vod_id) !== id)
            .slice(0, 3)
            .map(mapVodRowToMovie)
          setRelatedMovies(relList)
        } else {
          setRelatedMovies([])
        }
      } catch (e) {
        if (!cancelled) {
          setLoadError(e instanceof Error ? e.message : '加载失败')
          setMovie(null)
          setRelatedMovies([])
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [id])

  const bundleEpisodes = playBundles[currentSource]?.episodes ?? [
    { label: '正片', url: '' },
  ]
  const totalEpisodes = Math.max(1, bundleEpisodes.length)

  const previewUrl = useMemo(() => {
    const u = bundleEpisodes[selectedEpisode - 1]?.url?.trim() || ''
    return u
  }, [bundleEpisodes, selectedEpisode])

  const previewVideoRef = useRef<HTMLVideoElement>(null)

  const playerSearch = useMemo(() => {
    const sp = new URLSearchParams()
    sp.set('ep', String(selectedEpisode))
    sp.set('src', String(currentSource))
    return sp.toString()
  }, [selectedEpisode, currentSource])

  const goPlayer = useCallback(() => {
    if (!movie) return
    const run = () => {
      if (isWebPlayableUrl(previewUrl)) {
        const v = previewVideoRef.current
        const t = v && Number.isFinite(v.currentTime) ? Math.max(0, v.currentTime) : 0
        const dVideo = v && Number.isFinite(v.duration) && v.duration > 0 ? v.duration : 0
        const rec = getEpisodePlaybackProgress(movie.id, currentSource, selectedEpisode)
        const durationSec = dVideo > 0 ? dVideo : rec?.durationSec ?? 0
        /** 未等到 metadata 就进全屏时也要带上预览进度；接近 0 不写入以免覆盖旧续播点 */
        if (t > 0.25) {
          saveEpisodePlaybackProgress({
            vodId: movie.id,
            sourceIndex: currentSource,
            episodeIndex: selectedEpisode,
            positionSec: durationSec > 0 ? Math.min(t, durationSec - 0.25) : t,
            durationSec: Math.max(durationSec, t),
          })
        }
      }
      navigate(`/player/${movie.id}?${playerSearch}`)
    }
    requestAnimationFrame(run)
  }, [movie, navigate, playerSearch, previewUrl, currentSource, selectedEpisode])

  useLayoutEffect(() => {
    if (!movie || !isWebPlayableUrl(previewUrl)) {
      setPreviewUiLoading(false)
      setPreviewFirstFrameReady(false)
      return
    }
    const v = previewVideoRef.current
    if (!v) {
      setPreviewUiLoading(true)
      setPreviewFirstFrameReady(false)
      return
    }

    setPreviewFirstFrameReady(false)

    const show = () => setPreviewUiLoading(true)
    const hide = () => setPreviewUiLoading(false)

    setPreviewUiLoading(true)

    let fallback: number | undefined
    let rvfcHandle: number | undefined
    let onTime: (() => void) | undefined
    let frameWatchScheduled = false

    const clearFb = () => {
      if (fallback) {
        clearTimeout(fallback)
        fallback = undefined
      }
    }
    const armFallback = () => {
      clearFb()
      fallback = window.setTimeout(() => {
        fallback = undefined
        if (previewVideoRef.current !== v) return
        if (!v.paused || v.readyState >= HTMLMediaElement.HAVE_FUTURE_DATA) {
          hide()
          setPreviewFirstFrameReady(true)
        }
      }, 4000)
    }

    const markFirstFrame = () => {
      setPreviewFirstFrameReady(true)
    }

    const onLoadStart = () => {
      setPreviewFirstFrameReady(false)
      frameWatchScheduled = false
      show()
      armFallback()
    }

    const onPlaying = () => {
      clearFb()
      hide()
      if (frameWatchScheduled) return
      frameWatchScheduled = true
      if (typeof v.requestVideoFrameCallback === 'function') {
        rvfcHandle = v.requestVideoFrameCallback(() => {
          markFirstFrame()
        })
      } else {
        onTime = () => {
          if (v.currentTime > 0.02 || v.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA) {
            if (onTime) v.removeEventListener('timeupdate', onTime)
            onTime = undefined
            markFirstFrame()
          }
        }
        v.addEventListener('timeupdate', onTime)
      }
    }

    armFallback()
    v.addEventListener('loadstart', onLoadStart)
    v.addEventListener('playing', onPlaying)
    const onWaiting = () => {
      if (v.currentTime < 0.25 && !v.seeking) show()
    }
    v.addEventListener('waiting', onWaiting)
    const onError = () => {
      hide()
      setPreviewFirstFrameReady(true)
    }
    v.addEventListener('error', onError)

    return () => {
      clearFb()
      if (onTime) {
        v.removeEventListener('timeupdate', onTime)
        onTime = undefined
      }
      if (rvfcHandle != null && typeof v.cancelVideoFrameCallback === 'function') {
        try {
          v.cancelVideoFrameCallback(rvfcHandle)
        } catch {
          /* noop */
        }
        rvfcHandle = undefined
      }
      v.removeEventListener('loadstart', onLoadStart)
      v.removeEventListener('playing', onPlaying)
      v.removeEventListener('waiting', onWaiting)
      v.removeEventListener('error', onError)
    }
  }, [movie?.id, previewUrl, currentSource, selectedEpisode])

  useEffect(() => {
    const v = previewVideoRef.current
    if (!v || !isWebPlayableUrl(previewUrl) || !movie) return

    const applyTimeline = (withPlay: boolean) => {
      const settings = getPlaybackSettings()
      const rec = getEpisodePlaybackProgress(movie.id, currentSource, selectedEpisode)
      const d = v.duration || 0
      let t = 0
      const fromRec = resumePositionSecFromRecord(rec, d)
      if (fromRec != null) {
        t = fromRec
      } else if (
        settings.autoSkipIntroOutro &&
        settings.skipIntroEnabled &&
        settings.introSkipSec > 0
      ) {
        t = d > 0 ? Math.min(settings.introSkipSec, Math.max(0, d - 1)) : settings.introSkipSec
      }
      try {
        if (t > 0 && Number.isFinite(t) && Math.abs(v.currentTime - t) > 0.75) {
          v.currentTime = t
        }
      } catch {
        /* ignore */
      }
      v.playbackRate = parseSpeedToNumber(settings.defaultSpeed)
      if (withPlay) void v.play().catch(() => {})
    }

    const onMeta = () => {
      applyTimeline(true)
    }

    /** TV/WebView 上 duration 可能晚于 metadata，再对齐一次续播 */
    const onDurationChange = () => {
      applyTimeline(false)
    }

    const persist = () => {
      if (!v.duration || v.currentTime <= 0) return
      saveEpisodePlaybackProgress({
        vodId: movie.id,
        sourceIndex: currentSource,
        episodeIndex: selectedEpisode,
        positionSec: v.currentTime,
        durationSec: v.duration,
      })
      const pct = (v.currentTime / v.duration) * 100
      upsertWatchHistory(movie, Math.min(100, Math.max(0, pct)), {
        sourceIndex: currentSource,
        episodeIndex: selectedEpisode,
      })
    }

    v.addEventListener('loadedmetadata', onMeta)
    v.addEventListener('durationchange', onDurationChange)
    const timer = window.setInterval(persist, 5000)
    return () => {
      v.removeEventListener('loadedmetadata', onMeta)
      v.removeEventListener('durationchange', onDurationChange)
      window.clearInterval(timer)
      persist()
    }
  }, [previewUrl, movie, currentSource, selectedEpisode])

  /** 从播放页 SPA 返回时对齐本地续播点（pageshow/focus 在 WebView 里不一定触发） */
  useEffect(() => {
    const v = previewVideoRef.current
    if (!v || !movie || !isWebPlayableUrl(previewUrl)) return
    const path = `/detail/${movie.id}`
    if (location.pathname !== path) return

    const pullProgress = () => {
      const rec = getEpisodePlaybackProgress(movie.id, currentSource, selectedEpisode)
      const pos = resumePositionSecFromRecord(rec, v.duration || rec?.durationSec || 0)
      if (pos == null) return
      const d = v.duration || rec?.durationSec || 0
      if (d > 0 && pos >= d - 0.5) return
      if (Math.abs(v.currentTime - pos) <= 1.5) return
      try {
        v.currentTime = pos
      } catch {
        /* ignore */
      }
    }

    pullProgress()
    const r1 = requestAnimationFrame(() => pullProgress())
    const t = window.setTimeout(pullProgress, 150)
    return () => {
      cancelAnimationFrame(r1)
      clearTimeout(t)
    }
  }, [
    location.key,
    location.pathname,
    movie?.id,
    previewUrl,
    currentSource,
    selectedEpisode,
  ])

  /** 从全屏返回等场景：本地进度可能已更新，把预览对齐到存储（与播放页同一 key） */
  useEffect(() => {
    const v = previewVideoRef.current
    if (!v || !movie || !isWebPlayableUrl(previewUrl)) return

    const pullProgress = () => {
      const rec = getEpisodePlaybackProgress(movie.id, currentSource, selectedEpisode)
      const pos = resumePositionSecFromRecord(rec, v.duration || rec?.durationSec || 0)
      if (pos == null) return
      const d = v.duration || rec?.durationSec || 0
      if (d > 0 && pos >= d - 0.5) return
      if (Math.abs(v.currentTime - pos) <= 1.5) return
      try {
        v.currentTime = pos
      } catch {
        /* ignore */
      }
    }

    const onPageShow = () => {
      if (v.readyState >= 1) pullProgress()
      else {
        const once = () => {
          pullProgress()
          v.removeEventListener('loadedmetadata', once)
        }
        v.addEventListener('loadedmetadata', once)
      }
    }

    window.addEventListener('pageshow', onPageShow)
    window.addEventListener('focus', pullProgress)
    return () => {
      window.removeEventListener('pageshow', onPageShow)
      window.removeEventListener('focus', pullProgress)
    }
  }, [movie, previewUrl, currentSource, selectedEpisode])

  useEffect(() => {
    setEpisodePage(0)
  }, [id])

  useEffect(() => {
    setPreviewMuted(true)
  }, [id])

  useEffect(() => {
    setSelectedEpisode(e => Math.min(e, totalEpisodes))
  }, [totalEpisodes, currentSource])

  useEffect(() => {
    if (totalEpisodes <= 1) return
    const idx = selectedEpisode - 1
    setEpisodePage(Math.floor(idx / EP_PER_PAGE))
  }, [selectedEpisode, totalEpisodes])

  const relatedLen = relatedMovies.length
  const relatedRightmostId =
    relatedLen > 0 ? `detail-rel-${relatedLen - 1}` : 'detail-back'
  /** 无推荐卡时从返回/全屏角标按下落到全屏播放按钮 */
  const relatedFirstDownId = relatedLen > 0 ? 'detail-rel-0' : 'detail-playbtn'

  useEffect(() => {
    if (!movie) {
      setFavorited(false)
      return
    }
    setFavorited(isFavorite(movie.id))
    const onLib = () => setFavorited(isFavorite(movie.id))
    window.addEventListener(LIBRARY_CHANGED_EVENT, onLib)
    return () => window.removeEventListener(LIBRARY_CHANGED_EVENT, onLib)
  }, [movie])

  useEffect(() => {
    const raf = requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        document
          .querySelector<HTMLElement>('[data-spatial-id="detail-back"]')
          ?.focus({ preventScroll: true })
      })
    })
    return () => cancelAnimationFrame(raf)
  }, [id])

  const previewPlayable = isWebPlayableUrl(previewUrl)

  const spatialBack = useTvSpatialNode(
    'detail-back',
    () => ({
      left: 'nav-0',
      right: previewPlayable ? 'detail-preview-mute' : 'detail-fullscreen',
      down: previewPlayable ? 'detail-preview-mute' : relatedFirstDownId,
    }),
    [relatedFirstDownId, previewPlayable]
  )

  const spatialPreviewMute = useTvSpatialNode(
    'detail-preview-mute',
    () =>
      !previewPlayable
        ? {}
        : {
            up: 'detail-back',
            right: 'detail-fullscreen',
            down: relatedFirstDownId,
          },
    [previewPlayable, relatedFirstDownId]
  )

  const spatialFullscreen = useTvSpatialNode(
    'detail-fullscreen',
    () => ({
      left: previewPlayable ? 'detail-preview-mute' : 'detail-back',
      right: 'detail-playbtn',
      down: relatedFirstDownId,
    }),
    [previewPlayable, relatedFirstDownId]
  )
  const spatialPlayBtn = useTvSpatialNode(
    'detail-playbtn',
    () => ({
      up: synopsisExpandable ? 'detail-desc-toggle' : undefined,
      left: 'detail-fullscreen',
      right: 'detail-fav',
      down: 'detail-src-0',
    }),
    [synopsisExpandable]
  )
  const spatialFav = useTvSpatialNode(
    'detail-fav',
    () => ({
      left: 'detail-playbtn',
      right: 'detail-src-0',
      up: synopsisExpandable ? 'detail-desc-toggle' : undefined,
      down: 'detail-src-0',
    }),
    [synopsisExpandable]
  )

  const sources = playBundles.map(b => b.name)
  const sourcesLen = Math.max(1, sources.length)

  const episodeLastPage =
    totalEpisodes > 1 ? Math.max(0, Math.ceil(totalEpisodes / EP_PER_PAGE) - 1) : 0
  const pageStart = totalEpisodes > 1 ? episodePage * EP_PER_PAGE : 0
  const pageEnd =
    totalEpisodes > 1 ? Math.min(pageStart + EP_PER_PAGE - 1, totalEpisodes - 1) : 0
  const showEpisodePager = totalEpisodes > EP_PER_PAGE
  const hasPrevEpisodePage = episodePage > 0
  const hasNextEpisodePage = episodePage < episodeLastPage

  /** 播放源 / 选集向下落地：单集时进推荐首张（无推荐则全屏播放按钮）；多集时先进翻页或首集格 */
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

  if (!movie) {
    return (
      <div className="flex min-h-full flex-col items-center justify-center gap-3 bg-background p-8 text-center">
        <p className="text-lg text-foreground">{loadError || '加载中…'}</p>
        {loadError && (
          <button
            type="button"
            className="tv-focusable rounded-full bg-secondary px-5 py-2 text-sm"
            onClick={() => navigate(-1)}
          >
            返回
          </button>
        )}
      </div>
    )
  }

  return (
    <div
      id="detail-page-anchor"
      className="min-h-full w-full flex flex-col bg-background"
    >
      <div className="flex flex-shrink-0 items-start">
        <div className="w-[55%] flex-shrink-0 flex flex-col p-8 pb-4 pr-5 min-w-0">
          <div className="relative w-full aspect-video rounded-2xl overflow-hidden shadow-[var(--shadow-elevated)] flex-shrink-0 bg-black">
            {isWebPlayableUrl(previewUrl) ? (
              <>
                <video
                  key={`${movie.id}-${currentSource}-${selectedEpisode}-${previewUrl}`}
                  ref={previewVideoRef}
                  className={cn(
                    'detail-preview-video absolute inset-0 z-[2] h-full w-full object-contain transition-opacity duration-200',
                    previewFirstFrameReady ? 'opacity-100' : 'opacity-0'
                  )}
                  src={previewUrl}
                  poster={VIDEO_TRANSPARENT_POSTER}
                  playsInline
                  muted={previewMuted}
                  controls={false}
                  preload="auto"
                />
                {previewUiLoading ? (
                  <div className="pointer-events-none absolute inset-0 z-[5] flex items-center justify-center bg-black text-base text-muted-foreground">
                    加载中
                  </div>
                ) : null}
              </>
            ) : (
              <div className="absolute inset-0 z-[1] flex items-center justify-center bg-black px-4 text-center text-sm text-muted-foreground">
                当前线路无法在页面内预览，请使用右下角全屏进入播放页
              </div>
            )}

            <button
              type="button"
              {...spatialBack}
              className="tv-focusable pill-focus absolute top-6 left-6 z-10 w-10 h-10 rounded-full bg-background/50 backdrop-blur-sm flex items-center justify-center"
              onClick={() => navigate(-1)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') navigate(-1)
              }}
            >
              <ArrowLeft size={20} className="text-foreground" />
            </button>

            <button
              type="button"
              {...spatialFullscreen}
              className="tv-focusable pill-focus absolute bottom-6 right-6 z-10 flex h-12 w-12 items-center justify-center rounded-full bg-background/60 text-foreground backdrop-blur-sm"
              onClick={goPlayer}
              onKeyDown={(e) => {
                if (e.key === 'Enter') goPlayer()
              }}
              aria-label="全屏播放（续当前预览进度）"
            >
              <Maximize2 size={22} strokeWidth={2} />
            </button>

            <div className="absolute bottom-6 left-6 z-10 flex max-w-[calc(100%-5rem)] items-end gap-3 text-foreground">
              {previewPlayable ? (
                <button
                  type="button"
                  {...spatialPreviewMute}
                  className="tv-focusable pill-focus flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-background/60 text-foreground backdrop-blur-sm"
                  aria-label={previewMuted ? '开启声音' : '静音'}
                  aria-pressed={!previewMuted}
                  onClick={() => setPreviewMuted((m) => !m)}
                >
                  {previewMuted ? (
                    <VolumeX size={22} strokeWidth={2} className="text-foreground" />
                  ) : (
                    <Volume2 size={22} strokeWidth={2} className="text-foreground" />
                  )}
                </button>
              ) : null}
              <div className="min-w-0 pb-0.5">
                <p className="text-sm text-muted-foreground">
                  正在播放:{' '}
                  <span className="inline-flex rounded-md bg-white/50 px-2 py-0.5 text-sm font-medium text-yellow-400 shadow-sm backdrop-blur-sm">
                    {totalEpisodes > 1 ? `第 ${selectedEpisode} 集` : '正片'}
                  </span>
                </p>
              </div>
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
                  relatedUpId={previewPlayable ? 'detail-preview-mute' : 'detail-fullscreen'}
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
            <DetailSynopsis
              movieId={movie.id}
              text={movie.description}
              onExpandableChange={onSynopsisExpandableChange}
            />
          </div>

          <div className="flex items-center gap-3 mb-5">
            <button
              type="button"
              {...spatialPlayBtn}
              className="tv-focusable detail-play-pill pill-focus flex items-center gap-2 px-6 py-2.5 rounded-full font-medium text-sm"
              onClick={goPlayer}
              onKeyDown={(e) => {
                if (e.key === 'Enter') goPlayer()
              }}
              aria-label="全屏播放（续当前预览进度）"
            >
              <Maximize2 size={18} strokeWidth={2} />
              全屏播放
            </button>
            <button
              type="button"
              {...spatialFav}
              className={cn(
                'tv-focusable pill-focus px-6 py-2.5 rounded-full font-medium text-sm',
                favorited
                  ? 'bg-primary text-primary-foreground'
                  : 'bg-secondary text-secondary-foreground'
              )}
              onClick={() => setFavorited(toggleFavorite(movie))}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault()
                  setFavorited(toggleFavorite(movie))
                }
              }}
            >
              {favorited ? '已收藏' : '收藏'}
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
                  firstChipLeftId={relatedLen > 0 ? relatedRightmostId : 'detail-fullscreen'}
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
  firstChipLeftId,
}: {
  label: string
  index: number
  sourcesLen: number
  episodeSectionEntryId: string
  currentSource: number
  setCurrentSource: (n: number) => void
  /** 首枚播放源「左」：有推荐时从最后一张推荐卡进入，否则从预览全屏角标 */
  firstChipLeftId: string
}) {
  const spatial = useTvSpatialNode(
    `detail-src-${index}`,
    () => ({
      up: 'detail-playbtn',
      down: episodeSectionEntryId,
      left: index === 0 ? firstChipLeftId : `detail-src-${index - 1}`,
      right:
        index === sourcesLen - 1
          ? episodeSectionEntryId
          : `detail-src-${index + 1}`,
    }),
    [index, sourcesLen, episodeSectionEntryId, firstChipLeftId]
  )

  return (
    <button
      type="button"
      {...spatial}
      className={cn(
        'tv-focusable tab-focus px-4 py-2 rounded-full text-sm font-medium transition-all duration-200',
        index === currentSource
          ? 'tv-tab-selected'
          : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
      )}
      onClick={() => setCurrentSource(index)}
    >
      {label}
    </button>
  )
}
