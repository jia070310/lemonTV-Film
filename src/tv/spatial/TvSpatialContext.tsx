import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
} from 'react'
import { applyDetailSpatialScrollUp } from '@/lib/scrollDetailPane'

export type SpatialDir = 'up' | 'down' | 'left' | 'right'

export type SpatialNeighbors = Partial<Record<SpatialDir, string | undefined>>

type Getter = () => SpatialNeighbors

type TvSpatialContextValue = {
  registerSpatialNeighbors: (id: string, getNeighbors: Getter) => void
  unregisterSpatialNeighbors: (id: string) => void
  setMainSpatialEntry: (id: string | null) => void
  getMainSpatialEntry: () => string | null
}

const TvSpatialContext = createContext<TvSpatialContextValue | null>(null)

export function useTvSpatialContext(): TvSpatialContextValue {
  const v = useContext(TvSpatialContext)
  if (!v) {
    throw new Error('useTvSpatialContext must be used within TvSpatialProvider')
  }
  return v
}

/** Optional: pages outside provider should not throw */
export function useTvSpatialContextOptional(): TvSpatialContextValue | null {
  return useContext(TvSpatialContext)
}

export function TvSpatialProvider({ children }: { children: React.ReactNode }) {
  const gettersRef = useRef(new Map<string, Getter>())
  const mainEntryRef = useRef<string | null>(null)

  const registerSpatialNeighbors = useCallback((id: string, getNeighbors: Getter) => {
    gettersRef.current.set(id, getNeighbors)
  }, [])

  const unregisterSpatialNeighbors = useCallback((id: string) => {
    gettersRef.current.delete(id)
  }, [])

  const setMainSpatialEntry = useCallback((id: string | null) => {
    mainEntryRef.current = id
  }, [])

  const getMainSpatialEntry = useCallback(() => mainEntryRef.current, [])

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (
        e.key !== 'ArrowUp' &&
        e.key !== 'ArrowDown' &&
        e.key !== 'ArrowLeft' &&
        e.key !== 'ArrowRight'
      ) {
        return
      }

      const active = document.activeElement as HTMLElement | null
      if (!active) return

      if (
        active.tagName === 'INPUT' ||
        active.tagName === 'TEXTAREA' ||
        active.tagName === 'SELECT' ||
        active.isContentEditable ||
        active.closest('[data-spatial-arrow-through]')
      ) {
        return
      }

      const host =
        (active.closest('[data-spatial-id]') as HTMLElement | null) ??
        (active.hasAttribute('data-spatial-id') ? active : null)
      if (!host) return

      const id = host.getAttribute('data-spatial-id')
      if (!id) return

      const getter = gettersRef.current.get(id)
      if (!getter) return

      const neighbors = getter()
      const dir: SpatialDir =
        e.key === 'ArrowUp'
          ? 'up'
          : e.key === 'ArrowDown'
            ? 'down'
            : e.key === 'ArrowLeft'
              ? 'left'
              : 'right'
      const nextId = neighbors[dir]
      if (!nextId || !/^[a-zA-Z0-9_-]+$/.test(nextId)) return

      const next = document.querySelector(
        `[data-spatial-id="${nextId}"]`
      ) as HTMLElement | null
      if (!next || typeof next.focus !== 'function') return

      e.preventDefault()
      e.stopPropagation()
      next.focus({ preventScroll: true })

      const scrollAfter = (): void => {
        if (dir === 'up') {
          const detailHandled = applyDetailSpatialScrollUp(nextId, next)
          if (!detailHandled) {
            next.scrollIntoView({
              block: 'nearest',
              behavior: 'instant',
              inline: 'nearest',
            })
          }
          return
        }
        next.scrollIntoView({
          block: 'nearest',
          behavior: 'instant',
          inline: 'nearest',
        })
      }

      requestAnimationFrame(() => {
        scrollAfter()
        requestAnimationFrame(scrollAfter)
      })
    }

    window.addEventListener('keydown', onKeyDown, { capture: true })
    return () => window.removeEventListener('keydown', onKeyDown, { capture: true })
  }, [])

  const value = useMemo(
    () => ({
      registerSpatialNeighbors,
      unregisterSpatialNeighbors,
      setMainSpatialEntry,
      getMainSpatialEntry,
    }),
    [
      registerSpatialNeighbors,
      unregisterSpatialNeighbors,
      setMainSpatialEntry,
      getMainSpatialEntry,
    ]
  )

  return (
    <TvSpatialContext.Provider value={value}>{children}</TvSpatialContext.Provider>
  )
}
