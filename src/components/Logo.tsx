import { cn } from '@/lib/utils'

interface LogoProps {
  size?: 'sm' | 'md' | 'lg'
  className?: string
}

const IMG = '/lom.png'

export function Logo({ size = 'md', className }: LogoProps) {
  const sizes = {
    sm: { box: 'h-5 w-5 min-h-5 min-w-5', text: 'text-base' },
    md: { box: 'h-6 w-6 min-h-6 min-w-6', text: 'text-xl' },
    lg: { box: 'h-8 w-8 min-h-8 min-w-8', text: 'text-2xl' },
  }

  const s = sizes[size]

  return (
    <div className={cn('flex items-center gap-2', className)}>
      <img
        src={IMG}
        alt=""
        draggable={false}
        className={cn(s.box, 'shrink-0 object-contain')}
      />
      <span className={cn(s.text, 'font-bold tracking-wider text-foreground')}>
        柠檬影视
      </span>
    </div>
  )
}
