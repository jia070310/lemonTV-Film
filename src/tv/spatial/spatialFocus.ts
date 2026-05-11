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

/**
 * 沿祖先链调整每一个 overflow 滚动容器，使焦点节点完整落在可视区内。
 * 解决收藏/历史等「主区域 + 内层滚动」嵌套时，仅用 nearest scrollIntoView 无法回到首行的问题。
 */
export function scrollSpatialFocusIntoView(el: HTMLElement, margin = 16): void {
  let parent: HTMLElement | null = el.parentElement
  while (parent && parent !== document.documentElement) {
    const er = el.getBoundingClientRect()
    const style = window.getComputedStyle(parent)
    const canScrollY =
      (style.overflowY === 'auto' || style.overflowY === 'scroll') &&
      parent.scrollHeight > parent.clientHeight + 1
    if (canScrollY) {
      const pr = parent.getBoundingClientRect()
      if (er.top < pr.top + margin) {
        parent.scrollTop -= pr.top + margin - er.top
      } else if (er.bottom > pr.bottom - margin) {
        parent.scrollTop += er.bottom - (pr.bottom - margin)
      }
    }
    const canScrollX =
      (style.overflowX === 'auto' || style.overflowX === 'scroll') &&
      parent.scrollWidth > parent.clientWidth + 1
    if (canScrollX) {
      const pr = parent.getBoundingClientRect()
      if (er.left < pr.left + margin) {
        parent.scrollLeft -= pr.left + margin - er.left
      } else if (er.right > pr.right - margin) {
        parent.scrollLeft += er.right - (pr.right - margin)
      }
    }
    parent = parent.parentElement
  }
}
