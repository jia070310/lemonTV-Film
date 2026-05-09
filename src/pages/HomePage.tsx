import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { PosterCard } from '@/components/PosterCard'
import { heroMovies, movieList, categories } from '@/data/mockData'
import { SlidersHorizontal } from 'lucide-react'

const GRID_COLS = 6

export function HomePage() {
  const navigate = useNavigate()
  const [activeCategory, setActiveCategory] = useState(0)
  const [heroIndex, setHeroIndex] = useState(0)
  const scrollContainerRef = useRef<HTMLDivElement>(null)
  const heroRefs = useRef<(HTMLDivElement | null)[]>([])
  const catRefs = useRef<(HTMLButtonElement | null)[]>([])

  // Auto-scroll hero carousel to keep focused poster fully visible
  useEffect(() => {
    const container = scrollContainerRef.current
    const el = heroRefs.current[heroIndex]
    if (container && el) {
      const containerWidth = container.clientWidth
      const elLeft = el.offsetLeft
      const elWidth = el.offsetWidth
      const scrollLeft = elLeft - (containerWidth - elWidth) / 2
      container.scrollTo({ left: Math.max(0, scrollLeft), behavior: 'smooth' })
    }
  }, [heroIndex])

  const handleGridKeyDown = (e: React.KeyboardEvent<HTMLDivElement>, index: number) => {
    let nextIndex = -1
    const total = movieList.length

    if (e.key === 'ArrowRight') {
      const rowEnd = Math.floor(index / GRID_COLS) * GRID_COLS + GRID_COLS - 1
      if (index < Math.min(rowEnd, total - 1)) {
        nextIndex = index + 1
      }
    } else if (e.key === 'ArrowLeft') {
      const rowStart = Math.floor(index / GRID_COLS) * GRID_COLS
      if (index > rowStart) {
        nextIndex = index - 1
      }
    } else if (e.key === 'ArrowDown') {
      const next = index + GRID_COLS
      if (next < total) {
        nextIndex = next
      }
    } else if (e.key === 'ArrowUp') {
      const next = index - GRID_COLS
      if (next >= 0) {
        nextIndex = next
      } else {
        // First row: move focus back to category tabs
        e.preventDefault()
        e.stopPropagation()
        catRefs.current[activeCategory]?.focus()
        return
      }
    } else if (e.key === 'Enter') {
      e.preventDefault()
      navigate(`/detail/${movieList[index].id}`)
      return
    }

    if (nextIndex >= 0) {
      e.preventDefault()
      e.stopPropagation()
      const el = document.querySelector(`[data-card-index="${nextIndex}"]`) as HTMLElement
      el?.focus()
    }
  }

  return (
    <div id="home-page" className="h-full w-full flex flex-col bg-background overflow-hidden">
      {/* Top header bar */}
      <header className="flex items-center px-tv-2xl py-tv-md z-30 relative">
        <h1 className="text-xl font-bold text-foreground">热门影视</h1>
      </header>

      {/* Hero Carousel Section */}
      <section className="px-tv-2xl mt-tv-sm relative">
        <div ref={scrollContainerRef} className="flex gap-4 items-stretch h-[320px] overflow-x-auto no-scrollbar p-5">
          {heroMovies.map((movie, i) => (
            <div
              key={movie.id}
              ref={(el) => { heroRefs.current[i] = el }}
              id={i === 0 ? 'home-first-poster' : undefined}
              className={cn(
                'poster-focus tv-focusable relative rounded-xl overflow-hidden cursor-pointer transition-all duration-500 flex-shrink-0 h-[280px]',
                i === heroIndex ? 'w-[420px]' : 'w-[160px]'
              )}
              tabIndex={0}
              onFocus={() => setHeroIndex(i)}
              onMouseEnter={() => setHeroIndex(i)}
              onClick={() => navigate(`/detail/${movie.id}`)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  navigate(`/detail/${movie.id}`)
                } else if (e.key === 'ArrowRight') {
                  e.preventDefault()
                  e.stopPropagation()
                  const next = Math.min(heroMovies.length - 1, heroIndex + 1)
                  heroRefs.current[next]?.focus()
                  setHeroIndex(next)
                } else if (e.key === 'ArrowLeft') {
                  e.preventDefault()
                  e.stopPropagation()
                  const next = Math.max(0, heroIndex - 1)
                  heroRefs.current[next]?.focus()
                  setHeroIndex(next)
                } else if (e.key === 'ArrowDown') {
                  e.preventDefault()
                  e.stopPropagation()
                  catRefs.current[0]?.focus()
                }
              }}
            >
              <img
                src={i === heroIndex ? (movie.backdrop || movie.poster) : movie.poster}
                alt={movie.title}
                className="w-full h-full object-cover"
              />
              {/* Overlay for active hero */}
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
                      <span className="bg-primary/20 text-primary text-xs px-2 py-0.5 rounded-full">
                        {movie.tag}
                      </span>
                    )}
                  </div>
                </div>
              )}
              {/* Title overlay for inactive items */}
              {i !== heroIndex && (
                <div className="absolute inset-x-0 bottom-0 gradient-card p-3">
                  <p className="text-xs font-medium text-foreground truncate">{movie.title}</p>
                </div>
              )}
            </div>
          ))}

        </div>
      </section>

      {/* Category Tabs */}
      <section className="px-tv-2xl mt-tv-lg">
        <div className="flex items-center gap-6">
          {categories.map((cat, i) => (
            <button
              key={cat}
              ref={(el) => { catRefs.current[i] = el }}
              id={`cat-btn-${i}`}
              className={cn(
                'tv-focusable tab-focus px-6 py-2 rounded-full text-sm font-medium transition-all duration-300',
                i === activeCategory
                  ? 'bg-primary text-primary-foreground'
                  : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
              )}
              tabIndex={0}
              onClick={() => setActiveCategory(i)}
              onFocus={() => setActiveCategory(i)}
              onKeyDown={(e) => {
                if (e.key === 'ArrowRight') {
                  e.preventDefault()
                  e.stopPropagation()
                  const next = Math.min(categories.length, i + 1)
                  if (next < categories.length) {
                    catRefs.current[next]?.focus()
                  } else {
                    catRefs.current[categories.length]?.focus()
                  }
                } else if (e.key === 'ArrowLeft') {
                  e.preventDefault()
                  e.stopPropagation()
                  const next = Math.max(0, i - 1)
                  catRefs.current[next]?.focus()
                } else if (e.key === 'ArrowUp') {
                  e.preventDefault()
                  e.stopPropagation()
                  heroRefs.current[heroIndex]?.focus()
                } else if (e.key === 'ArrowDown') {
                  e.preventDefault()
                  e.stopPropagation()
                  const firstCard = document.getElementById('home-first-card')
                  firstCard?.focus()
                } else if (e.key === 'Enter') {
                  e.preventDefault()
                  setActiveCategory(i)
                }
              }}
            >
              {cat}
            </button>
          ))}

          {/* Filter button on the right */}
          <button
            ref={(el) => { catRefs.current[categories.length] = el }}
            id="cat-btn-filter"
            className="tv-focusable pill-focus ml-auto flex items-center gap-1.5 px-4 py-2 rounded-full bg-secondary text-sm text-secondary-foreground"
            tabIndex={0}
            onClick={() => navigate(`/filter?category=${encodeURIComponent(categories[activeCategory])}`)}
            onKeyDown={(e) => {
              if (e.key === 'ArrowLeft') {
                e.preventDefault()
                e.stopPropagation()
                catRefs.current[categories.length - 1]?.focus()
              } else if (e.key === 'ArrowUp') {
                e.preventDefault()
                e.stopPropagation()
                heroRefs.current[heroIndex]?.focus()
              } else if (e.key === 'ArrowDown') {
                e.preventDefault()
                e.stopPropagation()
                const firstCard = document.getElementById('home-first-card')
                firstCard?.focus()
              } else if (e.key === 'Enter') {
                e.preventDefault()
                navigate(`/filter?category=${encodeURIComponent(categories[activeCategory])}`)
              }
            }}
          >
            <SlidersHorizontal size={14} />
            筛选
          </button>
        </div>
      </section>

      {/* Movie Grid */}
      <section className="px-tv-2xl mt-tv-lg">
        <div className="grid grid-cols-6 gap-4 pb-4">
          {movieList.map((movie, i) => (
            <div
              key={movie.id}
              data-card-index={i}
              id={i === 0 ? 'home-first-card' : undefined}
              tabIndex={0}
              className="tv-focusable outline-none"
              onClick={() => navigate(`/detail/${movie.id}`)}
              onKeyDown={(e) => handleGridKeyDown(e, i)}
            >
              <PosterCard movie={movie} size="lg" className="w-full" />
            </div>
          ))}
        </div>
      </section>
    </div>
  )
}
