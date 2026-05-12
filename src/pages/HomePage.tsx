import { useState, useRef, useLayoutEffect, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { gridDownNeighborIndex } from '@/lib/gridSpatialNav'
import { scrollHomeHeroIntoView } from '@/lib/scrollHomeHero'
import { useVersionUpdate } from '@/context/VersionUpdateContext'
import { useTvSpatialMainEntry, useTvSpatialNode } from '@/tv/spatial'
import { PosterCard } from '@/components/PosterCard'
import { categories } from '@/data/mockData'
import type { Movie } from '@/data/mockData'
import type { HomeNavCategory } from '@/data/maccmsTaxonomy'
import { MACCMS_HERO_RECOMMEND_LEVEL, MACCMS_HOME_HERO_COUNT } from '@/config/maccms'
import { fetchHomeHeroMovies, fetchHomeLatestForCategory } from '@/lib/maccmsApi'
import {
  homeGridListEqual,
  homeHeroStripEqual,
  readGridSessionCache,
  readHeroSessionCache,
  writeGridSessionCache,
  writeHeroSessionCache,
} from '@/lib/homeSessionCache'
import { SlidersHorizontal } from 'lucide-react'

const GRID_COLS = 6

function HomeHeroTile({
  i,
  movie,
  heroIndex,
  setHeroIndex,
  heroIndexRef,
  heroCount,
  activeCategory,
  navigate,
  heroRef,
  showUpdateBanner,
}: {
  i: number
  movie: Movie
  heroIndex: number
  setHeroIndex: (n: number) => void
  heroIndexRef: React.MutableRefObject<number>
  heroCount: number
  activeCategory: number
  navigate: (path: string) => void
  heroRef: (el: HTMLDivElement | null) => void
  showUpdateBanner: boolean
}) {
  const spatial = useTvSpatialNode(
    `home-hero-${i}`,
    () => ({
      left: i > 0 ? `home-hero-${i - 1}` : 'nav-0',
      right: i < heroCount - 1 ? `home-hero-${i + 1}` : undefined,
      up: showUpdateBanner ? 'home-update-banner' : undefined,
      down: `home-cat-${activeCategory}`,
    }),
    [i, heroCount, activeCategory, showUpdateBanner]
  )

  return (
    <div
      ref={heroRef}
      id={i === 0 ? 'home-first-poster' : undefined}
      data-hero-index={i}
      className={cn(
        'hero-carousel-item relative h-[280px] flex-shrink-0 cursor-pointer overflow-visible rounded-xl transition-[width] duration-200 ease-out',
        i === heroIndex ? 'w-[420px]' : 'w-[160px]'
      )}
      onClick={() => navigate(`/detail/${movie.id}`)}
    >
      <div className="absolute inset-0 z-0 overflow-hidden rounded-xl">
        <div
          {...spatial}
          data-hero-index={i}
          className="poster-focus tv-focusable absolute inset-0 z-[1] outline-none"
          onFocus={() => {
            heroIndexRef.current = i
            setHeroIndex(i)
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault()
              navigate(`/detail/${movie.id}`)
            }
          }}
        >
          <img
            key={`${movie.id}-${i === heroIndex ? 'wide' : 'cover'}`}
            src={
              (i === heroIndex ? movie.backdrop || movie.poster : movie.poster) ||
              '/images/movie-poster-1.png'
            }
            alt={movie.title}
            className="h-full w-full rounded-xl object-cover"
            onError={(e) => {
              const el = e.currentTarget
              if (!el.src.endsWith('/images/movie-poster-1.png')) {
                el.src = '/images/movie-poster-1.png'
              }
            }}
          />
        </div>
      </div>
      {i === heroIndex && (
        <div className="pointer-events-none absolute inset-0 z-[2] flex flex-col justify-end overflow-hidden rounded-xl p-6 gradient-hero">
          <h2 className="mb-1 text-2xl font-bold text-foreground">{movie.title}</h2>
          <p className="mb-3 line-clamp-2 max-w-[280px] text-sm text-muted-foreground">
            {movie.description}
          </p>
          <div className="flex flex-wrap items-center gap-3">
            <span className="text-xs text-muted-foreground">{movie.year}</span>
            <span className="text-xs text-muted-foreground">{movie.genre}</span>
            {movie.tag && (
              <span className="rounded-full bg-white/50 px-2 py-0.5 text-xs font-medium text-yellow-400 shadow-sm backdrop-blur-sm">
                {movie.tag}
              </span>
            )}
          </div>
        </div>
      )}
      {i !== heroIndex && (
        <div className="pointer-events-none absolute inset-x-0 bottom-0 z-[2] overflow-hidden rounded-b-xl gradient-card p-3">
          <p className="truncate text-xs font-medium text-foreground">{movie.title}</p>
        </div>
      )}
    </div>
  )
}

function HomeUpdateBanner({
  heroIndex,
  remoteVersion,
  currentVersion,
  onOpen,
}: {
  heroIndex: number
  remoteVersion: string
  currentVersion: string
  onOpen: () => void
}) {
  const spatial = useTvSpatialNode(
    'home-update-banner',
    () => ({
      down: `home-hero-${heroIndex}`,
    }),
    [heroIndex]
  )

  return (
    <section className="px-tv-2xl pt-tv-sm" aria-live="polite">
      <button
        type="button"
        {...spatial}
        className="tv-focusable flex max-w-2xl items-center gap-3 rounded-2xl bg-primary px-5 py-3 text-left shadow-md outline-none"
        onClick={() => onOpen()}
        onKeyDown={(e) => {
          if (e.key === 'Enter') {
            e.preventDefault()
            onOpen()
          }
        }}
      >
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-black/25">
          <span className="text-lg font-black text-primary-foreground">!</span>
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-bold text-primary-foreground">
            仓库 v{remoteVersion} · 当前 v{currentVersion}
          </p>
          <p className="text-xs text-primary-foreground/85">
            有更新 · 已全局提醒 · 按确定查看；约 10 秒后收起本横幅
          </p>
        </div>
      </button>
    </section>
  )
}

function HomeCategoryTab({
  i,
  label,
  activeCategory,
  setActiveCategory,
  heroIndexRef,
  categoriesLen,
  heroStripLen,
  showUpdateBanner,
}: {
  i: number
  label: string
  activeCategory: number
  setActiveCategory: (n: number) => void
  heroIndexRef: React.MutableRefObject<number>
  categoriesLen: number
  heroStripLen: number
  showUpdateBanner: boolean
}) {
  const spatial = useTvSpatialNode(
    `home-cat-${i}`,
    () => ({
      left: i > 0 ? `home-cat-${i - 1}` : undefined,
      right: i < categoriesLen - 1 ? `home-cat-${i + 1}` : 'home-cat-filter',
      up:
        heroStripLen > 0
          ? `home-hero-${heroIndexRef.current}`
          : showUpdateBanner
            ? 'home-update-banner'
            : undefined,
      down: 'home-grid-0',
    }),
    [i, categoriesLen, heroStripLen, showUpdateBanner]
  )

  return (
    <button
      type="button"
      {...spatial}
      id={`cat-btn-${i}`}
      className={cn(
        'tv-focusable tab-focus inline-flex items-center gap-2.5 px-6 py-2 rounded-full text-sm font-medium transition-[box-shadow,transform,colors] duration-150 ease-out',
        i === activeCategory
          ? 'tv-tab-selected'
          : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
      )}
      aria-pressed={i === activeCategory}
      onClick={() => setActiveCategory(i)}
      onKeyDown={(e) => {
        if (e.key === 'Enter') {
          e.preventDefault()
          setActiveCategory(i)
        }
      }}
    >
      <span>{label}</span>
      {i === activeCategory && (
        <span
          className="h-1.5 w-1.5 flex-shrink-0 rounded-full bg-current opacity-90"
          aria-hidden
        />
      )}
    </button>
  )
}

function HomeFilterTab({
  categoriesLen,
  navigate,
  heroIndexRef,
  categoryLabel,
  heroStripLen,
  showUpdateBanner,
}: {
  categoriesLen: number
  navigate: (path: string) => void
  heroIndexRef: React.MutableRefObject<number>
  categoryLabel: string
  heroStripLen: number
  showUpdateBanner: boolean
}) {
  const spatial = useTvSpatialNode(
    'home-cat-filter',
    () => ({
      left: `home-cat-${categoriesLen - 1}`,
      up:
        heroStripLen > 0
          ? `home-hero-${heroIndexRef.current}`
          : showUpdateBanner
            ? 'home-update-banner'
            : undefined,
      down: 'home-grid-0',
    }),
    [categoriesLen, heroStripLen, showUpdateBanner]
  )

  return (
    <button
      type="button"
      {...spatial}
      id="cat-btn-filter"
      className="tv-focusable pill-focus ml-auto flex items-center gap-1.5 px-4 py-2 rounded-full bg-secondary text-sm text-secondary-foreground hover:bg-surface-hover"
      onClick={() =>
        navigate(`/filter?category=${encodeURIComponent(categoryLabel)}`)
      }
      onKeyDown={(e) => {
        if (e.key === 'Enter') {
          e.preventDefault()
          navigate(`/filter?category=${encodeURIComponent(categoryLabel)}`)
        }
      }}
    >
      <SlidersHorizontal size={14} />
      筛选
    </button>
  )
}

function HomeGridCell({
  index,
  movie,
  total,
  activeCategory,
}: {
  index: number
  movie: Movie
  total: number
  activeCategory: number
}) {
  const row = Math.floor(index / GRID_COLS)
  const col = index % GRID_COLS

  const spatial = useTvSpatialNode(
    `home-grid-${index}`,
    () => {
      const downIdx = gridDownNeighborIndex(index, total, GRID_COLS)
      return {
      up:
        row === 0
          ? `home-cat-${activeCategory}`
          : `home-grid-${index - GRID_COLS}`,
      down: downIdx !== undefined ? `home-grid-${downIdx}` : undefined,
      left: col === 0 ? 'nav-0' : `home-grid-${index - 1}`,
      right:
        col < GRID_COLS - 1 && index + 1 < total
          ? `home-grid-${index + 1}`
          : undefined,
      }
    },
    [index, total, activeCategory]
  )

  return (
    <div
      data-card-index={index}
      id={index === 0 ? 'home-first-card' : undefined}
      className="rounded-lg outline-none"
    >
      <PosterCard
        movie={movie}
        size="lg"
        focusable={false}
        posterPriority
        posterShellProps={{ ...spatial }}
        className="w-full"
      />
    </div>
  )
}

export function HomePage() {
  const navigate = useNavigate()
  const { showHomeBanner, remote, openUpdateDialog, currentVersionName } =
    useVersionUpdate()
  const [activeCategory, setActiveCategory] = useState(0)
  const [heroIndex, setHeroIndex] = useState(0)
  const heroIndexRef = useRef(0)
  const scrollContainerRef = useRef<HTMLDivElement>(null)
  const heroRefs = useRef<(HTMLDivElement | null)[]>([])

  const [heroMovies, setHeroMovies] = useState<Movie[]>(
    () => readHeroSessionCache() ?? []
  )
  /** 首次海报请求结束后，才区分「接口确实无数据」与「尚在静默拉取」 */
  const [heroFetchSettled, setHeroFetchSettled] = useState(false)
  const [gridMovies, setGridMovies] = useState<Movie[]>(
    () => readGridSessionCache(0) ?? []
  )
  const [heroError, setHeroError] = useState<string | null>(null)
  const [gridError, setGridError] = useState<string | null>(null)

  useTvSpatialMainEntry(heroMovies.length > 0 ? 'home-hero-0' : 'home-cat-0')

  useEffect(() => {
    let cancelled = false
    setHeroError(null)
    ;(async () => {
      try {
        const hero = await fetchHomeHeroMovies(MACCMS_HOME_HERO_COUNT)
        if (!cancelled) {
          setHeroMovies((prev) => (homeHeroStripEqual(prev, hero) ? prev : hero))
          writeHeroSessionCache(hero)
          setHeroError(null)
        }
      } catch (e) {
        if (!cancelled) {
          const snap = readHeroSessionCache()
          if (snap == null || snap.length === 0) {
            setHeroMovies([])
            setHeroError(e instanceof Error ? e.message : '海报加载失败')
          }
        }
      } finally {
        if (!cancelled) setHeroFetchSettled(true)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    const cat = categories[activeCategory] as HomeNavCategory
    let cancelled = false
    const cachedGrid = readGridSessionCache(activeCategory)
    if (cachedGrid !== null) {
      setGridMovies(cachedGrid)
      setGridError(null)
    } else {
      setGridMovies([])
    }
    ;(async () => {
      try {
        const list = await fetchHomeLatestForCategory(cat)
        if (!cancelled) {
          setGridMovies((prev) => (homeGridListEqual(prev, list) ? prev : list))
          writeGridSessionCache(activeCategory, list)
          setGridError(null)
        }
      } catch (e) {
        if (!cancelled) {
          const snap = readGridSessionCache(activeCategory)
          if (snap == null || snap.length === 0) {
            setGridMovies([])
            setGridError(e instanceof Error ? e.message : '列表加载失败')
          }
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [activeCategory])

  useEffect(() => {
    const onFocusIn = (e: FocusEvent) => {
      const target = e.target as HTMLElement | null
      if (!target?.hasAttribute?.('data-hero-index')) return
      const strip = document.getElementById('hero-carousel-scroll')
      if (!strip?.contains(target)) return
      const related = e.relatedTarget as HTMLElement | null
      if (related && strip.contains(related)) return
      scrollHomeHeroIntoView()
    }
    document.addEventListener('focusin', onFocusIn, true)
    return () => document.removeEventListener('focusin', onFocusIn, true)
  }, [])

  useLayoutEffect(() => {
    const container = scrollContainerRef.current
    const el = heroRefs.current[heroIndex]
    if (container && el) {
      const containerWidth = container.clientWidth
      const elLeft = el.offsetLeft
      const elWidth = el.offsetWidth
      const scrollLeft = elLeft - (containerWidth - elWidth) / 2
      container.scrollTo({ left: Math.max(0, scrollLeft), behavior: 'auto' })
    }
  }, [heroIndex])

  const setHeroRef = (i: number, el: HTMLDivElement | null) => {
    heroRefs.current[i] = el
  }

  const catLen = categories.length
  const categoryLabel = categories[activeCategory] ?? categories[0]
  const updateBannerOn = showHomeBanner && Boolean(remote)

  return (
    <div
      id="home-page"
      className="flex min-h-full w-full flex-col bg-background pb-10"
    >
      <header className="flex items-center px-tv-2xl py-tv-md z-30 relative">
        <h1 className="text-xl font-bold text-foreground">热门影视</h1>
        {(heroError || gridError) && (
          <span
            className="ml-4 text-sm text-destructive truncate max-w-xl"
            title={[heroError, gridError].filter(Boolean).join(' · ')}
          >
            {[heroError, gridError].filter(Boolean).join(' · ')}
          </span>
        )}
      </header>

      {updateBannerOn && remote && (
        <HomeUpdateBanner
          heroIndex={heroIndex}
          remoteVersion={remote.versionName}
          currentVersion={currentVersionName}
          onOpen={openUpdateDialog}
        />
      )}

      <section id="home-hero-anchor" className="px-tv-2xl mt-tv-sm relative overflow-visible scroll-mt-0">
        <div
          id="hero-carousel-scroll"
          ref={scrollContainerRef}
          className="flex gap-4 items-center min-h-[300px] overflow-x-auto overflow-y-visible no-scrollbar px-5 py-6"
        >
          {heroMovies.length > 0 ? (
            heroMovies.map((movie, i) => (
              <HomeHeroTile
                key={movie.id}
                i={i}
                movie={movie}
                heroIndex={heroIndex}
                setHeroIndex={setHeroIndex}
                heroIndexRef={heroIndexRef}
                heroCount={heroMovies.length}
                activeCategory={activeCategory}
                navigate={navigate}
                heroRef={(el) => setHeroRef(i, el)}
                showUpdateBanner={updateBannerOn}
              />
            ))
          ) : heroFetchSettled && !heroError ? (
            <p className="max-w-xl px-5 text-sm text-muted-foreground">
              暂无「推荐等级 {MACCMS_HERO_RECOMMEND_LEVEL}」的影片用于海报位。请在 MACCMS
              后台将首页幻灯片条目的推荐值设为 {MACCMS_HERO_RECOMMEND_LEVEL}。
            </p>
          ) : heroMovies.length === 0 && heroError ? (
            <p className="text-destructive px-5 text-sm">{heroError}</p>
          ) : null}
        </div>
      </section>

      <section className="px-tv-2xl mt-tv-lg">
        <div className="flex items-center gap-6">
          {categories.map((cat, i) => (
            <HomeCategoryTab
              key={cat}
              i={i}
              label={cat}
              activeCategory={activeCategory}
              setActiveCategory={setActiveCategory}
              heroIndexRef={heroIndexRef}
              categoriesLen={catLen}
              heroStripLen={heroMovies.length}
              showUpdateBanner={updateBannerOn}
            />
          ))}
          <HomeFilterTab
            categoriesLen={catLen}
            navigate={navigate}
            heroIndexRef={heroIndexRef}
            categoryLabel={categoryLabel}
            heroStripLen={heroMovies.length}
            showUpdateBanner={updateBannerOn}
          />
        </div>
      </section>

      <section className="px-tv-2xl mt-tv-lg">
        <div className="grid grid-cols-6 gap-4 pb-4">
          {gridMovies.map((movie, i) => (
            <HomeGridCell
              key={`${activeCategory}-${movie.id}`}
              index={i}
              movie={movie}
              total={gridMovies.length}
              activeCategory={activeCategory}
            />
          ))}
        </div>
      </section>
    </div>
  )
}
