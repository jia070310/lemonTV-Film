import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { triggerAppBackNavigation } from '@/components/AppBackHandler'
import { PlayerProgressBar } from '@/components/PlayerProgressBar'
import { cn } from '@/lib/utils'
import { setPlayerPanelBackCloser } from '@/lib/playerPanelBack'
import { queryFocusableSpatial } from '@/tv/spatial/spatialFocus'
import { useTvSpatialMainEntry, useTvSpatialNode } from '@/tv/spatial'
import type { Movie } from '@/data/mockData'
import {
  enrichMovieFromDetailRow,
  fetchVodDetailById,
  mapVodRowToMovie,
  parsePlaySources,
} from '@/lib/maccmsApi'
import {
  getEpisodePlaybackProgress,
  resumePositionSecFromRecord,
  saveEpisodePlaybackProgress,
} from '@/lib/playbackProgressStorage'
import {
  getPlaybackSettings,
  parseSpeedToNumber,
  savePlaybackSettings,
} from '@/lib/playbackSettingsStorage'
import { isWebPlayableUrl } from '@/lib/playbackVideoUrl'
import { upsertWatchHistory } from '@/lib/userLibraryStorage'
import {
  ArrowLeft,
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Gauge,
  List,
  RefreshCw,
  FastForward,
  X,
  AlertTriangle,
  RotateCcw,
} from 'lucide-react'

type PanelType = 'speed' | 'episodes' | 'source' | 'skip' | null
type ControlsMode = 'nav' | 'seek' | null
type FocusZone = 'top' | 'progress' | 'bottom' | 'none'

const CONTROLS_AUTO_HIDE_MS = 6000
const SEEK_DELTA_SECONDS = 15
const SPEED_OPTIONS = ['0.5x', '1.0x', '1.25x', '1.5x', '2.0x'] as const

function formatDuration(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds <= 0) return '00:00'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  if (h > 0)
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function getPanelSpatialMainId(
  panel: NonNullable<PanelType>,
  currentEpisode: number,
  currentSource: number,
  currentSpeedLabel: string
): string {
  switch (panel) {
    case 'speed': {
      const i = SPEED_OPTIONS.indexOf(currentSpeedLabel as (typeof SPEED_OPTIONS)[number])
      return `player-panel-speed-${i >= 0 ? i : 1}`
    }
    case 'episodes':
      return `player-panel-episodes-${Math.max(0, currentEpisode - 1)}`
    case 'source':
      return `player-panel-source-${currentSource}`
    case 'skip':
      return 'player-panel-skip-close'
  }
}

function PlayerSpeedCell({
  speed,
  idx,
  currentSpeed,
  onSelect,
}: {
  speed: string
  idx: number
  currentSpeed: string
  onSelect: (s: string) => void
}) {
  const cols = 3
  const n = SPEED_OPTIONS.length
  const row = Math.floor(idx / cols)
  const col = idx % cols
  const spatial = useTvSpatialNode(
    `player-panel-speed-${idx}`,
    () => ({
      up: row === 0 ? 'player-panel-speed-close' : `player-panel-speed-${idx - cols}`,
      down: idx + cols < n ? `player-panel-speed-${idx + cols}` : undefined,
      left: col === 0 ? undefined : `player-panel-speed-${idx - 1}`,
      right: col < cols - 1 && idx + 1 < n ? `player-panel-speed-${idx + 1}` : undefined,
    }),
    [idx, n]
  )
  return (
    <button
      {...spatial}
      type="button"
      className={cn(
        'tv-focusable tab-focus rounded-lg py-2.5 text-sm font-medium transition-all',
        speed === currentSpeed
          ? 'tv-tab-selected'
          : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
      )}
      onClick={() => onSelect(speed)}
    >
      {speed}
    </button>
  )
}

function EpisodeCell({
  ep,
  idx,
  total,
  isCurrent,
  onSelect,
}: {
  ep: number
  idx: number
  total: number
  isCurrent: boolean
  onSelect: (ep: number) => void
}) {
  const cols = 4
  const row = Math.floor(idx / cols)
  const col = idx % cols
  const spatial = useTvSpatialNode(
    `player-panel-episodes-${idx}`,
    () => ({
      up: row === 0 ? 'player-panel-episodes-close' : `player-panel-episodes-${idx - cols}`,
      down: idx + cols < total ? `player-panel-episodes-${idx + cols}` : undefined,
      left: col === 0 ? undefined : `player-panel-episodes-${idx - 1}`,
      right: col === cols - 1 || idx + 1 >= total ? undefined : `player-panel-episodes-${idx + 1}`,
    }),
    [idx, total]
  )
  return (
    <button
      {...spatial}
      type="button"
      className={cn(
        'tv-focusable tab-focus rounded-lg py-2.5 text-sm font-medium transition-all',
        isCurrent ? 'tv-tab-selected' : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
      )}
      onClick={() => onSelect(ep)}
    >
      {ep}
    </button>
  )
}

function SourceRow({
  idx,
  n,
  label,
  isCurrent,
  onSelect,
}: {
  idx: number
  n: number
  label: string
  isCurrent: boolean
  onSelect: (idx: number) => void
}) {
  const spatial = useTvSpatialNode(
    `player-panel-source-${idx}`,
    () => ({
      up: idx === 0 ? 'player-panel-source-close' : `player-panel-source-${idx - 1}`,
      down: idx + 1 < n ? `player-panel-source-${idx + 1}` : undefined,
    }),
    [idx, n]
  )
  return (
    <button
      {...spatial}
      type="button"
      className={cn(
        'tv-focusable tab-focus flex items-center justify-between rounded-lg py-3 px-4 text-left text-sm font-medium transition-all',
        isCurrent ? 'tv-tab-selected' : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
      )}
      onClick={() => onSelect(idx)}
    >
      <span>{label}</span>
      {isCurrent && <span className="text-xs opacity-80">当前</span>}
    </button>
  )
}

export function PlayerPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()

  const [movie, setMovie] = useState<Movie | null>(null)
  const [playBundles, setPlayBundles] = useState<
    { name: string; episodes: { label: string; url: string }[] }[]
  >([])
  const [currentEpisode, setCurrentEpisode] = useState(1)
  const [currentSource, setCurrentSource] = useState(0)

  const [isPlaying, setIsPlaying] = useState(true)
  const [showControls, setShowControls] = useState(true)
  const [currentTime, setCurrentTime] = useState(0)
  const [duration, setDuration] = useState(0)
  const [seekOverlay, setSeekOverlay] = useState<{
    direction: 'forward' | 'backward'
    seconds: number
  } | null>(null)
  const [activePanel, setActivePanel] = useState<PanelType>(null)
  const [currentSpeedLabel, setCurrentSpeedLabel] = useState(() => getPlaybackSettings().defaultSpeed)
  const speedLabelRef = useRef(currentSpeedLabel)
  speedLabelRef.current = currentSpeedLabel
  const [systemTime, setSystemTime] = useState('')
  const [progressFocused, setProgressFocused] = useState(false)

  const [controlsMode, setControlsMode] = useState<ControlsMode>('nav')
  const [focusedZone, setFocusedZone] = useState<FocusZone>('none')

  const [isLoading, setIsLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const videoRef = useRef<HTMLVideoElement>(null)
  const hideTimerRef = useRef<ReturnType<typeof setTimeout>>()
  const seekTimerRef = useRef<ReturnType<typeof setTimeout>>()
  const progressBarRef = useRef<HTMLDivElement>(null)
  const movieRef = useRef<Movie | null>(null)
  const durationRef = useRef(0)
  const currentTimeRef = useRef(0)
  const currentSourceRef = useRef(0)
  const currentEpisodeRef = useRef(1)
  const outroTailSkipRef = useRef(false)

  const displayMovie: Movie =
    movie ?? {
      id: id || '0',
      title: '加载中…',
      poster: '/images/movie-poster-1.png',
      rating: 0,
      year: '—',
      genre: '—',
      area: '—',
    }

  const bundleEpisodes = playBundles[currentSource]?.episodes ?? []
  const totalEpisodes = Math.max(1, bundleEpisodes.length)
  const playUrl = useMemo(() => {
    const ep = bundleEpisodes[currentEpisode - 1]
    return ep?.url?.trim() || ''
  }, [bundleEpisodes, currentEpisode])

  const sources = playBundles.map((b) => b.name)

  const spatialMainId = useMemo(() => {
    if (activePanel)
      return getPanelSpatialMainId(activePanel, currentEpisode, currentSource, currentSpeedLabel)
    if (showControls) return 'player-play'
    return null
  }, [activePanel, showControls, currentEpisode, currentSource, currentSpeedLabel])

  useTvSpatialMainEntry(spatialMainId)

  const spatialBack = useTvSpatialNode(
    'player-back',
    () => (!showControls || activePanel ? {} : { down: 'player-progress' }),
    [showControls, activePanel]
  )

  const spatialProgress = useTvSpatialNode(
    'player-progress',
    () => (!showControls || activePanel ? {} : { up: 'player-back', down: 'player-play' }),
    [showControls, activePanel]
  )

  const spatialPrev = useTvSpatialNode(
    'player-prev',
    () => (!showControls || activePanel ? {} : { up: 'player-progress', right: 'player-play' }),
    [showControls, activePanel]
  )

  const spatialPlay = useTvSpatialNode(
    'player-play',
    () =>
      !showControls || activePanel
        ? {}
        : {
            up: 'player-progress',
            left: 'player-prev',
            right: 'player-next',
            down: 'player-speed',
          },
    [showControls, activePanel]
  )

  const spatialNext = useTvSpatialNode(
    'player-next',
    () =>
      !showControls || activePanel
        ? {}
        : { up: 'player-progress', left: 'player-play', right: 'player-speed' },
    [showControls, activePanel]
  )

  const spatialSpeed = useTvSpatialNode(
    'player-speed',
    () => {
      if (!showControls || activePanel) return {}
      const rightAfterSpeed =
        totalEpisodes > 1 ? 'player-episodes' : playBundles.length > 0 ? 'player-source' : 'player-skip'
      return {
        up: 'player-progress',
        left: 'player-next',
        right: rightAfterSpeed,
      }
    },
    [showControls, activePanel, totalEpisodes, playBundles.length]
  )

  const spatialEpisodes = useTvSpatialNode(
    'player-episodes',
    () => {
      if (!showControls || activePanel) return {}
      const right = playBundles.length > 0 ? 'player-source' : 'player-skip'
      return { up: 'player-progress', left: 'player-speed', right }
    },
    [showControls, activePanel, playBundles.length]
  )

  const spatialSource = useTvSpatialNode(
    'player-source',
    () => {
      if (!showControls || activePanel) return {}
      const left = totalEpisodes > 1 ? 'player-episodes' : 'player-speed'
      return { up: 'player-progress', left, right: 'player-skip' }
    },
    [showControls, activePanel, totalEpisodes]
  )

  const spatialSkip = useTvSpatialNode(
    'player-skip',
    () => {
      if (!showControls || activePanel) return {}
      const left =
        playBundles.length > 0 ? 'player-source' : totalEpisodes > 1 ? 'player-episodes' : 'player-speed'
      return { up: 'player-progress', left }
    },
    [showControls, activePanel, playBundles.length, totalEpisodes]
  )

  useEffect(() => {
    if (!activePanel) {
      setPlayerPanelBackCloser(null)
      return
    }
    setPlayerPanelBackCloser(() => {
      setActivePanel(null)
      return true
    })
    const focusId = getPanelSpatialMainId(
      activePanel,
      currentEpisode,
      currentSource,
      currentSpeedLabel
    )
    const tid = window.setTimeout(() => {
      queryFocusableSpatial(focusId)?.focus({ preventScroll: true })
    }, 0)
    return () => {
      clearTimeout(tid)
      setPlayerPanelBackCloser(null)
    }
  }, [activePanel, currentEpisode, currentSource, currentSpeedLabel])

  const syncUrl = useCallback(
    (ep: number, src: number) => {
      const next = new URLSearchParams(searchParams)
      next.set('ep', String(ep))
      next.set('src', String(src))
      setSearchParams(next, { replace: true })
    },
    [searchParams, setSearchParams]
  )

  useEffect(() => {
    if (!id) return
    const ep = Math.max(1, parseInt(searchParams.get('ep') || '1', 10) || 1)
    const src = Math.max(0, parseInt(searchParams.get('src') || '0', 10) || 0)
    setCurrentEpisode(ep)
    setCurrentSource(src)
  }, [id, searchParams])

  useEffect(() => {
    if (!id) return
    let cancelled = false
    setErrorMessage(null)
    setIsLoading(true)
    setPlayBundles([])
    ;(async () => {
      try {
        const row = await fetchVodDetailById(id)
        if (cancelled) return
        if (!row) {
          setMovie(null)
          setPlayBundles([])
          setErrorMessage('未找到影片')
          return
        }
        const base = mapVodRowToMovie(row)
        const enriched = enrichMovieFromDetailRow(base, row)
        setMovie(enriched)
        const bundles = parsePlaySources(row)
        setPlayBundles(
          bundles.length > 0
            ? bundles
            : [{ name: '默认', episodes: [{ label: '正片', url: '' }] }]
        )
      } catch (e) {
        if (!cancelled) {
          setMovie(null)
          setPlayBundles([])
          setErrorMessage(e instanceof Error ? e.message : '加载失败')
        }
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [id])

  useEffect(() => {
    setCurrentSource((s) => Math.min(s, Math.max(0, playBundles.length - 1)))
  }, [playBundles.length])

  /** 须在 playBundles 拉取完成后再夹集数，否则首帧 bundle 为空会把 URL 里的 ep 误夹成 1 */
  useEffect(() => {
    if (playBundles.length === 0) return
    setCurrentEpisode((e) => Math.min(Math.max(1, e), Math.max(1, bundleEpisodes.length)))
  }, [bundleEpisodes.length, playBundles.length])

  useEffect(() => {
    const updateTime = () => {
      const now = new Date()
      setSystemTime(
        `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
      )
    }
    updateTime()
    const interval = setInterval(updateTime, 30000)
    return () => clearInterval(interval)
  }, [])

  const resetHideTimer = useCallback(() => {
    if (hideTimerRef.current) clearTimeout(hideTimerRef.current)
    setShowControls(true)
    if (activePanel) return
    hideTimerRef.current = setTimeout(() => {
      setShowControls(false)
      setControlsMode(null)
      setFocusedZone('none')
    }, CONTROLS_AUTO_HIDE_MS)
  }, [activePanel])

  useEffect(() => {
    resetHideTimer()
    return () => {
      if (hideTimerRef.current) clearTimeout(hideTimerRef.current)
    }
  }, [resetHideTimer])

  const applyLoadedPosition = useCallback(() => {
    const v = videoRef.current
    if (!v || !id || !isWebPlayableUrl(playUrl)) return
    const settings = getPlaybackSettings()
    const rec = getEpisodePlaybackProgress(id, currentSource, currentEpisode)
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
    v.currentTime = t
    v.playbackRate = parseSpeedToNumber(speedLabelRef.current)
    setCurrentTime(t)
    setDuration(d)
  }, [id, playUrl, currentSource, currentEpisode])

  const goNextEpisode = useCallback(() => {
    if (currentEpisode >= totalEpisodes) return
    const ne = currentEpisode + 1
    setCurrentEpisode(ne)
    syncUrl(ne, currentSource)
  }, [currentEpisode, totalEpisodes, currentSource, syncUrl])

  const goPrevEpisode = useCallback(() => {
    if (currentEpisode <= 1) return
    const ne = currentEpisode - 1
    setCurrentEpisode(ne)
    syncUrl(ne, currentSource)
  }, [currentEpisode, currentSource, syncUrl])

  useEffect(() => {
    if (!id) return
    setCurrentSpeedLabel(getPlaybackSettings().defaultSpeed)
  }, [id])

  useEffect(() => {
    outroTailSkipRef.current = false
    setCurrentTime(0)
    setDuration(0)
    const v = videoRef.current
    if (!v) return
    if (!isWebPlayableUrl(playUrl)) {
      v.removeAttribute('src')
      return
    }
    v.src = playUrl
    const syncDurationFromVideo = () => {
      const d = v.duration || 0
      if (Number.isFinite(d) && d > 0) {
        durationRef.current = d
        setDuration((prev) => (Math.abs(prev - d) > 0.05 ? d : prev))
      }
    }

    const onMeta = () => {
      syncDurationFromVideo()
      applyLoadedPosition()
      void v.play().catch(() => setIsPlaying(false))
    }

    const onDurationChange = () => {
      syncDurationFromVideo()
      const v0 = videoRef.current
      if (!v0 || !id || !isWebPlayableUrl(playUrl)) return
      if (!Number.isFinite(v0.duration) || v0.duration <= 0) return
      const rec0 = getEpisodePlaybackProgress(id, currentSource, currentEpisode)
      const pos = resumePositionSecFromRecord(rec0, v0.duration)
      if (pos != null && pos > 1 && v0.currentTime < 1) {
        v0.currentTime = pos
        currentTimeRef.current = pos
        setCurrentTime(pos)
      }
    }

    const onTime = () => {
      const ct = v.currentTime
      const d = v.duration || 0
      setCurrentTime(ct)
      currentTimeRef.current = ct
      if (Number.isFinite(d) && d > 0) {
        durationRef.current = d
        setDuration((prev) => (Math.abs(prev - d) > 0.05 ? d : prev))
      }

      const settings = getPlaybackSettings()
      if (
        settings.autoSkipIntroOutro &&
        settings.skipOutroEnabled &&
        settings.outroSkipSec > 0 &&
        d > 0 &&
        !outroTailSkipRef.current
      ) {
        const tail = Math.min(settings.outroSkipSec, d * 0.45)
        if (ct >= d - tail - 0.12) {
          outroTailSkipRef.current = true
          v.currentTime = Math.max(0, d - 0.1)
        }
      }
    }

    /** 部分 WebView 对 timeupdate 节流很狠，播放中用短间隔从 video 拉当前时间，保证进度条跟手 */
    const PLAYBACK_UI_TICK_MS = 250
    let uiTick: number | undefined
    const tickPlaybackUi = () => {
      if (v.paused) return
      const ct = v.currentTime
      const d = v.duration || 0
      setCurrentTime(ct)
      currentTimeRef.current = ct
      if (Number.isFinite(d) && d > 0) {
        durationRef.current = d
        setDuration((prev) => (Math.abs(prev - d) > 0.05 ? d : prev))
      }
    }
    const startUiTick = () => {
      if (uiTick != null) return
      uiTick = window.setInterval(tickPlaybackUi, PLAYBACK_UI_TICK_MS) as unknown as number
    }
    const stopUiTick = () => {
      if (uiTick != null) {
        window.clearInterval(uiTick)
        uiTick = undefined
      }
    }
    const onEnded = () => {
      stopUiTick()
      setIsPlaying(false)
      const settings = getPlaybackSettings()
      if (settings.autoPlayNext && currentEpisode < totalEpisodes) {
        goNextEpisode()
      }
    }
    const onPlay = () => setIsPlaying(true)
    const onPlaying = () => {
      startUiTick()
    }
    const onPause = () => {
      stopUiTick()
      setIsPlaying(false)
    }
    const onError = () => {
      stopUiTick()
      setErrorMessage('视频无法播放，请尝试换源')
      setIsPlaying(false)
    }

    v.addEventListener('loadedmetadata', onMeta)
    v.addEventListener('durationchange', onDurationChange)
    v.addEventListener('timeupdate', onTime)
    v.addEventListener('playing', onPlaying)
    v.addEventListener('ended', onEnded)
    v.addEventListener('play', onPlay)
    v.addEventListener('pause', onPause)
    v.addEventListener('error', onError)
    void v.load()

    const persistTimer = window.setInterval(() => {
      if (!id || !v.duration || v.currentTime <= 0) return
      saveEpisodePlaybackProgress({
        vodId: id,
        sourceIndex: currentSource,
        episodeIndex: currentEpisode,
        positionSec: v.currentTime,
        durationSec: v.duration,
      })
    }, 5000)

    return () => {
      stopUiTick()
      v.removeEventListener('loadedmetadata', onMeta)
      v.removeEventListener('durationchange', onDurationChange)
      v.removeEventListener('timeupdate', onTime)
      v.removeEventListener('playing', onPlaying)
      v.removeEventListener('ended', onEnded)
      v.removeEventListener('play', onPlay)
      v.removeEventListener('pause', onPause)
      v.removeEventListener('error', onError)
      window.clearInterval(persistTimer)
      if (id) {
        const t = currentTimeRef.current
        const d = durationRef.current || v.duration || 0
        if (t > 0.25) {
          if (d > 0) {
            saveEpisodePlaybackProgress({
              vodId: id,
              sourceIndex: currentSourceRef.current,
              episodeIndex: currentEpisodeRef.current,
              positionSec: Math.min(t, d - 0.25),
              durationSec: d,
            })
          } else {
            const rec = getEpisodePlaybackProgress(
              id,
              currentSourceRef.current,
              currentEpisodeRef.current
            )
            const dur = rec?.durationSec ?? 0
            if (dur > 0 || t > 1) {
              saveEpisodePlaybackProgress({
                vodId: id,
                sourceIndex: currentSourceRef.current,
                episodeIndex: currentEpisodeRef.current,
                positionSec: t,
                durationSec: Math.max(dur, t),
              })
            }
          }
        }
      }
    }
  }, [id, playUrl, currentEpisode, currentSource, applyLoadedPosition, totalEpisodes, goNextEpisode])

  movieRef.current = movie
  currentTimeRef.current = currentTime
  currentSourceRef.current = currentSource
  currentEpisodeRef.current = currentEpisode

  useEffect(() => {
    if (!movie) return
    const pct = duration > 0 ? (currentTime / duration) * 100 : 0
    upsertWatchHistory(movie, Math.min(100, Math.max(0, pct)), {
      sourceIndex: currentSource,
      episodeIndex: currentEpisode,
    })
  }, [movie, duration, currentTime, currentSource, currentEpisode])

  useEffect(() => {
    if (!movie) return
    const tid = window.setInterval(() => {
      const d = durationRef.current
      const t = currentTimeRef.current
      const p = d > 0 ? Math.min(100, (t / d) * 100) : 0
      upsertWatchHistory(movie, p, {
        sourceIndex: currentSourceRef.current,
        episodeIndex: currentEpisodeRef.current,
      })
    }, 15000)
    return () => clearInterval(tid)
  }, [movie?.id])

  useEffect(() => {
    return () => {
      const m = movieRef.current
      const d = durationRef.current
      const t = currentTimeRef.current
      if (m && d > 0)
        upsertWatchHistory(m, Math.min(100, (t / d) * 100), {
          sourceIndex: currentSourceRef.current,
          episodeIndex: currentEpisodeRef.current,
        })
    }
  }, [])

  const handleSeek = useCallback(
    (direction: 'forward' | 'backward') => {
      const v = videoRef.current
      const d = durationRef.current || duration
      const delta = SEEK_DELTA_SECONDS
      if (v) {
        if (direction === 'forward') v.currentTime = Math.min(v.currentTime + delta, d || v.duration)
        else v.currentTime = Math.max(v.currentTime - delta, 0)
      }
      setSeekOverlay({ direction, seconds: delta })
      if (seekTimerRef.current) clearTimeout(seekTimerRef.current)
      seekTimerRef.current = setTimeout(() => setSeekOverlay(null), 1200)
      resetHideTimer()
    },
    [duration, resetHideTimer]
  )

  const seekToSeconds = useCallback(
    (sec: number) => {
      const v = videoRef.current
      const d = durationRef.current || duration
      if (!v || !d) return
      v.currentTime = Math.min(Math.max(0, sec), d)
      resetHideTimer()
    },
    [duration, resetHideTimer]
  )

  const togglePanel = (panel: PanelType) => {
    setActivePanel((prev) => (prev === panel ? null : panel))
    resetHideTimer()
  }

  const switchSource = (idx: number) => {
    setCurrentSource(idx)
    syncUrl(currentEpisode, idx)
    setIsLoading(true)
    setActivePanel(null)
    setTimeout(() => setIsLoading(false), 400)
  }

  const selectEpisode = (ep: number) => {
    setCurrentEpisode(ep)
    syncUrl(ep, currentSource)
    setActivePanel(null)
    resetHideTimer()
  }

  const selectSpeed = (label: string) => {
    setCurrentSpeedLabel(label)
    const v = videoRef.current
    if (v) v.playbackRate = parseSpeedToNumber(label)
    setActivePanel(null)
    resetHideTimer()
  }

  useEffect(() => {
    const v = videoRef.current
    if (v) v.playbackRate = parseSpeedToNumber(currentSpeedLabel)
  }, [currentSpeedLabel, playUrl])

  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      if (activePanel) {
        if (e.key === 'Escape' || e.key === 'Backspace') {
          e.preventDefault()
          setActivePanel(null)
        }
        return
      }

      resetHideTimer()

      switch (e.key) {
        case ' ':
        case 'Enter': {
          if (!showControls) {
            e.preventDefault()
            setShowControls(true)
            setControlsMode('nav')
            setFocusedZone('bottom')
            const v = videoRef.current
            if (v) void (v.paused ? v.play() : v.pause())
          } else if (controlsMode === 'nav' && focusedZone === 'progress') {
            e.preventDefault()
            const v = videoRef.current
            if (v) void (v.paused ? v.play() : v.pause())
          } else if (controlsMode !== 'nav') {
            e.preventDefault()
            setControlsMode('nav')
            setFocusedZone('bottom')
            const v = videoRef.current
            if (v) void (v.paused ? v.play() : v.pause())
          }
          break
        }
        case 'ArrowLeft': {
          if (!showControls) {
            e.preventDefault()
            setShowControls(true)
            setControlsMode('seek')
            handleSeek('backward')
          } else if (focusedZone !== 'top' && focusedZone !== 'bottom') {
            e.preventDefault()
            setControlsMode('seek')
            handleSeek('backward')
          }
          break
        }
        case 'ArrowRight': {
          if (!showControls) {
            e.preventDefault()
            setShowControls(true)
            setControlsMode('seek')
            handleSeek('forward')
          } else if (focusedZone !== 'top' && focusedZone !== 'bottom') {
            e.preventDefault()
            setControlsMode('seek')
            handleSeek('forward')
          }
          break
        }
        case 'ArrowUp': {
          e.preventDefault()
          if (!showControls) {
            setShowControls(true)
            setControlsMode('nav')
            setFocusedZone('bottom')
          } else if (controlsMode === 'seek') {
            setControlsMode('nav')
            setFocusedZone('progress')
          } else {
            if (focusedZone === 'bottom') setFocusedZone('progress')
            else if (focusedZone === 'progress') setFocusedZone('top')
          }
          break
        }
        case 'ArrowDown': {
          e.preventDefault()
          if (!showControls) {
            setShowControls(true)
            setControlsMode('nav')
            setFocusedZone('bottom')
          } else if (controlsMode === 'seek') {
            setControlsMode('nav')
            setFocusedZone('bottom')
          } else {
            if (focusedZone === 'top') setFocusedZone('progress')
            else if (focusedZone === 'progress') setFocusedZone('bottom')
          }
          break
        }
        case 'Escape':
        case 'Backspace': {
          if (showControls) {
            e.preventDefault()
            setShowControls(false)
            setControlsMode(null)
            setFocusedZone('none')
          } else {
            e.preventDefault()
            triggerAppBackNavigation()
          }
          break
        }
      }
    }
    window.addEventListener('keydown', handleKey)
    return () => window.removeEventListener('keydown', handleKey)
  }, [
    activePanel,
    showControls,
    controlsMode,
    focusedZone,
    handleSeek,
    resetHideTimer,
    goNextEpisode,
  ])

  const handleRetry = () => {
    setErrorMessage(null)
    const v = videoRef.current
    if (v && playUrl) {
      v.load()
      void v.play().catch(() => {})
    }
  }

  const togglePlayPause = () => {
    const v = videoRef.current
    if (!v) return
    void (v.paused ? v.play() : v.pause())
    resetHideTimer()
  }

  const playable = isWebPlayableUrl(playUrl)

  return (
    <div
      className="relative h-screen w-screen cursor-none select-none overflow-hidden bg-background"
      onClick={() => {
        if (activePanel) return
        setShowControls((prev) => !prev)
        if (!showControls) {
          setControlsMode('nav')
          setFocusedZone('bottom')
        } else {
          setControlsMode(null)
          setFocusedZone('none')
        }
        resetHideTimer()
      }}
      onMouseMove={resetHideTimer}
    >
      <div className="absolute inset-0 bg-black">
        {playable ? (
          <video
            key={`${id}-${currentSource}-${currentEpisode}-${playUrl}`}
            ref={videoRef}
            className="h-full w-full object-contain"
            playsInline
            controls={false}
            poster={displayMovie.backdrop || displayMovie.poster}
          />
        ) : (
          <>
            <img
              src={displayMovie.backdrop || displayMovie.poster}
              alt={displayMovie.title}
              className="h-full w-full object-cover"
            />
            <div className="absolute inset-0 flex items-center justify-center bg-background/50 p-6 text-center text-sm text-muted-foreground">
              {!playUrl ? '暂无可播放地址' : '当前线路地址无法在应用内播放，请尝试换源'}
            </div>
          </>
        )}
      </div>

      {isLoading && (
        <div className="pointer-events-none absolute inset-0 z-30 flex items-center justify-center">
          <div className="flex flex-col items-center gap-3 rounded-2xl bg-background/80 px-10 py-6 backdrop-blur-md">
            <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
            <span className="text-lg font-medium text-foreground">加载中...</span>
          </div>
        </div>
      )}

      {errorMessage && (
        <div
          className="absolute inset-0 z-50 flex items-center justify-center bg-black/60"
          onClick={(e) => e.stopPropagation()}
        >
          <div className="flex w-[500px] flex-col items-center rounded-2xl border border-border bg-card/95 p-8 shadow-[var(--shadow-elevated)] backdrop-blur-xl">
            <AlertTriangle size={48} className="mb-4 text-destructive" />
            <h3 className="mb-2 text-xl font-bold text-foreground">播放错误</h3>
            <p className="mb-6 text-center text-sm text-muted-foreground">{errorMessage}</p>
            <div className="flex items-center gap-3">
              <button
                type="button"
                className="tv-focusable pill-focus rounded-xl bg-primary px-6 py-2.5 text-sm font-medium text-primary-foreground"
                onClick={handleRetry}
              >
                <RotateCcw size={16} className="mr-1 inline" />
                重试
              </button>
              <button
                type="button"
                className="tv-focusable pill-focus rounded-xl bg-secondary px-6 py-2.5 text-sm font-medium text-secondary-foreground"
                onClick={() => setErrorMessage(null)}
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}

      {seekOverlay && (
        <div className="pointer-events-none absolute inset-0 z-30 flex animate-fade-in items-center justify-center">
          <div className="flex items-center gap-3 rounded-2xl bg-background/70 px-8 py-4 backdrop-blur-md">
            {seekOverlay.direction === 'forward' ? (
              <SkipForward size={28} className="text-primary" />
            ) : (
              <SkipBack size={28} className="text-primary" />
            )}
            <span className="text-2xl font-bold text-foreground">
              {seekOverlay.direction === 'forward' ? '+' : '-'}
              {seekOverlay.seconds}s
            </span>
          </div>
        </div>
      )}

      {!isPlaying && !activePanel && !isLoading && !errorMessage && playable && (
        <div className="pointer-events-none absolute inset-0 z-20 flex items-center justify-center">
          <button
            type="button"
            className="tv-focusable pointer-events-auto flex h-20 w-20 animate-scale-in items-center justify-center rounded-full bg-primary/80 backdrop-blur-sm"
            tabIndex={0}
            onClick={(e) => {
              e.stopPropagation()
              void videoRef.current?.play()
              resetHideTimer()
            }}
          >
            <Play size={40} className="ml-1 text-primary-foreground" fill="currentColor" />
          </button>
        </div>
      )}

      <div
        className={cn(
          'gradient-top absolute inset-x-0 top-0 z-40 transition-all duration-500',
          showControls ? 'translate-y-0 opacity-100' : 'pointer-events-none -translate-y-full opacity-0'
        )}
        onClick={(e) => e.stopPropagation()}
        {...(!showControls || activePanel ? ({ inert: true } as object) : {})}
      >
        <div className="flex items-center justify-between px-10 py-6">
          <div className="flex items-center gap-4">
            <button
              type="button"
              {...spatialBack}
              className="tv-focusable pill-focus flex h-10 w-10 items-center justify-center rounded-full bg-foreground/10 backdrop-blur-sm"
              onFocus={() => setFocusedZone('top')}
              onBlur={() => setFocusedZone((prev) => (prev === 'top' ? 'none' : prev))}
              onClick={() => navigate(-1)}
            >
              <ArrowLeft size={20} className="text-foreground" />
            </button>
            <div>
              <h2 className="text-lg font-bold text-foreground">
                {displayMovie.title}
                {totalEpisodes > 1 && (
                  <span className="ml-2 inline-flex align-middle rounded-md bg-white/50 px-2 py-0.5 text-sm font-medium text-yellow-400 shadow-sm backdrop-blur-sm">
                    第{currentEpisode}集
                  </span>
                )}
              </h2>
            </div>
          </div>
          <span className="text-sm font-medium text-foreground/80">{systemTime}</span>
        </div>
      </div>

      <div
        className={cn(
          'gradient-bottom absolute inset-x-0 bottom-0 z-40 transition-all duration-500',
          showControls ? 'translate-y-0 opacity-100' : 'pointer-events-none translate-y-full opacity-0'
        )}
        onClick={(e) => e.stopPropagation()}
        {...(!showControls || activePanel ? ({ inert: true } as object) : {})}
      >
        <div className="px-10 pb-8 pt-16">
          <PlayerProgressBar
            className="mb-5"
            currentTime={currentTime}
            duration={duration}
            progressFocused={progressFocused}
            seekModeHighlight={controlsMode === 'seek'}
            barRef={progressBarRef}
            trackProps={spatialProgress}
            onSeekToSeconds={seekToSeconds}
            onProgressFocusChange={(f) => {
              setProgressFocused(f)
              if (f) setFocusedZone('progress')
              else setFocusedZone((prev) => (prev === 'progress' ? 'none' : prev))
            }}
          />

          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <button
                type="button"
                {...spatialPrev}
                className="tv-focusable pill-focus flex h-9 w-9 items-center justify-center rounded-full text-foreground/80 hover:text-foreground"
                onFocus={() => setFocusedZone('bottom')}
                onBlur={() => setFocusedZone((prev) => (prev === 'bottom' ? 'none' : prev))}
                onClick={() => {
                  goPrevEpisode()
                  resetHideTimer()
                }}
              >
                <SkipBack size={20} />
              </button>
              <button
                type="button"
                {...spatialPlay}
                className="tv-focusable flex h-12 w-12 items-center justify-center rounded-full bg-foreground/10 text-foreground backdrop-blur-sm transition-all hover:bg-primary hover:text-primary-foreground"
                onFocus={() => setFocusedZone('bottom')}
                onBlur={() => setFocusedZone((prev) => (prev === 'bottom' ? 'none' : prev))}
                onClick={() => {
                  togglePlayPause()
                }}
              >
                {isPlaying ? (
                  <Pause size={24} />
                ) : (
                  <Play size={24} className="ml-0.5" fill="currentColor" />
                )}
              </button>
              <button
                type="button"
                {...spatialNext}
                className="tv-focusable pill-focus flex h-9 w-9 items-center justify-center rounded-full text-foreground/80 hover:text-foreground"
                onFocus={() => setFocusedZone('bottom')}
                onBlur={() => setFocusedZone((prev) => (prev === 'bottom' ? 'none' : prev))}
                onClick={() => {
                  goNextEpisode()
                  resetHideTimer()
                }}
              >
                <SkipForward size={20} />
              </button>

              <span className="ml-2 flex items-baseline gap-0 text-sm font-medium">
                <span className="text-white">{formatDuration(currentTime)}</span>
                <span className="text-white/30"> / </span>
                <span className="text-white/60">{formatDuration(duration)}</span>
              </span>
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                {...spatialSpeed}
                className={cn(
                  'tv-focusable pill-focus flex items-center gap-1.5 rounded-full px-4 py-2 text-sm font-medium transition-all',
                  activePanel === 'speed'
                    ? 'tv-tab-selected'
                    : 'bg-foreground/10 text-foreground/80 hover:bg-foreground/20'
                )}
                onFocus={() => setFocusedZone('bottom')}
                onBlur={() => setFocusedZone((prev) => (prev === 'bottom' ? 'none' : prev))}
                onClick={() => togglePanel('speed')}
              >
                <Gauge size={16} />
                倍速 {currentSpeedLabel !== '1.0x' ? currentSpeedLabel : ''}
              </button>
              {totalEpisodes > 1 && (
                <button
                  type="button"
                  {...spatialEpisodes}
                  className={cn(
                    'tv-focusable pill-focus flex items-center gap-1.5 rounded-full px-4 py-2 text-sm font-medium transition-all',
                    activePanel === 'episodes'
                      ? 'tv-tab-selected'
                      : 'bg-foreground/10 text-foreground/80 hover:bg-foreground/20'
                  )}
                  onFocus={() => setFocusedZone('bottom')}
                  onBlur={() => setFocusedZone((prev) => (prev === 'bottom' ? 'none' : prev))}
                  onClick={() => togglePanel('episodes')}
                >
                  <List size={16} />
                  选集
                </button>
              )}
              {playBundles.length > 0 && (
                <button
                  type="button"
                  {...spatialSource}
                  className={cn(
                    'tv-focusable pill-focus flex items-center gap-1.5 rounded-full px-4 py-2 text-sm font-medium transition-all',
                    activePanel === 'source'
                      ? 'tv-tab-selected'
                      : 'bg-foreground/10 text-foreground/80 hover:bg-foreground/20'
                  )}
                  onFocus={() => setFocusedZone('bottom')}
                  onBlur={() => setFocusedZone((prev) => (prev === 'bottom' ? 'none' : prev))}
                  onClick={() => togglePanel('source')}
                >
                  <RefreshCw size={16} />
                  换源
                </button>
              )}
              <button
                type="button"
                {...spatialSkip}
                className={cn(
                  'tv-focusable pill-focus flex items-center gap-1.5 rounded-full px-4 py-2 text-sm font-medium transition-all',
                  activePanel === 'skip'
                    ? 'tv-tab-selected'
                    : 'bg-foreground/10 text-foreground/80 hover:bg-foreground/20'
                )}
                onFocus={() => setFocusedZone('bottom')}
                onBlur={() => setFocusedZone((prev) => (prev === 'bottom' ? 'none' : prev))}
                onClick={() => togglePanel('skip')}
              >
                <FastForward size={16} />
                跳过
              </button>
            </div>
          </div>
        </div>
      </div>

      {activePanel === 'speed' && (
        <SpeedPanel
          speeds={[...SPEED_OPTIONS]}
          currentSpeed={currentSpeedLabel}
          onSelect={selectSpeed}
          onClose={() => setActivePanel(null)}
        />
      )}
      {activePanel === 'episodes' && (
        <EpisodesPanel
          totalEpisodes={totalEpisodes}
          currentEpisode={currentEpisode}
          onSelect={selectEpisode}
          onClose={() => setActivePanel(null)}
        />
      )}
      {activePanel === 'source' && (
        <SourcePanel
          sources={sources}
          currentSource={currentSource}
          onSelect={(idx) => switchSource(idx)}
          onClose={() => setActivePanel(null)}
        />
      )}
      {activePanel === 'skip' && <SkipPanel onClose={() => setActivePanel(null)} />}
    </div>
  )
}

function PanelWrapper({
  title,
  onClose,
  closeSpatial,
  children,
}: {
  title: string
  onClose: () => void
  /** TV 空间导航：关闭按钮 */
  closeSpatial: { 'data-spatial-id': string; tabIndex: 0 }
  children: React.ReactNode
}) {
  return (
    <div
      role="dialog"
      aria-modal="true"
      className="animate-scale-in absolute right-6 bottom-28 z-[60] w-[300px] overflow-hidden rounded-2xl border border-border bg-card/95 shadow-[var(--shadow-elevated)] backdrop-blur-xl"
      onClick={(e) => e.stopPropagation()}
    >
      <div className="flex items-center justify-between border-b border-border px-5 py-4">
        <h3 className="text-base font-bold text-foreground">{title}</h3>
        <button
          type="button"
          {...closeSpatial}
          className="tv-focusable flex h-8 w-8 items-center justify-center rounded-full bg-secondary"
          onClick={onClose}
        >
          <X size={14} className="text-muted-foreground" />
        </button>
      </div>
      <div className="p-4">{children}</div>
    </div>
  )
}

function SpeedPanel({
  speeds,
  currentSpeed,
  onSelect,
  onClose,
}: {
  speeds: string[]
  currentSpeed: string
  onSelect: (s: string) => void
  onClose: () => void
}) {
  const spatialClose = useTvSpatialNode(
    'player-panel-speed-close',
    () => ({ down: 'player-panel-speed-0' }),
    []
  )
  return (
    <PanelWrapper title="播放倍速" onClose={onClose} closeSpatial={spatialClose}>
      <div className="grid grid-cols-3 gap-2">
        {speeds.map((speed, idx) => (
          <PlayerSpeedCell
            key={speed}
            speed={speed}
            idx={idx}
            currentSpeed={currentSpeed}
            onSelect={onSelect}
          />
        ))}
      </div>
    </PanelWrapper>
  )
}

function EpisodesPanel({
  totalEpisodes,
  currentEpisode,
  onSelect,
  onClose,
}: {
  totalEpisodes: number
  currentEpisode: number
  onSelect: (ep: number) => void
  onClose: () => void
}) {
  const spatialClose = useTvSpatialNode(
    'player-panel-episodes-close',
    () => ({ down: 'player-panel-episodes-0' }),
    []
  )
  return (
    <PanelWrapper title="选集" onClose={onClose} closeSpatial={spatialClose}>
      <div className="thin-scrollbar grid max-h-[240px] grid-cols-4 gap-2 overflow-y-auto pr-1">
        {Array.from({ length: totalEpisodes }, (_, i) => i + 1).map((ep) => (
          <EpisodeCell
            key={ep}
            ep={ep}
            idx={ep - 1}
            total={totalEpisodes}
            isCurrent={ep === currentEpisode}
            onSelect={onSelect}
          />
        ))}
      </div>
    </PanelWrapper>
  )
}

function SourcePanel({
  sources,
  currentSource,
  onSelect,
  onClose,
}: {
  sources: string[]
  currentSource: number
  onSelect: (idx: number) => void
  onClose: () => void
}) {
  const n = sources.length
  const spatialClose = useTvSpatialNode(
    'player-panel-source-close',
    () => ({ down: 'player-panel-source-0' }),
    []
  )
  return (
    <PanelWrapper title="切换播放源" onClose={onClose} closeSpatial={spatialClose}>
      <div className="flex flex-col gap-2">
        {sources.map((source, idx) => (
          <SourceRow
            key={idx}
            idx={idx}
            n={n}
            label={source}
            isCurrent={idx === currentSource}
            onSelect={onSelect}
          />
        ))}
      </div>
    </PanelWrapper>
  )
}

function SkipPanel({ onClose }: { onClose: () => void }) {
  const s0 = getPlaybackSettings()
  const masterOn = s0.autoSkipIntroOutro
  const [introSeconds, setIntroSeconds] = useState(s0.introSkipSec)
  const [outroSeconds, setOutroSeconds] = useState(s0.outroSkipSec)
  const [introEnabled, setIntroEnabled] = useState(s0.skipIntroEnabled)
  const [outroEnabled, setOutroEnabled] = useState(s0.skipOutroEnabled)

  const spClose = useTvSpatialNode(
    'player-panel-skip-close',
    () => ({ down: 'player-panel-skip-intro-en' }),
    []
  )
  const spIntroEn = useTvSpatialNode(
    'player-panel-skip-intro-en',
    () => ({
      up: 'player-panel-skip-close',
      down: 'player-panel-skip-intro-m',
    }),
    []
  )
  const spIntroM = useTvSpatialNode(
    'player-panel-skip-intro-m',
    () => ({
      up: 'player-panel-skip-intro-en',
      right: 'player-panel-skip-intro-p',
      down: 'player-panel-skip-outro-en',
    }),
    []
  )
  const spIntroP = useTvSpatialNode(
    'player-panel-skip-intro-p',
    () => ({
      up: 'player-panel-skip-intro-en',
      left: 'player-panel-skip-intro-m',
      down: 'player-panel-skip-outro-m',
    }),
    []
  )
  const spOutroEn = useTvSpatialNode(
    'player-panel-skip-outro-en',
    () => ({
      up: 'player-panel-skip-intro-m',
      down: 'player-panel-skip-outro-m',
    }),
    []
  )
  const spOutroM = useTvSpatialNode(
    'player-panel-skip-outro-m',
    () => ({
      up: 'player-panel-skip-intro-p',
      right: 'player-panel-skip-outro-p',
      down: 'player-panel-skip-save',
    }),
    []
  )
  const spOutroP = useTvSpatialNode(
    'player-panel-skip-outro-p',
    () => ({
      up: 'player-panel-skip-intro-p',
      left: 'player-panel-skip-outro-m',
      down: 'player-panel-skip-save',
    }),
    []
  )
  const spSave = useTvSpatialNode(
    'player-panel-skip-save',
    () => ({
      up: 'player-panel-skip-outro-m',
    }),
    []
  )

  const persist = () => {
    savePlaybackSettings({
      introSkipSec: introSeconds,
      outroSkipSec: outroSeconds,
      skipIntroEnabled: introEnabled,
      skipOutroEnabled: outroEnabled,
    })
    onClose()
  }

  return (
    <PanelWrapper title="跳过片头/片尾" onClose={onClose} closeSpatial={spClose}>
      <div className="space-y-4">
        {!masterOn && (
          <p className="rounded-lg border border-border bg-muted/40 px-3 py-2 text-xs leading-relaxed text-muted-foreground">
            设置中「自动跳过片头片尾」总开关为关闭，当前不会自动跳过。可在「设置 →
            播放设置」中开启；开启后以下时长与片头/片尾分项开关会立即生效。
          </p>
        )}
        <div>
          <div className="mb-2 flex items-center justify-between">
            <span className="text-sm text-foreground">片头时长</span>
            <button
              type="button"
              {...spIntroEn}
              className={cn(
                'tv-focusable rounded-full px-3 py-1 text-xs font-medium transition-all',
                introEnabled ? 'tv-tab-selected' : 'bg-secondary text-muted-foreground'
              )}
              onClick={() => setIntroEnabled(!introEnabled)}
            >
              {introEnabled ? '已开启' : '已关闭'}
            </button>
          </div>
          <div className="flex items-center gap-3">
            <button
              type="button"
              {...spIntroM}
              className="tv-focusable flex h-8 w-8 items-center justify-center rounded-lg bg-secondary text-sm font-bold text-foreground"
              onClick={() => setIntroSeconds(Math.max(0, introSeconds - 5))}
            >
              -
            </button>
            <div className="relative h-2 flex-1 rounded-full bg-secondary">
              <div
                className="pointer-events-none absolute inset-y-0 left-0 rounded-full bg-primary transition-all"
                style={{ width: `${(introSeconds / 300) * 100}%` }}
              />
            </div>
            <button
              type="button"
              {...spIntroP}
              className="tv-focusable flex h-8 w-8 items-center justify-center rounded-lg bg-secondary text-sm font-bold text-foreground"
              onClick={() => setIntroSeconds(Math.min(300, introSeconds + 5))}
            >
              +
            </button>
            <span className="min-w-[45px] text-right font-mono text-sm text-foreground">
              {Math.floor(introSeconds / 60)}:{String(introSeconds % 60).padStart(2, '0')}
            </span>
          </div>
        </div>
        <div>
          <div className="mb-2 flex items-center justify-between">
            <span className="text-sm text-foreground">片尾时长</span>
            <button
              type="button"
              {...spOutroEn}
              className={cn(
                'tv-focusable rounded-full px-3 py-1 text-xs font-medium transition-all',
                outroEnabled ? 'tv-tab-selected' : 'bg-secondary text-muted-foreground'
              )}
              onClick={() => setOutroEnabled(!outroEnabled)}
            >
              {outroEnabled ? '已开启' : '已关闭'}
            </button>
          </div>
          <div className="flex items-center gap-3">
            <button
              type="button"
              {...spOutroM}
              className="tv-focusable flex h-8 w-8 items-center justify-center rounded-lg bg-secondary text-sm font-bold text-foreground"
              onClick={() => setOutroSeconds(Math.max(0, outroSeconds - 5))}
            >
              -
            </button>
            <div className="relative h-2 flex-1 rounded-full bg-secondary">
              <div
                className="pointer-events-none absolute inset-y-0 left-0 rounded-full bg-primary transition-all"
                style={{ width: `${(outroSeconds / 300) * 100}%` }}
              />
            </div>
            <button
              type="button"
              {...spOutroP}
              className="tv-focusable flex h-8 w-8 items-center justify-center rounded-lg bg-secondary text-sm font-bold text-foreground"
              onClick={() => setOutroSeconds(Math.min(300, outroSeconds + 5))}
            >
              +
            </button>
            <span className="min-w-[45px] text-right font-mono text-sm text-foreground">
              {Math.floor(outroSeconds / 60)}:{String(outroSeconds % 60).padStart(2, '0')}
            </span>
          </div>
        </div>
        <button
          type="button"
          {...spSave}
          className="tv-focusable pill-focus mt-2 w-full rounded-xl bg-primary py-2.5 text-sm font-medium text-primary-foreground"
          onClick={persist}
        >
          保存设置
        </button>
      </div>
    </PanelWrapper>
  )
}
