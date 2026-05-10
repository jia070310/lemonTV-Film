import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { useTvSpatialContext, useTvSpatialNode } from '@/tv/spatial'
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

function SidebarNavButton({
  index,
  item,
  active,
  navigate,
}: {
  index: number
  item: NavItem
  active: boolean
  navigate: (path: string) => void
}) {
  const { getMainSpatialEntry } = useTvSpatialContext()
  const spatial = useTvSpatialNode(
    `nav-${index}`,
    () => ({
      up: index > 0 ? `nav-${index - 1}` : undefined,
      down: index < navItems.length - 1 ? `nav-${index + 1}` : undefined,
      right: getMainSpatialEntry() ?? undefined,
    }),
    [index, getMainSpatialEntry]
  )

  return (
    <button
      id={`nav-btn-${index}`}
      type="button"
      {...spatial}
      className={cn(
        'tv-focusable w-full flex items-center gap-3 px-4 py-3 rounded-xl text-left outline-none transition-[box-shadow,transform,colors] duration-150 ease-out',
        active
          ? 'bg-primary text-primary-foreground'
          : 'text-secondary-foreground hover:bg-surface-hover'
      )}
      onClick={() => navigate(item.path)}
    >
      <span
        className={cn(
          'flex-shrink-0',
          active ? 'text-primary-foreground' : 'text-muted-foreground'
        )}
      >
        {item.icon}
      </span>
      <span className="text-sm font-medium">{item.label}</span>
      {active && (
        <div className="ml-auto w-1.5 h-1.5 rounded-full bg-primary-foreground" />
      )}
    </button>
  )
}

export function Layout() {
  const location = useLocation()
  const navigate = useNavigate()

  const isActive = (item: NavItem) => {
    if (item.path === '/') return location.pathname === '/'
    if (!item.matchPrefix) return location.pathname === item.path

    if (!location.pathname.startsWith(item.matchPrefix)) return false

    const searchParams = new URLSearchParams(location.search)
    const itemParams = new URLSearchParams(item.path.split('?')[1] || '')

    for (const [key, value] of itemParams) {
      if (searchParams.get(key) !== value) return false
    }
    return true
  }

  return (
    <div className="h-screen w-screen flex overflow-hidden bg-background text-foreground">
      <aside
        id="tv-sidebar"
        className="w-[173px] h-full bg-card border-r border-border flex flex-col flex-shrink-0 select-none"
      >
        <div className="p-6 pb-4">
          <Logo size="md" />
        </div>

        <nav className="flex-1 px-4 space-y-1 overflow-y-auto thin-scrollbar py-2">
          {navItems.map((item, i) => (
            <SidebarNavButton
              key={item.label}
              index={i}
              item={item}
              active={isActive(item)}
              navigate={navigate}
            />
          ))}
        </nav>

        <div className="p-4 border-t border-border">
          <p className="text-[10px] text-muted-foreground text-center">
            柠檬影视 TV v1.0
          </p>
        </div>
      </aside>

      <main
        id="tv-main-content"
        className="flex-1 h-full overflow-y-auto no-scrollbar"
      >
        <Outlet />
      </main>
    </div>
  )
}
