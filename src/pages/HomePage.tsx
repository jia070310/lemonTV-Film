import { useState, useRef, useLayoutEffect, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { scrollHomeHeroIntoView } from '@/lib/scrollHomeHero'
import { useTvSpatialMainEntry, useTvSpatialNode } from '@/tv/spatial'
import { PosterCard } from '@/components/PosterCard'
import { heroMovies, movieList, categories } from '@/data/mockData'
import type { Movie } from '@/data/mockData'
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
}) {
  const spatial = useTvSpatialNode(
    `home-hero-${i}`,
    () => ({
      left: i > 0 ? `home-hero-${i - 1}` : 'nav-0',
      right: i < heroCount - 1 ? `home-hero-${i + 1}` : undefined,
      down: `home-cat-${activeCategory}`,
    }),
    [i, heroCount, activeCategory]
  )

  return (
    <div
      ref={heroRef}
      id={i === 0 ? 'home-first-poster' : undefined}
      data-hero-index={i}
      {...spatial}
      className={cn(
        'hero-carousel-item poster-focus relative rounded-xl overflow-hidden cursor-pointer transition-[width] duration-200 ease-out flex-shrink-0 h-[280px]',
        i === heroIndex ? 'w-[420px]' : 'w-[160px]'
      )}
      onFocus={() => {
        heroIndexRef.current = i
        setHeroIndex(i)
      }}
      onClick={() => navigate(`/detail/${movie.id}`)}
      onKeyDown={(e) => {
        if (e.key === 'Enter') {
          e.preventDefault()
          navigate(`/detail/${movie.id}`)
        }
      }}
    >
      <img
        src={i === heroIndex ? (movie.backdrop || movie.poster) : movie.poster}
        alt={movie.title}
        className="w-full h-full object-cover"
      />
      {i === heroIndex && (
        <div className="absolute inset-0 gradient-hero flex flex-col justify-end p-6">
          <h2 className="text-2xl font-bold text-foreground mb-1">{movie.title}</h2>
          <p className="text-sm text-muted-foreground mb-3 line-clamp-2 max-w-[280px]">
            {movie.description}
          </p>
          <div className="flex items-center gap-3">
            <span className="text-primary font-bold text-sm">{movie.rating} 分</span>
            <span className="text-muted-foreground text-xs">{movie.year}</span>
            <span className="text-muted-foreground text-xs">{movie.genre}</span>
            {movie.tag && (
              <span className="tv-tab-selected text-xs px-2 py-0.5 rounded-full">
                {movie.tag}
              </span>
            )}
          </div>
        </div>
      )}
      {i !== heroIndex && (
        <div className="absolute inset-x-0 bottom-0 gradient-card p-3">
          <p className="text-xs font-medium text-foreground truncate">{movie.title}</p>
        </div>
      )}
    </div>
  )
}

function HomeCategoryTab({
  i,
  label,
  activeCategory,
  setActiveCategory,
  heroIndexRef,
  categoriesLen,
}: {
  i: number
  label: string
  activeCategory: number
  setActiveCategory: (n: number) => void
  heroIndexRef: React.MutableRefObject<number>
  categoriesLen: number
}) {
  const spatial = useTvSpatialNode(
    `home-cat-${i}`,
    () => ({
      left: i > 0 ? `home-cat-${i - 1}` : undefined,
      right: i < categoriesLen - 1 ? `home-cat-${i + 1}` : 'home-cat-filter',
      up: `home-hero-${heroIndexRef.current}`,
      down: 'home-grid-0',
    }),
    [i, categoriesLen]
  )

  return (
    <button
      type="button"
      {...spatial}
      id={`cat-btn-${i}`}
      className={cn(
        'tv-focusable tab-focus px-6 py-2 rounded-full text-sm font-medium transition-[box-shadow,transform,colors] duration-150 ease-out',
        i === activeCategory
          ? 'tv-tab-selected'
          : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
      )}
      onClick={() => setActiveCategory(i)}
      onFocus={() => setActiveCategory(i)}
      onKeyDown={(e) => {
        if (e.key === 'Enter') {
          e.preventDefault()
          setActiveCategory(i)
        }
      }}
    >
      {label}
    </button>
  )
}

function HomeFilterTab({
  categoriesLen,
  navigate,
  heroIndexRef,
  categoryLabel,
}: {
  categoriesLen: number
  navigate: (path: string) => void
  heroIndexRef: React.MutableRefObject<number>
  categoryLabel: string
}) {
  const spatial = useTvSpatialNode(
    'home-cat-filter',
    () => ({
      left: `home-cat-${categoriesLen - 1}`,
      up: `home-hero-${heroIndexRef.current}`,
      down: 'home-grid-0',
    }),
    [categoriesLen]
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
  navigate,
}: {
  index: number
  movie: Movie
  total: number
  activeCategory: number
  navigate: (path: string) => void
}) {
  const row = Math.floor(index / GRID_COLS)
  const col = index % GRID_COLS

  const spatial = useTvSpatialNode(
    `home-grid-${index}`,
    () => ({
      up:
        row === 0
          ? `home-cat-${activeCategory}`
          : `home-grid-${index - GRID_COLS}`,
      down:
        index + GRID_COLS < total ? `home-grid-${index + GRID_COLS}` : undefined,
      left: col === 0 ? 'nav-0' : `home-grid-${index - 1}`,
      right:
        col < GRID_COLS - 1 && index + 1 < total
          ? `home-grid-${index + 1}`
          : undefined,
    }),
    [index, total, activeCategory]
  )

  return (
    <div
      {...spatial}
      data-card-index={index}
      id={index === 0 ? 'home-first-card' : undefined}
      className="poster-focus tv-focusable rounded-lg outline-none"
      onClick={() => navigate(`/detail/${movie.id}`)}
      onKeyDown={(e) => {
        if (e.key === 'Enter') {
          e.preventDefault()
          navigate(`/detail/${movie.id}`)
        }
      }}
    >
      <PosterCard movie={movie} size="lg" focusable={false} className="w-full" />
    </div>
  )
}

export function HomePage() {
  const navigate = useNavigate()
  const [activeCategory, setActiveCategory] = useState(0)
  const [heroIndex, setHeroIndex] = useState(0)
  const heroIndexRef = useRef(0)
  const scrollContainerRef = useRef<HTMLDivElement>(null)
  const heroRefs = useRef<(HTMLDivElement | null)[]>([])

  useTvSpatialMainEntry('home-hero-0')

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

  return (
    <div id="home-page" className="h-full w-full flex flex-col bg-background overflow-hidden">
      <header className="flex items-center px-tv-2xl py-tv-md z-30 relative">
        <h1 className="text-xl font-bold text-foreground">热门影视</h1>
      </header>

      <section id="home-hero-anchor" className="px-tv-2xl mt-tv-sm relative overflow-visible scroll-mt-0">
        <div
          id="hero-carousel-scroll"
          ref={scrollContainerRef}
          className="flex gap-4 items-center min-h-[300px] overflow-x-auto overflow-y-visible no-scrollbar px-5 py-6"
        >
          {heroMovies.map((movie, i) => (
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
            />
          ))}
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
            />
          ))}
          <HomeFilterTab
            categoriesLen={catLen}
            navigate={navigate}
            heroIndexRef={heroIndexRef}
            categoryLabel={categoryLabel}
          />
        </div>
      </section>

      <section className="px-tv-2xl mt-tv-lg">
        <div className="grid grid-cols-6 gap-4 pb-4">
          {movieList.map((movie, i) => (
            <HomeGridCell
              key={movie.id}
              index={i}
              movie={movie}
              total={movieList.length}
              activeCategory={activeCategory}
              navigate={navigate}
            />
          ))}
        </div>
      </section>
    </div>
  )
}
