/**
 * TV 空间导航：查找可聚焦的 spatial 节点、安全的选择器转义。
 */

export function escapeSpatialIdForSelector(id: string): string {
  if (typeof CSS !== 'undefined' && typeof CSS.escape === 'function') {
    return CSS.escape(id)
  }
  return id.replace(/\\/g, '\\\\').replace(/"/g, '\\"')
}

/** id 合法且存在于 DOM、可 program-focus */
export function queryFocusableSpatial(id: string): HTMLElement | null {
  if (!id || !/^[a-zA-Z0-9_-]+$/.test(id)) return null
  const el = document.querySelector(
    `[data-spatial-id="${escapeSpatialIdForSelector(id)}"]`
  ) as HTMLElement | null
  if (!el || typeof el.focus !== 'function' || !el.isConnected) return null
  if (el.getAttribute('tabindex') === '-1') return null
  if (el.closest('[inert]')) return null
  return el
}

export function isTvFocusLost(active: Element | null): boolean {
  if (!active || !(active instanceof HTMLElement)) return true
  if (active === document.body || active === document.documentElement) return true
  if (!active.isConnected) return true
  return false
}

const MAIN_SCROLL_ID = 'tv-main-content'

/**
 * 仅在焦点元素被裁切时滚动，避免网格内相邻移动时重复的 scrollIntoView / 强制同步布局。
 */
export function scrollSpatialFocusIntoView(el: HTMLElement, margin = 16): void {
  const rect = el.getBoundingClientRect()
  const main = document.getElementById(MAIN_SCROLL_ID)

  let rootTop = 0
  let rootLeft = 0
  let rootRight = window.innerWidth
  let rootBottom = window.innerHeight

  if (main?.contains(el)) {
    const r = main.getBoundingClientRect()
    rootTop = r.top
    rootLeft = r.left
    rootRight = r.right
    rootBottom = r.bottom
  }

  const clipped =
    rect.top < rootTop + margin ||
    rect.bottom > rootBottom - margin ||
    rect.left < rootLeft + margin ||
    rect.right > rootRight - margin

  if (!clipped) return

  el.scrollIntoView({
    block: 'nearest',
    behavior: 'instant',
    inline: 'nearest',
  })
}
