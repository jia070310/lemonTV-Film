import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { App } from '@capacitor/app'
import { Capacitor } from '@capacitor/core'

const DOUBLE_MS = 2000

let triggerBack: (() => void) | null = null

/** 供播放器页等在遥控器键盘中统一走同一套返回策略（非全局 keydown 时使用） */
export function triggerAppBackNavigation() {
  triggerBack?.()
}

export function AppBackHandler() {
  const location = useLocation()
  const navigate = useNavigate()
  const [toast, setToast] = useState<string | null>(null)
  const pendingRef = useRef<{ key: string; at: number } | null>(null)
  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const pathnameRef = useRef(location.pathname)
  pathnameRef.current = location.pathname

  const showToast = useCallback((msg: string) => {
    if (toastTimerRef.current) clearTimeout(toastTimerRef.current)
    setToast(msg)
    toastTimerRef.current = setTimeout(() => {
      setToast(null)
      toastTimerRef.current = null
    }, DOUBLE_MS)
  }, [])

  const runBack = useCallback(() => {
    const normalized = location.pathname === '' ? '/' : location.pathname

    if (normalized === '/') {
      const now = Date.now()
      const p = pendingRef.current
      if (p?.key === 'home' && now - p.at < DOUBLE_MS) {
        pendingRef.current = null
        void App.exitApp()
        return
      }
      pendingRef.current = { key: 'home', at: now }
      showToast('再按一次退出应用')
      return
    }

    if (normalized.startsWith('/player/')) {
      const rest = normalized.slice('/player/'.length)
      const id = rest.split('/')[0] ?? ''
      if (!id) {
        pendingRef.current = null
        navigate('/', { replace: true })
        return
      }
      const now = Date.now()
      const key = `player:${id}`
      const p = pendingRef.current
      if (p?.key === key && now - p.at < DOUBLE_MS) {
        pendingRef.current = null
        navigate(`/detail/${id}`, { replace: true })
        return
      }
      pendingRef.current = { key, at: now }
      showToast('再按一次返回详情页')
      return
    }

    pendingRef.current = null
    navigate('/', { replace: true })
  }, [location.pathname, navigate, showToast])

  useEffect(() => {
    triggerBack = runBack
    return () => {
      triggerBack = null
    }
  }, [runBack])

  useEffect(() => {
    pendingRef.current = null
  }, [location.pathname])

  useEffect(() => {
    if (Capacitor.isNativePlatform()) {
      let cancelled = false
      let listener: { remove: () => Promise<void> } | undefined
      void App.addListener('backButton', () => {
        runBack()
      }).then((handle) => {
        if (cancelled) void handle.remove()
        else listener = handle
      })
      return () => {
        cancelled = true
        void listener?.remove()
      }
    }

    const onKeyDown = (e: KeyboardEvent) => {
      if (pathnameRef.current.startsWith('/player/')) return
      if (e.key !== 'Escape' && e.key !== 'Backspace') return
      const el = e.target as HTMLElement | null
      if (el?.closest('input, textarea, select, [contenteditable="true"]')) return
      e.preventDefault()
      runBack()
    }
    window.addEventListener('keydown', onKeyDown, true)
    return () => window.removeEventListener('keydown', onKeyDown, true)
  }, [runBack])

  if (!toast) return null

  return (
    <div
      role="status"
      className="pointer-events-none fixed bottom-24 left-1/2 z-[10000] max-w-[min(90vw,24rem)] -translate-x-1/2 rounded-lg border border-black/10 bg-white/80 px-4 py-3 text-center text-sm text-neutral-900 shadow-lg backdrop-blur-sm"
    >
      {toast}
    </div>
  )
}
