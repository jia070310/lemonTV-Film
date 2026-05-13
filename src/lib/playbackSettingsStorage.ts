const KEY = 'lemonTv.playback.settings.v1'

export type PlaybackSettings = {
  /** 设置页：自动跳过片头片尾总开关 */
  autoSkipIntroOutro: boolean
  /** 设置页：播放结束自动下一集 */
  autoPlayNext: boolean
  /** 设置页：默认倍速文案，如 1.0x */
  defaultSpeed: string
  /** 片头跳过秒数（播放器内可改） */
  introSkipSec: number
  /** 片尾跳过秒数（距结束不足该值时切下一集或结束） */
  outroSkipSec: number
  skipIntroEnabled: boolean
  skipOutroEnabled: boolean
}

export const DEFAULT_PLAYBACK_SETTINGS: PlaybackSettings = {
  /** 总开关默认关闭，避免未配置时误跳片头片尾 */
  autoSkipIntroOutro: false,
  autoPlayNext: true,
  defaultSpeed: '1.0x',
  introSkipSec: 90,
  outroSkipSec: 120,
  skipIntroEnabled: true,
  skipOutroEnabled: true,
}

function clampSec(n: number, max = 600): number {
  if (!Number.isFinite(n) || n < 0) return 0
  return Math.min(max, Math.floor(n))
}

function pickBool(v: unknown, fallback: boolean): boolean {
  return typeof v === 'boolean' ? v : fallback
}

export function parseSpeedToNumber(speedLabel: string): number {
  const m = /^([\d.]+)x?$/.exec(String(speedLabel).trim())
  if (!m) return 1
  const n = parseFloat(m[1])
  if (!Number.isFinite(n) || n <= 0) return 1
  return Math.min(3, Math.max(0.25, n))
}

export function getPlaybackSettings(): PlaybackSettings {
  if (typeof localStorage === 'undefined') return { ...DEFAULT_PLAYBACK_SETTINGS }
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return { ...DEFAULT_PLAYBACK_SETTINGS }
    const o = JSON.parse(raw) as Partial<PlaybackSettings>
    return {
      ...DEFAULT_PLAYBACK_SETTINGS,
      ...o,
      autoSkipIntroOutro: pickBool(
        o.autoSkipIntroOutro,
        DEFAULT_PLAYBACK_SETTINGS.autoSkipIntroOutro
      ),
      autoPlayNext: pickBool(o.autoPlayNext, DEFAULT_PLAYBACK_SETTINGS.autoPlayNext),
      skipIntroEnabled: pickBool(o.skipIntroEnabled, DEFAULT_PLAYBACK_SETTINGS.skipIntroEnabled),
      skipOutroEnabled: pickBool(o.skipOutroEnabled, DEFAULT_PLAYBACK_SETTINGS.skipOutroEnabled),
      defaultSpeed:
        typeof o.defaultSpeed === 'string' && o.defaultSpeed.trim()
          ? o.defaultSpeed.trim()
          : DEFAULT_PLAYBACK_SETTINGS.defaultSpeed,
      introSkipSec: clampSec(
        typeof o.introSkipSec === 'number' ? o.introSkipSec : DEFAULT_PLAYBACK_SETTINGS.introSkipSec
      ),
      outroSkipSec: clampSec(
        typeof o.outroSkipSec === 'number' ? o.outroSkipSec : DEFAULT_PLAYBACK_SETTINGS.outroSkipSec
      ),
    }
  } catch {
    return { ...DEFAULT_PLAYBACK_SETTINGS }
  }
}

export function savePlaybackSettings(partial: Partial<PlaybackSettings>): PlaybackSettings {
  const next = { ...getPlaybackSettings(), ...partial }
  if (typeof localStorage !== 'undefined') {
    try {
      localStorage.setItem(KEY, JSON.stringify(next))
    } catch {
      /* ignore */
    }
  }
  return next
}
