import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { PosterCard } from '@/components/PosterCard'
import { heroMovies, movieList, categories } from '@/data/mockData'
import { ChevronLeft, ChevronRight, SlidersHorizontal } from 'lucide-react'

export function HomePage() {
  const navigate = useNavigate()
  const [activeCategory, setActiveCategory] = useState(0)
  const [heroIndex, setHeroIndex] = useState(0)

  return (
    <div className="h-full w-full flex flex-col bg-background overflow-hidden">
      {/* Top header bar */}
      <header className="flex items-center px-tv-2xl py-tv-md z-30 relative">
        <h1 className="text-xl font-bold text-foreground">热门影视</h1>
      </header>

      {/* Hero Carousel Section */}
      <section className="px-tv-2xl mt-tv-sm relative">
        <div className="flex gap-4 items-stretch h-[280px]">
          {heroMovies.map((movie, i) => (
            <div
              key={movie.id}
              className={cn(
                'poster-focus tv-focusable relative rounded-xl overflow-hidden cursor-pointer transition-all duration-500 flex-shrink-0',
                i === heroIndex ? 'w-[420px]' : 'w-[160px]'
              )}
              tabIndex={0}
              onFocus={() => setHeroIndex(i)}
              onMouseEnter={() => setHeroIndex(i)}
              onClick={() => navigate(`/detail/${movie.id}`)}
              onKeyDown={(e) => { if (e.key === 'Enter') navigate(`/detail/${movie.id}`) }}
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

          {/* Carousel nav arrows */}
          <div className="absolute right-12 top-4 flex items-center gap-2">
            <button
              className="tv-focusable w-8 h-8 rounded-full bg-secondary/80 flex items-center justify-center"
              tabIndex={0}
              onClick={() => setHeroIndex(Math.max(0, heroIndex - 1))}
            >
              <ChevronLeft size={16} className="text-foreground" />
            </button>
            <button
              className="tv-focusable w-8 h-8 rounded-full bg-secondary/80 flex items-center justify-center"
              tabIndex={0}
              onClick={() => setHeroIndex(Math.min(heroMovies.length - 1, heroIndex + 1))}
            >
              <ChevronRight size={16} className="text-foreground" />
            </button>
          </div>
        </div>
      </section>

      {/* Category Tabs */}
      <section className="px-tv-2xl mt-tv-lg">
        <div className="flex items-center gap-6">
          {categories.map((cat, i) => (
            <button
              key={cat}
              className={cn(
                'tv-focusable tab-focus px-6 py-2 rounded-full text-sm font-medium transition-all duration-300',
                i === activeCategory
                  ? 'bg-primary text-primary-foreground'
                  : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
              )}
              tabIndex={0}
              onClick={() => setActiveCategory(i)}
            >
              {cat}
            </button>
          ))}

          {/* Filter button on the right */}
          <button
            className="tv-focusable pill-focus ml-auto flex items-center gap-1.5 px-4 py-2 rounded-full bg-secondary text-sm text-secondary-foreground"
            tabIndex={0}
            onClick={() => navigate(`/filter?category=${encodeURIComponent(categories[activeCategory])}`)}
          >
            <SlidersHorizontal size={14} />
            筛选
          </button>
        </div>
      </section>

      {/* Movie Grid */}
      <section className="flex-1 px-tv-2xl mt-tv-lg overflow-hidden">
        <div className="grid grid-cols-6 gap-4 h-full pb-4">
          {movieList.map((movie) => (
            <PosterCard key={movie.id} movie={movie} size="lg" className="w-full" />
          ))}
        </div>
      </section>
    </div>
  )
}
