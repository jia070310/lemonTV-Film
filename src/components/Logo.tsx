import { cn } from '@/lib/utils'
import { Film } from 'lucide-react'

interface LogoProps {
  size?: 'sm' | 'md' | 'lg'
  className?: string
}

export function Logo({ size = 'md', className }: LogoProps) {
  const sizes = {
    sm: { icon: 'w-6 h-6', text: 'text-base', iconSize: 14 },
    md: { icon: 'w-8 h-8', text: 'text-xl', iconSize: 18 },
    lg: { icon: 'w-10 h-10', text: 'text-2xl', iconSize: 22 },
  }

  const s = sizes[size]

  return (
    <div className={cn('flex items-center gap-2', className)}>
      <div className={cn(s.icon, 'bg-primary rounded-lg flex items-center justify-center')}>
        <Film size={s.iconSize} className="text-primary-foreground" />
      </div>
      <span className={cn(s.text, 'font-bold tracking-wider text-foreground')}>
        柠檬影视
      </span>
    </div>
  )
}
