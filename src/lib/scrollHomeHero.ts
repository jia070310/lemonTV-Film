/**
 * Reset scroll so the home hero strip is fully visible.
 * Coalesced to one rAF — avoids stacking duplicate scroll work when focus returns from the sidebar.
 */
let scheduled = false

export function scrollHomeHeroIntoView(): void {
  if (scheduled) return
  scheduled = true

  const apply = (): void => {
    const main = document.getElementById('tv-main-content')
    const anchor = document.getElementById('home-hero-anchor')

    document.documentElement.scrollTop = 0
    ;(document.body as HTMLElement).scrollTop = 0

    if (main) {
      main.scrollTop = 0
      main.scrollTo({ top: 0, behavior: 'auto' })
    }

    anchor?.scrollIntoView({
      block: 'start',
      behavior: 'instant',
      inline: 'nearest',
    })
  }

  requestAnimationFrame(() => {
    apply()
    scheduled = false
  })
}
