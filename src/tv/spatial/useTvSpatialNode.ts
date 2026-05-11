import { useEffect, useRef } from 'react'
import { useTvSpatialContextOptional, type SpatialNeighbors } from './TvSpatialContext'

/**
 * Register explicit D-pad neighbors (same idea as Compose TV focusProperties).
 * Returns props to spread onto the focusable root element.
 */
export function useTvSpatialNode(
  id: string,
  getNeighbors: () => SpatialNeighbors,
  deps: unknown[] = []
): {
  'data-spatial-id': string
  tabIndex: 0
} {
  const ctx = useTvSpatialContextOptional()
  const getNeighborsRef = useRef(getNeighbors)
  getNeighborsRef.current = getNeighbors

  useEffect(() => {
    if (!ctx) return
    const getter = () => getNeighborsRef.current()
    ctx.registerSpatialNeighbors(id, getter)
    return () => ctx.unregisterSpatialNeighbors(id)
    // eslint-disable-next-line react-hooks/exhaustive-deps -- deps bridges caller state (hero index, etc.)
  }, [ctx, id, ...deps])

  return { 'data-spatial-id': id, tabIndex: 0 as const }
}

/** Page sets default focus target when pressing Right from sidebar */
export function useTvSpatialMainEntry(entryId: string | null) {
  const ctx = useTvSpatialContextOptional()

  useEffect(() => {
    if (!ctx) return
    if (entryId == null || entryId === '') {
      ctx.setMainSpatialEntry(null)
      return
    }
    ctx.setMainSpatialEntry(entryId)
    return () => ctx.setMainSpatialEntry(null)
  }, [ctx, entryId])
}
