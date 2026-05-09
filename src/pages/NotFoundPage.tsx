import { useNavigate } from 'react-router-dom'
import { AlertTriangle, Home } from 'lucide-react'

export function NotFoundPage() {
  const navigate = useNavigate()

  return (
    <div className="h-full flex flex-col items-center justify-center bg-background text-foreground p-8">
      <AlertTriangle size={64} className="text-primary mb-6" />
      <h2 className="text-3xl font-bold mb-2">页面不存在</h2>
      <p className="text-muted-foreground mb-8">您访问的页面不存在或已被移除</p>
      <button
        className="tv-focusable pill-focus flex items-center gap-2 px-8 py-3 rounded-xl bg-primary text-primary-foreground text-base font-medium"
        tabIndex={0}
        onClick={() => navigate('/')}
        onKeyDown={(e) => { if (e.key === 'Enter') navigate('/') }}
      >
        <Home size={20} />
        返回首页
      </button>
    </div>
  )
}
