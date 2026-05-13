const KEY = 'lemonTv.playback.progress.v1'
const MAX_ENTRIES = 200

export type EpisodePlaybackProgress = {
  vodId: string
  sourceIndex: number
  /** 1-based 集序号 */
  episodeIndex: number
  positionSec: number
  durationSec: number
  updatedAt: number
}

function keyOf(vodId: string, sourceIndex: number, episodeIndex: number): string {
  return `${vodId}|${sourceIndex}|${episodeIndex}`
}

function parseList(raw: string | null): EpisodePlaybackProgress[] {
  if (!raw) return []
  try {
    const v = JSON.parse(raw) as unknown
    if (!Array.isArray(v)) return []
    const out: EpisodePlaybackProgress[] = []
    for (const row of v) {
      if (!row || typeof row !== 'object') continue
      const o = row as Record<string, unknown>
      if (typeof o.vodId !== 'string') continue
      const sourceIndex = typeof o.sourceIndex === 'number' ? o.sourceIndex : 0
      const episodeIndex = typeof o.episodeIndex === 'number' ? o.episodeIndex : 1
      const positionSec =
        typeof o.positionSec === 'number' && Number.isFinite(o.positionSec)
          ? Math.max(0, o.positionSec)
          : 0
      const durationSec =
        typeof o.durationSec === 'number' && Number.isFinite(o.durationSec)
          ? Math.max(0, o.durationSec)
          : 0
      const updatedAt =
        typeof o.updatedAt === 'number' && Number.isFinite(o.updatedAt) ? o.updatedAt : 0
      out.push({ vodId: o.vodId, sourceIndex, episodeIndex, positionSec, durationSec, updatedAt })
    }
    return out
  } catch {
    return []
  }
}

function writeList(list: EpisodePlaybackProgress[]): void {
  if (typeof localStorage === 'undefined') return
  try {
    localStorage.setItem(KEY, JSON.stringify(list.slice(0, MAX_ENTRIES)))
  } catch {
    /* ignore */
  }
}

export function getEpisodePlaybackProgress(
  vodId: string,
  sourceIndex: number,
  episodeIndex: number
): EpisodePlaybackProgress | null {
  const list = parseList(localStorage.getItem(KEY))
  const k = keyOf(vodId, sourceIndex, episodeIndex)
  const hit = list.find((x) => keyOf(x.vodId, x.sourceIndex, x.episodeIndex) === k)
  return hit ?? null
}

/** 同一影片在任意线路/集下最近更新的进度（用于收藏进播放、详情默认集） */
export function getLatestEpisodeProgressForVod(vodId: string): EpisodePlaybackProgress | null {
  const list = parseList(localStorage.getItem(KEY))
  const rows = list.filter((x) => x.vodId === vodId)
  if (rows.length === 0) return null
  return rows.reduce((a, b) => (a.updatedAt >= b.updatedAt ? a : b))
}

/** 有本地进度且未到末尾则返回续播秒数，否则 null（由调用方再决定是否跳过片头） */
export function resumePositionSecFromRecord(
  rec: EpisodePlaybackProgress | null,
  mediaDurationSec: number
): number | null {
  if (!rec || !(rec.positionSec > 0)) return null
  const d = mediaDurationSec > 0 ? mediaDurationSec : rec.durationSec
  if (d > 0 && rec.positionSec >= d - 0.5) return null
  return rec.positionSec
}

export function saveEpisodePlaybackProgress(entry: Omit<EpisodePlaybackProgress, 'updatedAt'>): void {
  const list = parseList(localStorage.getItem(KEY))
  const k = keyOf(entry.vodId, entry.sourceIndex, entry.episodeIndex)
  const filtered = list.filter((x) => keyOf(x.vodId, x.sourceIndex, x.episodeIndex) !== k)
  const next: EpisodePlaybackProgress = {
    ...entry,
    positionSec: Math.max(0, entry.positionSec),
    durationSec: Math.max(0, entry.durationSec),
    updatedAt: Date.now(),
  }
  writeList([next, ...filtered])
}
