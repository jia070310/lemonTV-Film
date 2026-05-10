/**
 * Detail page: episode strip may scroll locally; main column uses `#tv-main-content`.
 * When focus is on the top row (播放/返回/立即播放/收藏), reset episode strip scroll.
 */

function resetDetailEpisodeInnerScroll(): void {
  const ep = document.getElementById('detail-ep-scroll')
  if (ep) ep.scrollTop = 0
}

function scrollVerticalOverflowParentToReveal(
  scrollRoot: HTMLElement,
  child: HTMLElement,
  margin = 8
): void {
  if (!scrollRoot.contains(child)) return
  const rootRect = scrollRoot.getBoundingClientRect()
  const elRect = child.getBoundingClientRect()
  if (elRect.top < rootRect.top + margin) {
    scrollRoot.scrollTop -= rootRect.top + margin - elRect.top
  } else if (elRect.bottom > rootRect.bottom - margin) {
    scrollRoot.scrollTop += elRect.bottom - (rootRect.bottom - margin)
  }
}

/** Inner → outer; run twice in one frame so rects settle after nested adjustments. */
export function revealDetailSpatialFocus(focusedEl: HTMLElement): void {
  const epScroll = document.getElementById('detail-ep-scroll')
  const main = document.getElementById('tv-main-content')

  const run = (): void => {
    if (epScroll?.contains(focusedEl)) {
      scrollVerticalOverflowParentToReveal(epScroll, focusedEl)
    }
    if (main?.contains(focusedEl)) {
      scrollVerticalOverflowParentToReveal(main, focusedEl)
    }
  }

  run()
  run()
}

/**
 * @returns true when on detail route (caller skips generic scrollIntoView).
 */
export function applyDetailSpatialScrollAfterFocus(
  nextId: string,
  focusedEl: HTMLElement
): boolean {
  if (!document.getElementById('detail-page-anchor')) return false

  const snapEpisodeStrip =
    nextId === 'detail-playbtn' ||
    nextId === 'detail-fav' ||
    nextId === 'detail-play' ||
    nextId === 'detail-back'

  if (snapEpisodeStrip) {
    resetDetailEpisodeInnerScroll()
  }

  revealDetailSpatialFocus(focusedEl)
  return true
}
