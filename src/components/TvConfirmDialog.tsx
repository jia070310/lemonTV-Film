import { useEffect, type ReactNode } from 'react'
import { cn } from '@/lib/utils'
import { useTvSpatialNode } from '@/tv/spatial'

type Props = {
  /** 用于 `data-spatial-id`，须全局唯一 */
  spatialIdPrefix: string
  title: string
  children: ReactNode
  cancelLabel?: string
  confirmLabel?: string
  onCancel: () => void
  onConfirm: () => void
}

/**
 * 与版本更新弹窗一致的卡片样式；中文按钮，避免 WebView 原生 confirm 显示英文。
 */
export function TvConfirmDialog({
  spatialIdPrefix,
  title,
  children,
  cancelLabel = '取消',
  confirmLabel = '确定',
  onCancel,
  onConfirm,
}: Props) {
  const cancelId = `${spatialIdPrefix}-cancel`
  const okId = `${spatialIdPrefix}-ok`

  const cancelSpatial = useTvSpatialNode(
    cancelId,
    () => ({ right: okId }),
    [okId]
  )
  const okSpatial = useTvSpatialNode(
    okId,
    () => ({ left: cancelId }),
    [cancelId]
  )

  useEffect(() => {
    const id = requestAnimationFrame(() => {
      document
        .querySelector<HTMLElement>(`[data-spatial-id="${cancelId}"]`)
        ?.focus({ preventScroll: true })
    })
    return () => cancelAnimationFrame(id)
  }, [cancelId])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault()
        onCancel()
      }
    }
    window.addEventListener('keydown', onKey, true)
    return () => window.removeEventListener('keydown', onKey, true)
  }, [onCancel])

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/70 p-6 backdrop-blur-sm"
      role="presentation"
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel()
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={`${spatialIdPrefix}-title`}
        className={cn(
          'flex max-h-[85vh] w-full max-w-lg flex-col rounded-2xl border border-border bg-card',
          'shadow-[var(--shadow-elevated)]'
        )}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="border-b border-border px-6 py-4">
          <h2
            id={`${spatialIdPrefix}-title`}
            className="text-lg font-bold text-foreground"
          >
            {title}
          </h2>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto thin-scrollbar px-6 py-4">
          {children}
        </div>

        <div className="flex justify-end gap-3 border-t border-border px-6 py-4">
          <button
            type="button"
            {...cancelSpatial}
            className="tv-focusable tv-dialog-action-btn pill-focus rounded-full px-6 py-2.5 text-sm font-medium"
            onClick={onCancel}
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            {...okSpatial}
            className="tv-focusable tv-dialog-action-btn pill-focus rounded-full px-6 py-2.5 text-sm font-medium"
            onClick={onConfirm}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
