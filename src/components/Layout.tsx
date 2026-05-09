import { useState, useEffect } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { Logo } from '@/components/Logo'
import {
  Home,
  Tv,
  Film,
  Clapperboard,
  Sparkles,
  Search,
  Heart,
  Clock,
  Settings,
} from 'lucide-react'

interface NavItem {
  label: string
  icon: React.ReactNode
  path: string
  matchPrefix?: string
}

const navItems: NavItem[] = [
  { label: '首页', icon: <Home size={20} />, path: '/' },
  { label: '电视剧', icon: <Tv size={20} />, path: '/filter?category=电视剧', matchPrefix: '/filter' },
  { label: '电影', icon: <Film size={20} />, path: '/filter?category=电影', matchPrefix: '/filter' },
  { label: '综艺', icon: <Clapperboard size={20} />, path: '/filter?category=综艺', matchPrefix: '/filter' },
  { label: '动漫', icon: <Sparkles size={20} />, path: '/filter?category=动漫', matchPrefix: '/filter' },
  { label: '搜索', icon: <Search size={20} />, path: '/search' },
  { label: '我的收藏', icon: <Heart size={20} />, path: '/library?tab=favorite', matchPrefix: '/library' },
  { label: '观看历史', icon: <Clock size={20} />, path: '/library?tab=history', matchPrefix: '/library' },
  { label: '设置', icon: <Settings size={20} />, path: '/settings' },
]

export function Layout() {
  const location = useLocation()
  const navigate = useNavigate()
  const [focusedIndex, setFocusedIndex] = useState(0)

  const isActive = (item: NavItem) => {
    if (item.path === '/') return location.pathname === '/'
    if (!item.matchPrefix) return location.pathname === item.path

    // Check pathname matches prefix
    if (!location.pathname.startsWith(item.matchPrefix)) return false

    // For routes with query params, check params match
    const searchParams = new URLSearchParams(location.search)
    const itemParams = new URLSearchParams(item.path.split('?')[1] || '')

    for (const [key, value] of itemParams) {
      if (searchParams.get(key) !== value) return false
    }
    return true
  }

  // TV remote navigation: up/down arrows navigate sidebar
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Only handle if focus is within sidebar or body
      const activeEl = document.activeElement
      const sidebarEl = document.getElementById('tv-sidebar')
      if (!sidebarEl) return

      const isInSidebar = sidebarEl.contains(activeEl)

      if (e.key === 'ArrowDown' && isInSidebar) {
        e.preventDefault()
        setFocusedIndex(prev => {
          const next = Math.min(navItems.length - 1, prev + 1)
          const btn = document.getElementById(`nav-btn-${next}`)
          btn?.focus()
          return next
        })
      } else if (e.key === 'ArrowUp' && isInSidebar) {
        e.preventDefault()
        setFocusedIndex(prev => {
          const next = Math.max(0, prev - 1)
          const btn = document.getElementById(`nav-btn-${next}`)
          btn?.focus()
          return next
        })
      } else if (e.key === 'ArrowRight' && isInSidebar) {
        e.preventDefault()
        // Move focus to home page first hero poster if available
        const firstPoster = document.getElementById('home-first-poster')
        if (firstPoster) {
          firstPoster.focus()
        } else {
          const mainContent = document.getElementById('tv-main-content')
          const firstFocusable = mainContent?.querySelector('[tabindex="0"]') as HTMLElement
          firstFocusable?.focus()
        }
      } else if (e.key === 'Enter' && isInSidebar) {
        e.preventDefault()
        navigate(navItems[focusedIndex].path)
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [focusedIndex, navigate])

  return (
    <div className="h-screen w-screen flex overflow-hidden bg-background text-foreground">
      {/* Left Sidebar */}
      <aside
        id="tv-sidebar"
        className="w-[173px] h-full bg-card border-r border-border flex flex-col flex-shrink-0 select-none"
      >
        {/* Logo */}
        <div className="p-6 pb-4">
          <Logo size="md" />
        </div>

        {/* Navigation */}
        <nav className="flex-1 px-4 space-y-1 overflow-y-auto thin-scrollbar py-2">
          {navItems.map((item, i) => {
            const active = isActive(item)
            return (
              <button
                key={item.label}
                id={`nav-btn-${i}`}
                className={cn(
                  'tv-focusable w-full flex items-center gap-3 px-4 py-3 rounded-xl text-left transition-all duration-300 outline-none',
                  active
                    ? 'bg-primary text-primary-foreground'
                    : 'text-secondary-foreground hover:bg-surface-hover'
                )}
                tabIndex={0}
                onClick={() => navigate(item.path)}
                onFocus={() => setFocusedIndex(i)}
              >
                <span className={cn(
                  'flex-shrink-0',
                  active ? 'text-primary-foreground' : 'text-muted-foreground'
                )}>
                  {item.icon}
                </span>
                <span className="text-sm font-medium">{item.label}</span>
                {active && (
                  <div className="ml-auto w-1.5 h-1.5 rounded-full bg-primary-foreground" />
                )}
              </button>
            )
          })}
        </nav>

        {/* Footer info */}
        <div className="p-4 border-t border-border">
          <p className="text-[10px] text-muted-foreground text-center">
            柠檬影视 TV v1.0
          </p>
        </div>
      </aside>

      {/* Main Content */}
      <main
        id="tv-main-content"
        className="flex-1 h-full overflow-y-auto no-scrollbar"
      >
        <Outlet />
      </main>
    </div>
  )
}
