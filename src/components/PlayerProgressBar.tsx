import { useCallback, useRef, type ComponentPropsWithoutRef } from 'react'
import { cn } from '@/lib/utils'

/** 对齐 LomenTV0 `PlayerScreen.kt` 中进度条与气泡常量 */
const BUBBLE_FOCUS_SCALE = 1.12
const FOCUS_ANIM_MS = 170

function formatDuration(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds <= 0) return '00:00'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  if (h > 0)
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

export type PlayerProgressBarProps = {
  currentTime: number
  duration: number
  /** 进度条自身焦点（遥控器） */
  progressFocused: boolean
  /** 快进退态：气泡与焦点态一致高亮（Lomen `forceProgressBubbleHighlight`） */
  seekModeHighlight: boolean
  onSeekToSeconds: (sec: number) => void
  onProgressFocusChange: (focused: boolean) => void
  className?: string
  barRef?: import('react').Ref<HTMLDivElement>
  /** 叠在可聚焦轨道上（如 `useTvSpatialNode` 返回的 data-spatial-id） */
  trackProps?: ComponentPropsWithoutRef<'div'>
}

export function PlayerProgressBar({
  currentTime,
  duration,
  progressFocused,
  seekModeHighlight,
  onSeekToSeconds,
  onProgressFocusChange,
  className,
  barRef,
  trackProps,
}: PlayerProgressBarProps) {
  const trackRef = useRef<HTMLDivElement | null>(null)
  const setRefs = useCallback(
    (node: HTMLDivElement | null) => {
      trackRef.current = node
      if (!barRef) return
      if (typeof barRef === 'function') {
        barRef(node)
      } else {
        const r = barRef as { current: HTMLDivElement | null }
        r.current = node
      }
    },
    [barRef]
  )

  const ratio = duration > 0 ? Math.min(1, Math.max(0, currentTime / duration)) : 0
  const progressPct = ratio * 100
  const bubbleHighlighted = progressFocused || seekModeHighlight
  const bubbleAnchorPct = Math.min(94, Math.max(6, progressPct))

  const seekFromClientX = useCallback(
    (clientX: number) => {
      const el = trackRef.current
      if (!el || duration <= 0) return
      const rect = el.getBoundingClientRect()
      if (rect.width <= 0) return
      const frac = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width))
      onSeekToSeconds(Math.round(frac * duration))
    },
    [duration, onSeekToSeconds]
  )

  const bubbleText = `${formatDuration(currentTime)} / ${formatDuration(duration)}`

  return (
    <div className={cn('relative w-full', className)}>
      <div className="relative mb-2 min-h-9 w-full">
        <div
          className="absolute -top-2 z-[1] flex max-w-[170px] min-w-[88px] justify-center transition-transform ease-out"
          style={{
            left: `${bubbleAnchorPct}%`,
            transform: `translateX(-50%) scale(${bubbleHighlighted ? BUBBLE_FOCUS_SCALE : 1})`,
            transitionDuration: `${FOCUS_ANIM_MS}ms`,
          }}
        >
          <div
            className={cn(
              'rounded px-2.5 py-1 text-center text-[10px] font-medium leading-tight tracking-tight text-black whitespace-nowrap transition-colors ease-out',
              bubbleHighlighted ? 'bg-[#fff176]' : 'bg-yellow-400'
            )}
            style={{
              borderRadius: '4px',
              transitionDuration: `${FOCUS_ANIM_MS}ms`,
              boxShadow: bubbleHighlighted
                ? '0 0 12px rgba(255,255,255,0.55), 0 0 18px rgba(250,204,21,0.85), 0 4px 12px rgba(0,0,0,0.25)'
                : '0 2px 10px rgba(250,204,21,0.55), 0 1px 4px rgba(0,0,0,0.2)',
            }}
          >
            {bubbleText}
          </div>
        </div>
      </div>

      <div
        ref={setRefs}
        {...trackProps}
        role="slider"
        aria-valuemin={0}
        aria-valuemax={Math.max(0, Math.floor(duration))}
        aria-valuenow={Math.floor(currentTime)}
        className={cn(
          /* player-progress-track：避免全局 .tv-focusable:focus 整块黄底盖住已播/未播比例 */
          'player-progress-track tv-focusable relative z-0 h-1.5 w-full cursor-pointer rounded-[3px] bg-white/20 outline-none transition-[height] ease-out',
          trackProps?.className
        )}
        style={{ transitionDuration: `${FOCUS_ANIM_MS}ms` }}
        onFocus={() => onProgressFocusChange(true)}
        onBlur={() => onProgressFocusChange(false)}
        onClick={(e) => {
          e.stopPropagation()
          seekFromClientX(e.clientX)
        }}
        onPointerDown={(e) => {
          if (e.button !== 0) return
          e.currentTarget.setPointerCapture(e.pointerId)
          seekFromClientX(e.clientX)
        }}
        onPointerMove={(e) => {
          if (!e.currentTarget.hasPointerCapture(e.pointerId)) return
          seekFromClientX(e.clientX)
        }}
        onPointerUp={(e) => {
          if (e.currentTarget.hasPointerCapture(e.pointerId)) {
            e.currentTarget.releasePointerCapture(e.pointerId)
          }
        }}
      >
        <div
          className="pointer-events-none absolute inset-y-0 left-0 z-[1] rounded-[3px] bg-yellow-400 transition-[width] ease-linear"
          style={{ width: `${progressPct}%`, transitionDuration: '120ms' }}
        />
      </div>
    </div>
  )
}
