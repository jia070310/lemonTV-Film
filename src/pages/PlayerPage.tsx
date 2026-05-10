import { useState, useEffect, useRef, useCallback } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { triggerAppBackNavigation } from '@/components/AppBackHandler'
import { cn } from '@/lib/utils'
import { heroMovies } from '@/data/mockData'
import {
  ArrowLeft, Play, Pause, SkipBack, SkipForward,
  Gauge, List, RefreshCw, FastForward, X, AlertTriangle,
  RotateCcw
} from 'lucide-react'

type PanelType = 'speed' | 'episodes' | 'source' | 'skip' | null
type ControlsMode = 'nav' | 'seek' | null
type FocusZone = 'top' | 'progress' | 'bottom' | 'none'

const CONTROLS_AUTO_HIDE_MS = 6000
const SEEK_DELTA_SECONDS = 15

function formatDuration(seconds: number): string {
  if (seconds <= 0) return '00:00'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  if (h > 0) return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

export function PlayerPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const movie = heroMovies.find(m => m.id === id) || heroMovies[0]
  const totalEpisodes = movie.episodes || 1

  // Core playback states
  const [isPlaying, setIsPlaying] = useState(true)
  const [showControls, setShowControls] = useState(true)
  const [currentTime, setCurrentTime] = useState(723)
  const duration = 2856
  const [seekOverlay, setSeekOverlay] = useState<{ direction: 'forward' | 'backward', seconds: number } | null>(null)
  const [activePanel, setActivePanel] = useState<PanelType>(null)
  const [currentEpisode, setCurrentEpisode] = useState(1)
  const [currentSpeed, setCurrentSpeed] = useState(1)
  const [currentSource, setCurrentSource] = useState(0)
  const [systemTime, setSystemTime] = useState('')
  const [progressFocused, setProgressFocused] = useState(false)

  // Controls mode & focus zone (from LomenTV0)
  const [controlsMode, setControlsMode] = useState<ControlsMode>('nav')
  const [focusedZone, setFocusedZone] = useState<FocusZone>('none')

  // Loading & error states
  const [isLoading, setIsLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  // Resume prompt
  const [showResumePrompt, setShowResumePrompt] = useState(false)
  const [startPosition] = useState(() => {
    // Simulate resume position from localStorage or query params
    return 0 // Set to 0 for demo; change to test resume prompt
  })

  // Auto-play next episode
  const [showNextEpisodePrompt, setShowNextEpisodePrompt] = useState(false)

  const hideTimerRef = useRef<ReturnType<typeof setTimeout>>()
  const seekTimerRef = useRef<ReturnType<typeof setTimeout>>()
  const resumePromptTimerRef = useRef<ReturnType<typeof setTimeout>>()
  const nextEpisodeTimerRef = useRef<ReturnType<typeof setTimeout>>()
  const progressBarRef = useRef<HTMLDivElement>(null)

  const speeds = [0.5, 0.75, 1, 1.25, 1.5, 2]
  const sources = ['源1 - 蓝光', '源2 - 超清', '源3 - 高清', '源4 - 备用']

  // Update system time
  useEffect(() => {
    const updateTime = () => {
      const now = new Date()
      setSystemTime(`${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`)
    }
    updateTime()
    const interval = setInterval(updateTime, 30000)
    return () => clearInterval(interval)
  }, [])

  // Resume prompt: show if startPosition > 0
  useEffect(() => {
    if (startPosition > 0) {
      setShowResumePrompt(true)
      resumePromptTimerRef.current = setTimeout(() => {
        setShowResumePrompt(false)
      }, 8000)
    }
    return () => {
      if (resumePromptTimerRef.current) clearTimeout(resumePromptTimerRef.current)
    }
  }, [startPosition])

  // Auto-hide controls
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

  // Simulate playback progress
  useEffect(() => {
    if (!isPlaying) return
    const interval = setInterval(() => {
      setCurrentTime(prev => {
        const next = Math.min(prev + 1, duration)
        return next
      })
    }, 1000)
    return () => clearInterval(interval)
  }, [isPlaying, duration])

  // Auto-play next episode when current ends
  useEffect(() => {
    if (currentTime >= duration && duration > 0 && isPlaying) {
      setIsPlaying(false)
      if (totalEpisodes > 1 && currentEpisode < totalEpisodes) {
        setShowNextEpisodePrompt(true)
        nextEpisodeTimerRef.current = setTimeout(() => {
          handleNextEpisode()
        }, 3000)
      }
    }
    return () => {
      if (nextEpisodeTimerRef.current) clearTimeout(nextEpisodeTimerRef.current)
    }
  }, [currentTime, duration, isPlaying, currentEpisode, totalEpisodes])

  const handleNextEpisode = useCallback(() => {
    setShowNextEpisodePrompt(false)
    if (currentEpisode < totalEpisodes) {
      setIsLoading(true)
      setCurrentEpisode(prev => prev + 1)
      setCurrentTime(0)
      setIsPlaying(true)
      setTimeout(() => setIsLoading(false), 800)
    }
  }, [currentEpisode, totalEpisodes])

  const progress = duration > 0 ? (currentTime / duration) * 100 : 0

  const handleSeek = useCallback((direction: 'forward' | 'backward') => {
    const delta = SEEK_DELTA_SECONDS
    if (direction === 'forward') {
      setCurrentTime(prev => Math.min(prev + delta, duration))
    } else {
      setCurrentTime(prev => Math.max(prev - delta, 0))
    }
    setSeekOverlay({ direction, seconds: delta })
    if (seekTimerRef.current) clearTimeout(seekTimerRef.current)
    seekTimerRef.current = setTimeout(() => setSeekOverlay(null), 1200)
    resetHideTimer()
  }, [duration, resetHideTimer])

  const handleProgressClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect()
    const x = e.clientX - rect.left
    const percent = x / rect.width
    setCurrentTime(Math.round(percent * duration))
    resetHideTimer()
  }

  const togglePanel = (panel: PanelType) => {
    setActivePanel(prev => prev === panel ? null : panel)
    resetHideTimer()
  }

  const switchSource = (idx: number) => {
    setCurrentSource(idx)
    setIsLoading(true)
    setActivePanel(null)
    setTimeout(() => setIsLoading(false), 1000)
  }

  // Keyboard / remote control handling (enhanced from LomenTV0)
  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      // When any panel is open, only ESC closes it
      if (activePanel) {
        if (e.key === 'Escape' || e.key === 'Backspace') {
          e.preventDefault()
          setActivePanel(null)
        }
        return
      }

      // Resume prompt: any key dismisses it
      if (showResumePrompt) {
        if (e.key === 'Escape' || e.key === 'Backspace') {
          e.preventDefault()
          setShowResumePrompt(false)
        }
        return
      }

      // Next episode prompt
      if (showNextEpisodePrompt) {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          handleNextEpisode()
        } else if (e.key === 'Escape' || e.key === 'Backspace') {
          e.preventDefault()
          setShowNextEpisodePrompt(false)
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
            setIsPlaying(prev => !prev)
          } else if (controlsMode === 'nav') {
            // In nav mode, let focused control handle it
            // But if progress bar is focused, toggle play/pause
            if (focusedZone === 'progress') {
              e.preventDefault()
              setIsPlaying(prev => !prev)
            }
            // Otherwise pass through for button clicks
          } else {
            e.preventDefault()
            setControlsMode('nav')
            setFocusedZone('bottom')
            setIsPlaying(prev => !prev)
          }
          break
        }
        case 'ArrowLeft': {
          if (!showControls) {
            e.preventDefault()
            setShowControls(true)
            setControlsMode('seek')
            handleSeek('backward')
          } else if (focusedZone === 'top' || focusedZone === 'bottom') {
            // In top/bottom button zones, let system handle focus navigation
          } else {
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
          } else if (focusedZone === 'top' || focusedZone === 'bottom') {
            // In top/bottom button zones, let system handle focus navigation
          } else {
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
            // Move focus up
            if (focusedZone === 'bottom') {
              setFocusedZone('progress')
            } else if (focusedZone === 'progress') {
              setFocusedZone('top')
            }
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
            // Move focus down
            if (focusedZone === 'top') {
              setFocusedZone('progress')
            } else if (focusedZone === 'progress') {
              setFocusedZone('bottom')
            }
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
  }, [activePanel, showControls, controlsMode, focusedZone, showResumePrompt, showNextEpisodePrompt, handleSeek, resetHideTimer, handleNextEpisode])

  const handleResumeContinue = () => {
    setShowResumePrompt(false)
    setCurrentTime(startPosition)
  }

  const handleResumeRestart = () => {
    setShowResumePrompt(false)
    setCurrentTime(0)
  }

  const handleRetry = () => {
    setErrorMessage(null)
    setIsLoading(true)
    setTimeout(() => setIsLoading(false), 1000)
  }

  return (
    <div
      className="h-screen w-screen bg-background relative overflow-hidden cursor-none select-none"
      onClick={() => {
        if (activePanel) {
          setActivePanel(null)
        } else if (showResumePrompt) {
          setShowResumePrompt(false)
        } else if (showNextEpisodePrompt) {
          setShowNextEpisodePrompt(false)
        } else {
          setShowControls(prev => !prev)
          if (!showControls) {
            setControlsMode('nav')
            setFocusedZone('bottom')
          } else {
            setControlsMode(null)
            setFocusedZone('none')
          }
          resetHideTimer()
        }
      }}
      onMouseMove={resetHideTimer}
    >
      {/* Video placeholder */}
      <div className="absolute inset-0">
        <img
          src={movie.backdrop || movie.poster}
          alt={movie.title}
          className="w-full h-full object-cover"
        />
        <div className="absolute inset-0 bg-background/30" />
      </div>

      {/* Loading overlay */}
      {isLoading && (
        <div className="absolute inset-0 flex items-center justify-center z-30 pointer-events-none">
          <div className="bg-background/80 backdrop-blur-md rounded-2xl px-10 py-6 flex flex-col items-center gap-3">
            <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
            <span className="text-lg font-medium text-foreground">加载中...</span>
          </div>
        </div>
      )}

      {/* Error dialog */}
      {errorMessage && (
        <div className="absolute inset-0 flex items-center justify-center z-50 bg-black/60" onClick={(e) => e.stopPropagation()}>
          <div className="bg-card/95 backdrop-blur-xl rounded-2xl border border-border shadow-[var(--shadow-elevated)] w-[500px] p-8 flex flex-col items-center">
            <AlertTriangle size={48} className="text-destructive mb-4" />
            <h3 className="text-xl font-bold text-foreground mb-2">播放错误</h3>
            <p className="text-sm text-muted-foreground text-center mb-6">{errorMessage}</p>
            <div className="flex items-center gap-3">
              <button
                className="tv-focusable pill-focus px-6 py-2.5 rounded-xl bg-primary text-primary-foreground font-medium text-sm"
                onClick={handleRetry}
              >
                <RotateCcw size={16} className="inline mr-1" />
                重试
              </button>
              <button
                className="tv-focusable pill-focus px-6 py-2.5 rounded-xl bg-secondary text-secondary-foreground font-medium text-sm"
                onClick={() => setErrorMessage(null)}
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Seek overlay */}
      {seekOverlay && (
        <div className="absolute inset-0 flex items-center justify-center z-30 pointer-events-none animate-fade-in">
          <div className="bg-background/70 backdrop-blur-md rounded-2xl px-8 py-4 flex items-center gap-3">
            {seekOverlay.direction === 'forward' ? (
              <SkipForward size={28} className="text-primary" />
            ) : (
              <SkipBack size={28} className="text-primary" />
            )}
            <span className="text-2xl font-bold text-foreground">
              {seekOverlay.direction === 'forward' ? '+' : '-'}{seekOverlay.seconds}s
            </span>
          </div>
        </div>
      )}

      {/* Next episode prompt */}
      {showNextEpisodePrompt && (
        <div className="absolute inset-0 flex items-center justify-center z-30 pointer-events-none">
          <div className="bg-background/80 backdrop-blur-md rounded-2xl px-10 py-6 flex flex-col items-center gap-3 pointer-events-auto">
            <p className="text-lg font-medium text-foreground">
              第 {currentEpisode} 集播放完毕
            </p>
            <p className="text-sm text-muted-foreground">
              即将自动播放第 {currentEpisode + 1} 集
            </p>
            <div className="flex items-center gap-3 mt-2">
              <button
                className="tv-focusable pill-focus px-5 py-2 rounded-xl bg-primary text-primary-foreground font-medium text-sm"
                onClick={(e) => { e.stopPropagation(); handleNextEpisode() }}
              >
                立即播放
              </button>
              <button
                className="tv-focusable pill-focus px-5 py-2 rounded-xl bg-secondary text-secondary-foreground font-medium text-sm"
                onClick={(e) => { e.stopPropagation(); setShowNextEpisodePrompt(false) }}
              >
                取消
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Center play button (when paused) */}
      {!isPlaying && !activePanel && !isLoading && !errorMessage && !showNextEpisodePrompt && (
        <div className="absolute inset-0 flex items-center justify-center z-20 pointer-events-none">
          <button
            className="tv-focusable w-20 h-20 rounded-full bg-primary/80 backdrop-blur-sm flex items-center justify-center animate-scale-in pointer-events-auto"
            tabIndex={0}
            onClick={(e) => {
              e.stopPropagation()
              setIsPlaying(true)
              resetHideTimer()
            }}
          >
            <Play size={40} className="text-primary-foreground ml-1" fill="currentColor" />
          </button>
        </div>
      )}

      {/* Resume prompt dialog */}
      {showResumePrompt && (
        <div className="absolute inset-0 flex items-center justify-center z-40 bg-black/40" onClick={(e) => e.stopPropagation()}>
          <div className="bg-card/95 backdrop-blur-xl rounded-2xl border border-border shadow-[var(--shadow-elevated)] w-[520px] p-6">
            <p className="text-base text-foreground mb-5">
              检测到播放记录：{formatDuration(startPosition)}
            </p>
            <div className="flex items-center gap-3">
              <button
                className="tv-focusable pill-focus px-5 py-2.5 rounded-xl bg-primary text-primary-foreground font-medium text-sm"
                onClick={(e) => { e.stopPropagation(); handleResumeContinue() }}
              >
                继续播放
              </button>
              <button
                className="tv-focusable pill-focus px-5 py-2.5 rounded-xl bg-secondary text-secondary-foreground font-medium text-sm"
                onClick={(e) => { e.stopPropagation(); handleResumeRestart() }}
              >
                从头开始
              </button>
              <button
                className="tv-focusable pill-focus px-5 py-2.5 rounded-xl bg-secondary text-secondary-foreground font-medium text-sm"
                onClick={(e) => { e.stopPropagation(); setShowResumePrompt(false) }}
              >
                关闭
              </button>
              <span className="text-xs text-muted-foreground ml-auto">8秒后自动关闭</span>
            </div>
          </div>
        </div>
      )}

      {/* Top bar */}
      <div
        className={cn(
          'absolute top-0 inset-x-0 z-40 gradient-top transition-all duration-500',
          showControls ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-full pointer-events-none'
        )}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-10 py-6">
          <div className="flex items-center gap-4">
            <button
              className={cn(
                'tv-focusable pill-focus w-10 h-10 rounded-full bg-foreground/10 backdrop-blur-sm flex items-center justify-center',
                focusedZone === 'top' && 'ring-2 ring-primary'
              )}
              tabIndex={0}
              onFocus={() => setFocusedZone('top')}
              onBlur={() => setFocusedZone(prev => prev === 'top' ? 'none' : prev)}
              onClick={() => navigate(-1)}
            >
              <ArrowLeft size={20} className="text-foreground" />
            </button>
            <div>
              <h2 className="text-lg font-bold text-foreground">
                {movie.title}
                {totalEpisodes > 1 && (
                  <span className="text-muted-foreground font-normal text-base ml-2">
                    第{currentEpisode}集
                  </span>
                )}
              </h2>
            </div>
          </div>
          <span className="text-foreground/80 text-sm font-medium">{systemTime}</span>
        </div>
      </div>

      {/* Bottom controls */}
      <div
        className={cn(
          'absolute bottom-0 inset-x-0 z-40 gradient-bottom transition-all duration-500',
          showControls ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-full pointer-events-none'
        )}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-10 pb-8 pt-16">
          {/* Progress bar with time bubble */}
          <div className="relative w-full mb-5">
            {/* Time bubble */}
            <div className="relative w-full h-6 mb-1">
              <div
                className={cn(
                  'absolute top-0 -translate-x-1/2 transition-all duration-200',
                  (progressFocused || controlsMode === 'seek') && 'scale-110'
                )}
                style={{ left: `${progress}%` }}
              >
                <div
                  className={cn(
                    'px-2.5 py-1 rounded text-xs font-mono font-medium whitespace-nowrap shadow-lg transition-colors duration-200',
                    (progressFocused || controlsMode === 'seek')
                      ? 'bg-primary text-primary-foreground'
                      : 'bg-primary/90 text-primary-foreground'
                  )}
                >
                  {formatDuration(currentTime)} / {formatDuration(duration)}
                </div>
              </div>
            </div>

            {/* Progress track */}
            <div
              ref={progressBarRef}
              className={cn(
                'relative w-full h-1.5 bg-foreground/20 rounded-full cursor-pointer group transition-all',
                progressFocused && 'h-2.5'
              )}
              onClick={handleProgressClick}
              onMouseEnter={() => { setProgressFocused(true); setFocusedZone('progress') }}
              onMouseLeave={() => { setProgressFocused(false); setFocusedZone(prev => prev === 'progress' ? 'none' : prev) }}
              onFocus={() => setFocusedZone('progress')}
              onBlur={() => setFocusedZone(prev => prev === 'progress' ? 'none' : prev)}
              tabIndex={0}
            >
              {/* Buffered */}
              <div
                className="absolute inset-y-0 left-0 bg-foreground/30 rounded-full"
                style={{ width: `${Math.min(progress + 10, 100)}%` }}
              />
              {/* Played */}
              <div
                className="absolute inset-y-0 left-0 bg-primary rounded-full transition-all"
                style={{ width: `${progress}%` }}
              />
              {/* Thumb */}
              <div
                className={cn(
                  'absolute top-1/2 -translate-y-1/2 -translate-x-1/2 rounded-full bg-primary transition-all duration-200',
                  progressFocused ? 'w-4 h-4' : 'w-3 h-3'
                )}
                style={{ left: `${progress}%` }}
              />
            </div>
          </div>

          {/* Controls row */}
          <div className="flex items-center justify-between">
            {/* Left: playback controls */}
            <div className="flex items-center gap-4">
              <button
                className="tv-focusable pill-focus w-9 h-9 rounded-full flex items-center justify-center text-foreground/80 hover:text-foreground"
                tabIndex={0}
                onFocus={() => setFocusedZone('bottom')}
                onBlur={() => setFocusedZone(prev => prev === 'bottom' ? 'none' : prev)}
                onClick={() => {
                  setCurrentEpisode(prev => Math.max(1, prev - 1))
                  setCurrentTime(0)
                  resetHideTimer()
                }}
              >
                <SkipBack size={20} />
              </button>
              <button
                className={cn(
                  'tv-focusable w-12 h-12 rounded-full bg-foreground/10 backdrop-blur-sm flex items-center justify-center text-foreground transition-all hover:bg-primary hover:text-primary-foreground focus-visible:bg-primary focus-visible:text-primary-foreground',
                  focusedZone === 'bottom' && 'ring-2 ring-primary/50'
                )}
                tabIndex={0}
                onFocus={() => setFocusedZone('bottom')}
                onBlur={() => setFocusedZone(prev => prev === 'bottom' ? 'none' : prev)}
                onClick={() => {
                  setIsPlaying(prev => !prev)
                  resetHideTimer()
                }}
              >
                {isPlaying ? <Pause size={24} /> : <Play size={24} className="ml-0.5" fill="currentColor" />}
              </button>
              <button
                className="tv-focusable pill-focus w-9 h-9 rounded-full flex items-center justify-center text-foreground/80 hover:text-foreground"
                tabIndex={0}
                onFocus={() => setFocusedZone('bottom')}
                onBlur={() => setFocusedZone(prev => prev === 'bottom' ? 'none' : prev)}
                onClick={() => {
                  setCurrentEpisode(prev => Math.min(totalEpisodes, prev + 1))
                  setCurrentTime(0)
                  resetHideTimer()
                }}
              >
                <SkipForward size={20} />
              </button>

              <span className="text-sm text-foreground/80 font-mono ml-2">
                {formatDuration(currentTime)} / {formatDuration(duration)}
              </span>
            </div>

            {/* Right: function buttons */}
            <div className="flex items-center gap-2">
              <button
                className={cn(
                  'tv-focusable pill-focus flex items-center gap-1.5 px-4 py-2 rounded-full text-sm font-medium transition-all',
                  activePanel === 'speed'
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-foreground/10 text-foreground/80 hover:bg-foreground/20'
                )}
                tabIndex={0}
                onFocus={() => setFocusedZone('bottom')}
                onBlur={() => setFocusedZone(prev => prev === 'bottom' ? 'none' : prev)}
                onClick={() => togglePanel('speed')}
              >
                <Gauge size={16} />
                倍速 {currentSpeed !== 1 ? `${currentSpeed}x` : ''}
              </button>
              {totalEpisodes > 1 && (
                <button
                  className={cn(
                    'tv-focusable pill-focus flex items-center gap-1.5 px-4 py-2 rounded-full text-sm font-medium transition-all',
                    activePanel === 'episodes'
                      ? 'bg-primary text-primary-foreground'
                      : 'bg-foreground/10 text-foreground/80 hover:bg-foreground/20'
                  )}
                  tabIndex={0}
                  onFocus={() => setFocusedZone('bottom')}
                  onBlur={() => setFocusedZone(prev => prev === 'bottom' ? 'none' : prev)}
                  onClick={() => togglePanel('episodes')}
                >
                  <List size={16} />
                  选集
                </button>
              )}
              <button
                className={cn(
                  'tv-focusable pill-focus flex items-center gap-1.5 px-4 py-2 rounded-full text-sm font-medium transition-all',
                  activePanel === 'source'
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-foreground/10 text-foreground/80 hover:bg-foreground/20'
                )}
                tabIndex={0}
                onFocus={() => setFocusedZone('bottom')}
                onBlur={() => setFocusedZone(prev => prev === 'bottom' ? 'none' : prev)}
                onClick={() => togglePanel('source')}
              >
                <RefreshCw size={16} />
                换源
              </button>
              <button
                className={cn(
                  'tv-focusable pill-focus flex items-center gap-1.5 px-4 py-2 rounded-full text-sm font-medium transition-all',
                  activePanel === 'skip'
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-foreground/10 text-foreground/80 hover:bg-foreground/20'
                )}
                tabIndex={0}
                onFocus={() => setFocusedZone('bottom')}
                onBlur={() => setFocusedZone(prev => prev === 'bottom' ? 'none' : prev)}
                onClick={() => togglePanel('skip')}
              >
                <FastForward size={16} />
                跳过
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Side panels */}
      {activePanel === 'speed' && (
        <SpeedPanel
          speeds={speeds}
          currentSpeed={currentSpeed}
          onSelect={(speed) => { setCurrentSpeed(speed); setActivePanel(null); resetHideTimer() }}
          onClose={() => setActivePanel(null)}
        />
      )}
      {activePanel === 'episodes' && (
        <EpisodesPanel
          totalEpisodes={totalEpisodes}
          currentEpisode={currentEpisode}
          onSelect={(ep) => { setCurrentEpisode(ep); setCurrentTime(0); setActivePanel(null); resetHideTimer() }}
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
      {activePanel === 'skip' && (
        <SkipPanel onClose={() => setActivePanel(null)} />
      )}
    </div>
  )
}

/* ===== Sub-components ===== */

function PanelWrapper({ title, onClose, children }: { title: string, onClose: () => void, children: React.ReactNode }) {
  return (
    <div
      className="absolute right-6 bottom-28 w-[300px] bg-card/95 backdrop-blur-xl rounded-2xl border border-border shadow-[var(--shadow-elevated)] z-50 animate-scale-in overflow-hidden"
      onClick={(e) => e.stopPropagation()}
    >
      <div className="flex items-center justify-between px-5 py-4 border-b border-border">
        <h3 className="text-base font-bold text-foreground">{title}</h3>
        <button
          className="tv-focusable w-8 h-8 rounded-full bg-secondary flex items-center justify-center"
          tabIndex={0}
          onClick={onClose}
        >
          <X size={14} className="text-muted-foreground" />
        </button>
      </div>
      <div className="p-4">
        {children}
      </div>
    </div>
  )
}

function SpeedPanel({ speeds, currentSpeed, onSelect, onClose }: {
  speeds: number[], currentSpeed: number, onSelect: (s: number) => void, onClose: () => void
}) {
  return (
    <PanelWrapper title="播放倍速" onClose={onClose}>
      <div className="grid grid-cols-3 gap-2">
        {speeds.map(speed => (
          <button
            key={speed}
            className={cn(
              'tv-focusable tab-focus py-2.5 rounded-lg text-sm font-medium transition-all',
              speed === currentSpeed
                ? 'bg-primary text-primary-foreground'
                : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
            )}
            tabIndex={0}
            onClick={() => onSelect(speed)}
          >
            {speed}x
          </button>
        ))}
      </div>
    </PanelWrapper>
  )
}

function EpisodesPanel({ totalEpisodes, currentEpisode, onSelect, onClose }: {
  totalEpisodes: number, currentEpisode: number, onSelect: (ep: number) => void, onClose: () => void
}) {
  return (
    <PanelWrapper title="选集" onClose={onClose}>
      <div className="grid grid-cols-4 gap-2 max-h-[240px] overflow-y-auto thin-scrollbar pr-1">
        {Array.from({ length: totalEpisodes }, (_, i) => i + 1).map(ep => (
          <button
            key={ep}
            className={cn(
              'tv-focusable tab-focus py-2.5 rounded-lg text-sm font-medium transition-all',
              ep === currentEpisode
                ? 'bg-primary text-primary-foreground'
                : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
            )}
            tabIndex={0}
            onClick={() => onSelect(ep)}
          >
            {ep}
          </button>
        ))}
      </div>
    </PanelWrapper>
  )
}

function SourcePanel({ sources, currentSource, onSelect, onClose }: {
  sources: string[], currentSource: number, onSelect: (idx: number) => void, onClose: () => void
}) {
  return (
    <PanelWrapper title="切换播放源" onClose={onClose}>
      <div className="flex flex-col gap-2">
        {sources.map((source, idx) => (
          <button
            key={idx}
            className={cn(
              'tv-focusable tab-focus py-3 px-4 rounded-lg text-sm font-medium text-left transition-all flex items-center justify-between',
              idx === currentSource
                ? 'bg-primary text-primary-foreground'
                : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
            )}
            tabIndex={0}
            onClick={() => onSelect(idx)}
          >
            <span>{source}</span>
            {idx === currentSource && (
              <span className="text-xs opacity-80">当前</span>
            )}
          </button>
        ))}
      </div>
    </PanelWrapper>
  )
}

function SkipPanel({ onClose }: { onClose: () => void }) {
  const [introSeconds, setIntroSeconds] = useState(45)
  const [outroSeconds, setOutroSeconds] = useState(90)
  const [introEnabled, setIntroEnabled] = useState(true)
  const [outroEnabled, setOutroEnabled] = useState(true)

  return (
    <PanelWrapper title="跳过片头/片尾" onClose={onClose}>
      <div className="space-y-4">
        <div>
          <div className="flex items-center justify-between mb-2">
            <span className="text-sm text-foreground">片头时长</span>
            <button
              className={cn(
                'tv-focusable px-3 py-1 rounded-full text-xs font-medium transition-all',
                introEnabled
                  ? 'bg-primary/20 text-primary'
                  : 'bg-secondary text-muted-foreground'
              )}
              tabIndex={0}
              onClick={() => setIntroEnabled(!introEnabled)}
            >
              {introEnabled ? '已开启' : '已关闭'}
            </button>
          </div>
          <div className="flex items-center gap-3">
            <button
              className="tv-focusable w-8 h-8 rounded-lg bg-secondary text-foreground flex items-center justify-center text-sm font-bold"
              tabIndex={0}
              onClick={() => setIntroSeconds(Math.max(0, introSeconds - 5))}
            >
              -
            </button>
            <div className="flex-1 h-2 bg-secondary rounded-full relative">
              <div
                className="absolute inset-y-0 left-0 bg-primary rounded-full transition-all"
                style={{ width: `${(introSeconds / 300) * 100}%` }}
              />
            </div>
            <button
              className="tv-focusable w-8 h-8 rounded-lg bg-secondary text-foreground flex items-center justify-center text-sm font-bold"
              tabIndex={0}
              onClick={() => setIntroSeconds(Math.min(300, introSeconds + 5))}
            >
              +
            </button>
            <span className="text-sm font-mono text-foreground min-w-[45px] text-right">
              {Math.floor(introSeconds / 60)}:{String(introSeconds % 60).padStart(2, '0')}
            </span>
          </div>
        </div>
        <div>
          <div className="flex items-center justify-between mb-2">
            <span className="text-sm text-foreground">片尾时长</span>
            <button
              className={cn(
                'tv-focusable px-3 py-1 rounded-full text-xs font-medium transition-all',
                outroEnabled
                  ? 'bg-primary/20 text-primary'
                  : 'bg-secondary text-muted-foreground'
              )}
              tabIndex={0}
              onClick={() => setOutroEnabled(!outroEnabled)}
            >
              {outroEnabled ? '已开启' : '已关闭'}
            </button>
          </div>
          <div className="flex items-center gap-3">
            <button
              className="tv-focusable w-8 h-8 rounded-lg bg-secondary text-foreground flex items-center justify-center text-sm font-bold"
              tabIndex={0}
              onClick={() => setOutroSeconds(Math.max(0, outroSeconds - 5))}
            >
              -
            </button>
            <div className="flex-1 h-2 bg-secondary rounded-full relative">
              <div
                className="absolute inset-y-0 left-0 bg-primary rounded-full transition-all"
                style={{ width: `${(outroSeconds / 300) * 100}%` }}
              />
            </div>
            <button
              className="tv-focusable w-8 h-8 rounded-lg bg-secondary text-foreground flex items-center justify-center text-sm font-bold"
              tabIndex={0}
              onClick={() => setOutroSeconds(Math.min(300, outroSeconds + 5))}
            >
              +
            </button>
            <span className="text-sm font-mono text-foreground min-w-[45px] text-right">
              {Math.floor(outroSeconds / 60)}:{String(outroSeconds % 60).padStart(2, '0')}
            </span>
          </div>
        </div>
        <button
          className="tv-focusable pill-focus w-full py-2.5 rounded-xl bg-primary text-primary-foreground font-medium text-sm mt-2"
          tabIndex={0}
          onClick={onClose}
        >
          保存设置
        </button>
      </div>
    </PanelWrapper>
  )
}
