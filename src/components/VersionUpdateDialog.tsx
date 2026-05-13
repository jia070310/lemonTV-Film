import { useEffect, useRef } from 'react'
import { cn } from '@/lib/utils'
import { useTvSpatialNode } from '@/tv/spatial'
import type { RemoteReleaseInfo } from '@/lib/githubRelease'

type Props = {
  remote: RemoteReleaseInfo
  currentVersionName: string
  onClose: () => void
  onDownload: () => void
}

/** 仅在 open 时挂载，避免未打开时注册 spatial id */
export function VersionUpdateDialog({
  remote,
  currentVersionName,
  onClose,
  onDownload,
}: Props) {
  const cancelSpatial = useTvSpatialNode(
    'version-dialog-cancel',
    () => ({
      right: 'version-dialog-download',
    }),
    []
  )
  const downloadSpatial = useTvSpatialNode(
    'version-dialog-download',
    () => ({
      left: 'version-dialog-cancel',
    }),
    []
  )

  const scrollRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const id = requestAnimationFrame(() => {
      document
        .querySelector<HTMLElement>('[data-spatial-id="version-dialog-cancel"]')
        ?.focus({ preventScroll: true })
    })
    return () => cancelAnimationFrame(id)
  }, [])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault()
        onClose()
      }
      const el = scrollRef.current
      if (!el || el.scrollHeight <= el.clientHeight) return
      if (e.key === 'ArrowDown') {
        e.preventDefault()
        el.scrollTop += 56
      } else if (e.key === 'ArrowUp') {
        e.preventDefault()
        el.scrollTop -= 56
      }
    }
    window.addEventListener('keydown', onKey, true)
    return () => window.removeEventListener('keydown', onKey, true)
  }, [onClose])

  const dateLabel = remote.releaseDate
    ? new Date(remote.releaseDate).toLocaleString()
    : '未知'

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/70 p-6 backdrop-blur-sm"
      role="presentation"
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="version-dialog-title"
        className={cn(
          'flex max-h-[85vh] w-full max-w-lg flex-col rounded-2xl border border-border bg-card',
          'shadow-[var(--shadow-elevated)]'
        )}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="border-b border-border px-6 py-4">
          <h2
            id="version-dialog-title"
            className="text-lg font-bold text-foreground"
          >
            发现新版本 v{remote.versionName}
          </h2>
          <p className="mt-1 text-xs text-muted-foreground">
            当前版本 v{currentVersionName} · 发布于 {dateLabel}
          </p>
        </div>

        <div
          ref={scrollRef}
          className="min-h-0 flex-1 overflow-y-auto thin-scrollbar px-6 py-4"
        >
          <p className="mb-2 text-sm font-medium text-foreground">更新内容</p>
          <pre className="whitespace-pre-wrap break-words font-sans text-sm leading-relaxed text-muted-foreground">
            {remote.releaseNotes}
          </pre>
          <p className="mt-4 text-xs text-muted-foreground/80">
            下载已启用 gh-proxy 加速。若 TV 未自动弹出下载，请用浏览器打开 Release 页手动获取 APK。
          </p>
        </div>

        <div className="flex justify-end gap-3 border-t border-border px-6 py-4">
          <button
            type="button"
            {...cancelSpatial}
            className="tv-focusable tv-dialog-action-btn pill-focus rounded-full px-6 py-2.5 text-sm font-medium"
            onClick={onClose}
          >
            稍后
          </button>
          <button
            type="button"
            {...downloadSpatial}
            className="tv-focusable tv-dialog-action-btn pill-focus rounded-full px-6 py-2.5 text-sm font-medium"
            onClick={() => {
              onDownload()
              onClose()
            }}
          >
            立即下载（加速）
          </button>
        </div>
      </div>
    </div>
  )
}
