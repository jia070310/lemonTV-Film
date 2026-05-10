/**
 * Detail route: TV WebViews often ignore scrollIntoView for nested layouts.
 * We align `#tv-main-content` by delta math and reset the episode strip scroll.
 */

function resetDetailEpisodeInnerScroll(): void {
  const ep = document.getElementById('detail-ep-scroll')
  if (ep) ep.scrollTop = 0
}

/** Scroll `main` so `el`’s top aligns near the top of the main viewport (if main can scroll). */
function scrollMainToAlignElementTop(
  main: HTMLElement,
  el: HTMLElement,
  margin = 8
): void {
  const mainRect = main.getBoundingClientRect()
  const elRect = el.getBoundingClientRect()
  const delta = elRect.top - mainRect.top - margin
  if (Math.abs(delta) < 2) return
  const nextTop = Math.max(0, main.scrollTop + delta)
  main.scrollTo({ top: nextTop, behavior: 'instant' })
}

function alignDetailInMain(el: HTMLElement | null): void {
  const main = document.getElementById('tv-main-content')
  if (!main || !el) return
  scrollMainToAlignElementTop(main, el)
}

/**
 * After spatial ArrowUp on the detail route, align main + episode strip.
 *
 * @returns true when detail-page handled scrolling (skip generic fallback).
 */
export function applyDetailSpatialScrollUp(
  nextId: string,
  focusedEl: HTMLElement
): boolean {
  if (!document.getElementById('detail-page-anchor')) return false

  const pageAnchor = document.getElementById('detail-page-anchor')
  const rightAnchor = document.getElementById('detail-right-anchor')
  const sourcesAnchor = document.getElementById('detail-sources-anchor')
  const episodesAnchor = document.getElementById('detail-episodes-anchor')

  if (nextId === 'detail-play' || nextId === 'detail-back') {
    resetDetailEpisodeInnerScroll()
    if (pageAnchor) alignDetailInMain(pageAnchor)
    return true
  }

  if (nextId === 'detail-playbtn' || nextId === 'detail-fav') {
    resetDetailEpisodeInnerScroll()
    if (rightAnchor) alignDetailInMain(rightAnchor)
    return true
  }

  if (nextId.startsWith('detail-src-')) {
    resetDetailEpisodeInnerScroll()
    if (sourcesAnchor) alignDetailInMain(sourcesAnchor)
    return true
  }

  if (nextId.startsWith('detail-ep-')) {
    if (episodesAnchor) alignDetailInMain(episodesAnchor)
    focusedEl.scrollIntoView({ block: 'nearest', behavior: 'instant', inline: 'nearest' })
    return true
  }

  focusedEl.scrollIntoView({ block: 'nearest', behavior: 'instant', inline: 'nearest' })
  return true
}
