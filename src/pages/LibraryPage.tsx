import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { gridDownNeighborIndex } from '@/lib/gridSpatialNav'
import { cn } from '@/lib/utils'
import { useTvSpatialMainEntry, useTvSpatialNode } from '@/tv/spatial'
import { PosterCard } from '@/components/PosterCard'
import type { Movie } from '@/data/mockData'
import {
  clearWatchHistory,
  getFavorites,
  getWatchHistory,
  LIBRARY_CHANGED_EVENT,
  removeFavoritesByIds,
  resumePlayerHref,
  type WatchHistoryItem,
} from '@/lib/userLibraryStorage'
import { getLatestEpisodeProgressForVod } from '@/lib/playbackProgressStorage'
import { Heart, Clock, FileX, Trash2 } from 'lucide-react'

type TabType = 'favorite' | 'history'

const GRID_COLS = 6

function LibraryTabBtn({
  which,
  active,
  onSelect,
  icon,
  label,
  hasGrid,
  downTarget,
}: {
  which: 0 | 1
  active: boolean
  onSelect: () => void
  icon: React.ReactNode
  label: string
  hasGrid: boolean
  /** 有工具条时优先落到工具条首按钮，否则落到网格 */
  downTarget?: string
}) {
  const spatial = useTvSpatialNode(
    `library-tab-${which}`,
    () => ({
      left: which === 1 ? 'library-tab-0' : undefined,
      right: which === 0 ? 'library-tab-1' : undefined,
      down: downTarget ?? (hasGrid ? 'library-grid-0' : undefined),
    }),
    [which, hasGrid, downTarget]
  )

  return (
    <button
      type="button"
      {...spatial}
      className={cn(
        'tv-focusable tab-focus flex items-center gap-2 px-6 py-3 rounded-xl text-sm font-medium transition-[box-shadow,transform,colors] duration-150 ease-out',
        active
          ? 'tv-tab-selected'
          : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
      )}
      onClick={onSelect}
    >
      {icon}
      {label}
    </button>
  )
}

function LibraryGridCell({
  index,
  movie,
  total,
  gridUpId,
  progressPercent,
  navigateHref,
  selectionMode,
  selected,
  onSelectionToggle,
}: {
  index: number
  movie: Movie
  total: number
  /** 首行向上的 TV 空间目标（工具条首钮或 Tab） */
  gridUpId: string
  progressPercent?: number
  navigateHref?: string
  selectionMode?: boolean
  selected?: boolean
  onSelectionToggle?: () => void
}) {
  const row = Math.floor(index / GRID_COLS)
  const col = index % GRID_COLS

  const spatial = useTvSpatialNode(
    `library-grid-${index}`,
    () => {
      const downIdx = gridDownNeighborIndex(index, total, GRID_COLS)
      return {
        up: row === 0 ? gridUpId : `library-grid-${index - GRID_COLS}`,
        down: downIdx !== undefined ? `library-grid-${downIdx}` : undefined,
        left: col === 0 ? 'nav-0' : `library-grid-${index - 1}`,
        right:
          col < GRID_COLS - 1 && index + 1 < total
            ? `library-grid-${index + 1}`
            : undefined,
      }
    },
    [index, total, gridUpId]
  )

  const pct =
    progressPercent != null
      ? Math.round(Math.min(100, Math.max(0, progressPercent)))
      : undefined

  return (
    <div className="library-poster-cell relative">
      <div className="relative z-[1]">
        <PosterCard
          movie={movie}
          size="lg"
          focusable={false}
          posterShellProps={{ ...spatial }}
          className="w-full"
          navigateHref={selectionMode ? undefined : navigateHref}
          selectionMode={selectionMode}
          selected={selected}
          onSelectionToggle={onSelectionToggle}
        />
      </div>
      {pct != null && (
        <div className="pointer-events-none absolute bottom-0 left-0 right-0 z-0 mx-0.5 h-1 overflow-hidden rounded-full bg-muted">
          <div
            className="h-full rounded-full bg-primary"
            style={{ width: `${pct}%` }}
          />
        </div>
      )}
    </div>
  )
}

type LibraryConfirmState =
  | null
  | { kind: 'removeFavorites'; ids: string[] }
  | { kind: 'clearHistory' }

/** 中文「取消 / 确定」，避免 WebView 原生 confirm 显示英文按钮 */
function LibraryConfirmDialog({
  message,
  onCancel,
  onConfirm,
}: {
  message: string
  onCancel: () => void
  onConfirm: () => void
}) {
  const cancelSpatial = useTvSpatialNode(
    'library-confirm-cancel',
    () => ({ right: 'library-confirm-ok' }),
    []
  )
  const okSpatial = useTvSpatialNode(
    'library-confirm-ok',
    () => ({ left: 'library-confirm-cancel' }),
    []
  )

  useEffect(() => {
    const id = requestAnimationFrame(() => {
      document
        .querySelector<HTMLElement>('[data-spatial-id="library-confirm-cancel"]')
        ?.focus({ preventScroll: true })
    })
    return () => cancelAnimationFrame(id)
  }, [])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault()
        onCancel()
      }
    }
    window.addEventListener('keydown', onKey, true)
    return () => window.removeEventListener('keydown', onKey, true)
  }, [onCancel])

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/70 p-6 backdrop-blur-sm"
      role="presentation"
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel()
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="library-confirm-title"
        className={cn(
          'w-full max-w-md rounded-2xl border border-border bg-card p-6',
          'shadow-[var(--shadow-elevated)]'
        )}
        onClick={(e) => e.stopPropagation()}
      >
        <p
          id="library-confirm-title"
          className="text-base leading-relaxed text-foreground"
        >
          {message}
        </p>
        <div className="mt-6 flex justify-end gap-3">
          <button
            type="button"
            {...cancelSpatial}
            className="tv-dialog-action-btn tv-focusable pill-focus rounded-full px-6 py-2.5 text-sm font-medium"
            onClick={onCancel}
          >
            取消
          </button>
          <button
            type="button"
            {...okSpatial}
            className="tv-dialog-action-btn tv-focusable pill-focus rounded-full px-6 py-2.5 text-sm font-medium"
            onClick={onConfirm}
          >
            确定
          </button>
        </div>
      </div>
    </div>
  )
}

export function LibraryPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = (searchParams.get('tab') as TabType) || 'favorite'

  const [favorites, setFavorites] = useState<Movie[]>(() => getFavorites())
  const [history, setHistory] = useState<WatchHistoryItem[]>(() => getWatchHistory())
  const [favoriteSelectMode, setFavoriteSelectMode] = useState(false)
  const [selectedFavoriteIds, setSelectedFavoriteIds] = useState<Set<string>>(() => new Set())
  const [libraryConfirm, setLibraryConfirm] = useState<LibraryConfirmState>(null)

  const setTab = (t: TabType) => {
    setSearchParams({ tab: t })
    setFavoriteSelectMode(false)
    setSelectedFavoriteIds(new Set())
  }

  const isFavorite = tab === 'favorite'

  const refresh = useCallback(() => {
    setFavorites(getFavorites())
    setHistory(getWatchHistory())
  }, [])

  useEffect(() => {
    refresh()
  }, [tab, refresh])

  useEffect(() => {
    const fn = () => refresh()
    window.addEventListener(LIBRARY_CHANGED_EVENT, fn)
    return () => window.removeEventListener(LIBRARY_CHANGED_EVENT, fn)
  }, [refresh])

  const items: Movie[] = isFavorite ? favorites : history.map((h) => h.movie)
  const hasGrid = items.length > 0

  const tab0Down = useMemo(() => {
    if (isFavorite && favorites.length > 0) {
      return favoriteSelectMode ? 'library-fav-select-all' : 'library-fav-manage'
    }
    if (!isFavorite && history.length > 0) return 'library-hist-clear'
    return undefined
  }, [isFavorite, favorites.length, history.length, favoriteSelectMode])

  const tab1Down = useMemo(() => {
    if (!isFavorite && history.length > 0) return 'library-hist-clear'
    if (isFavorite && favorites.length > 0) {
      return favoriteSelectMode ? 'library-fav-select-all' : 'library-fav-manage'
    }
    return undefined
  }, [isFavorite, favorites.length, history.length, favoriteSelectMode])

  const gridUpId = useMemo(() => {
    if (isFavorite && favorites.length > 0) {
      return favoriteSelectMode ? 'library-fav-select-all' : 'library-fav-manage'
    }
    if (!isFavorite && history.length > 0) return 'library-hist-clear'
    return isFavorite ? 'library-tab-0' : 'library-tab-1'
  }, [isFavorite, favorites.length, history.length, favoriteSelectMode])

  const spatialFavManage = useTvSpatialNode(
    'library-fav-manage',
    () =>
      !isFavorite || favorites.length === 0 || favoriteSelectMode
        ? {}
        : {
            up: 'library-tab-0',
            down: 'library-grid-0',
            left: 'library-tab-1',
            right: 'library-tab-1',
          },
    [isFavorite, favorites.length, favoriteSelectMode]
  )

  const spatialFavSelectAll = useTvSpatialNode(
    'library-fav-select-all',
    () =>
      !isFavorite || favorites.length === 0 || !favoriteSelectMode
        ? {}
        : {
            up: 'library-tab-0',
            down: 'library-grid-0',
            left: 'library-tab-1',
            right: 'library-fav-remove',
          },
    [isFavorite, favorites.length, favoriteSelectMode]
  )

  const spatialFavRemove = useTvSpatialNode(
    'library-fav-remove',
    () =>
      !isFavorite || favorites.length === 0 || !favoriteSelectMode
        ? {}
        : {
            up: 'library-tab-0',
            down: 'library-grid-0',
            left: 'library-fav-select-all',
            right: 'library-fav-done',
          },
    [isFavorite, favorites.length, favoriteSelectMode]
  )

  const spatialFavDone = useTvSpatialNode(
    'library-fav-done',
    () =>
      !isFavorite || favorites.length === 0 || !favoriteSelectMode
        ? {}
        : {
            up: 'library-tab-0',
            down: 'library-grid-0',
            left: 'library-fav-remove',
          },
    [isFavorite, favorites.length, favoriteSelectMode]
  )

  const spatialHistClear = useTvSpatialNode(
    'library-hist-clear',
    () =>
      isFavorite || history.length === 0
        ? {}
        : {
            up: 'library-tab-1',
            down: 'library-grid-0',
            left: 'library-tab-0',
            right: 'library-tab-0',
          },
    [isFavorite, history.length]
  )

  useTvSpatialMainEntry('library-tab-0')

  const toggleFavoriteSelect = (id: string) => {
    setSelectedFavoriteIds((prev) => {
      const n = new Set(prev)
      if (n.has(id)) n.delete(id)
      else n.add(id)
      return n
    })
  }

  const selectAllFavorites = () => {
    setSelectedFavoriteIds(new Set(favorites.map((m) => m.id)))
  }

  const removeSelectedFavorites = () => {
    const ids = [...selectedFavoriteIds]
    if (ids.length === 0) return
    setLibraryConfirm({ kind: 'removeFavorites', ids })
  }

  const exitFavoriteSelectMode = () => {
    setFavoriteSelectMode(false)
    setSelectedFavoriteIds(new Set())
  }

  const onClearHistory = () => {
    if (history.length === 0) return
    setLibraryConfirm({ kind: 'clearHistory' })
  }

  return (
    <div className="relative flex h-full flex-col overflow-hidden bg-background p-8">
      <div className="mb-6 flex flex-shrink-0 items-center justify-between gap-4">
        <div className="flex flex-wrap items-center gap-2">
          <LibraryTabBtn
            which={0}
            active={isFavorite}
            onSelect={() => setTab('favorite')}
            icon={<Heart size={18} />}
            label="我的收藏"
            hasGrid={isFavorite && hasGrid}
            downTarget={tab0Down}
          />
          <LibraryTabBtn
            which={1}
            active={!isFavorite}
            onSelect={() => setTab('history')}
            icon={<Clock size={18} />}
            label="播放记录"
            hasGrid={!isFavorite && hasGrid}
            downTarget={tab1Down}
          />
        </div>

        <div className="flex flex-shrink-0 flex-wrap items-center justify-end gap-3">
          {isFavorite && favorites.length > 0 && !favoriteSelectMode && (
            <button
              type="button"
              {...spatialFavManage}
              className="tv-focusable pill-focus rounded-full bg-secondary px-4 py-2 text-sm font-medium text-secondary-foreground hover:bg-surface-hover"
              onClick={() => {
                setFavoriteSelectMode(true)
                setSelectedFavoriteIds(new Set())
              }}
            >
              勾选删除
            </button>
          )}
          {isFavorite && favorites.length > 0 && favoriteSelectMode && (
            <>
              <button
                type="button"
                {...spatialFavSelectAll}
                className="tv-focusable pill-focus rounded-full bg-secondary px-4 py-2 text-sm font-medium text-secondary-foreground hover:bg-surface-hover"
                onClick={selectAllFavorites}
              >
                全选
              </button>
              <button
                type="button"
                {...spatialFavRemove}
                disabled={selectedFavoriteIds.size === 0}
                className="tv-focusable pill-focus inline-flex items-center gap-1.5 rounded-full bg-destructive/90 px-4 py-2 text-sm font-medium text-destructive-foreground hover:bg-destructive disabled:pointer-events-none disabled:opacity-40"
                onClick={removeSelectedFavorites}
              >
                <Trash2 size={16} />
                删除选中{selectedFavoriteIds.size > 0 ? `（${selectedFavoriteIds.size}）` : ''}
              </button>
              <button
                type="button"
                {...spatialFavDone}
                className="tv-focusable pill-focus rounded-full bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
                onClick={exitFavoriteSelectMode}
              >
                完成
              </button>
            </>
          )}
          {!isFavorite && history.length > 0 && (
            <button
              type="button"
              {...spatialHistClear}
              className="tv-focusable pill-focus rounded-full bg-secondary px-4 py-2 text-sm font-medium text-secondary-foreground hover:bg-surface-hover"
              onClick={onClearHistory}
            >
              清除记录
            </button>
          )}
          <span className="text-sm text-muted-foreground whitespace-nowrap">
            共 <span className="font-bold text-primary">{items.length}</span> 部
          </span>
        </div>
      </div>

      <div className="thin-scrollbar min-h-0 flex-1 overflow-x-hidden overflow-y-auto scroll-py-10 px-0.5 py-3">
        {items.length > 0 ? (
          <div className="grid grid-cols-6 gap-4 pb-10">
            {items.map((movie, i) => {
              const hist = !isFavorite ? history.find((h) => h.movie.id === movie.id) : undefined
              const latestFav = isFavorite ? getLatestEpisodeProgressForVod(movie.id) : undefined
              const latestAny = getLatestEpisodeProgressForVod(movie.id)
              const resumeHref =
                !isFavorite && hist
                  ? resumePlayerHref(movie.id, {
                      sourceIndex: latestAny?.sourceIndex ?? hist.sourceIndex,
                      episodeIndex: latestAny?.episodeIndex ?? hist.episodeIndex,
                    })
                  : isFavorite && latestFav
                    ? resumePlayerHref(movie.id, {
                        sourceIndex: latestFav.sourceIndex,
                        episodeIndex: latestFav.episodeIndex,
                      })
                    : undefined
              return (
                <LibraryGridCell
                  key={movie.id}
                  index={i}
                  movie={movie}
                  total={items.length}
                  gridUpId={gridUpId}
                  progressPercent={hist?.progress}
                  navigateHref={resumeHref}
                  selectionMode={isFavorite && favoriteSelectMode}
                  selected={selectedFavoriteIds.has(movie.id)}
                  onSelectionToggle={() => toggleFavoriteSelect(movie.id)}
                />
              )
            })}
          </div>
        ) : (
          <div className="flex h-full flex-col items-center justify-center text-muted-foreground">
            <FileX size={56} className="mb-4 opacity-20" />
            <p className="text-lg">
              {isFavorite ? '暂无收藏影片' : '暂无播放记录'}
            </p>
            <p className="mt-2 text-sm opacity-50">
              {isFavorite
                ? '在详情页点击收藏按钮添加'
                : '在播放页或详情预览观看后会自动记录，点击海报从上次进度续播'}
            </p>
          </div>
        )}
      </div>

      {libraryConfirm && (
        <LibraryConfirmDialog
          message={
            libraryConfirm.kind === 'removeFavorites'
              ? `确定取消收藏选中的 ${libraryConfirm.ids.length} 部影片？`
              : '确定清除全部播放记录？'
          }
          onCancel={() => setLibraryConfirm(null)}
          onConfirm={() => {
            const c = libraryConfirm
            if (c.kind === 'removeFavorites') {
              removeFavoritesByIds(c.ids)
              setFavoriteSelectMode(false)
              setSelectedFavoriteIds(new Set())
            } else {
              clearWatchHistory()
            }
            refresh()
            setLibraryConfirm(null)
          }}
        />
      )}
    </div>
  )
}
