import { useCallback, useEffect, useRef, useState } from 'react'
import { checkDeviceNetwork, checkMaccmsServer } from '@/lib/startupHealth'

type Phase = 'network' | 'server' | 'loading' | 'error'

const MIN_STEP_MS = 450

export function AppStartupSplash({ onComplete }: { onComplete: () => void }) {
  const [phase, setPhase] = useState<Phase>('network')
  const [statusLine, setStatusLine] = useState('正在检测网络…')
  const [detailLine, setDetailLine] = useState('')
  const cancelled = useRef(false)
  const runId = useRef(0)

  const runChecks = useCallback(async () => {
    const id = ++runId.current
    cancelled.current = false
    setPhase('network')
    setStatusLine('正在检测网络…')
    setDetailLine('')

    const gate = async () => {
      await new Promise((r) => setTimeout(r, MIN_STEP_MS))
    }

    await gate()
    if (cancelled.current || runId.current !== id) return

    const net = await checkDeviceNetwork()
    if (cancelled.current || runId.current !== id) return
    setDetailLine(net.message)
    if (!net.ok) {
      setPhase('error')
      setStatusLine('网络不可用')
      return
    }

    setPhase('server')
    setStatusLine('正在检测影视服务器…')
    setDetailLine('')
    await gate()
    if (cancelled.current || runId.current !== id) return

    const srv = await checkMaccmsServer()
    if (cancelled.current || runId.current !== id) return
    setDetailLine(srv.message)
    if (!srv.ok) {
      setPhase('error')
      setStatusLine('服务器连接失败')
      return
    }

    setPhase('loading')
    setStatusLine('加载中…')
    setDetailLine('')
    await new Promise((r) => setTimeout(r, 500))
    if (cancelled.current || runId.current !== id) return
    onComplete()
  }, [onComplete])

  useEffect(() => {
    void runChecks()
    return () => {
      cancelled.current = true
    }
  }, [runChecks])

  const retry = () => {
    void runChecks()
  }

  return (
    <div className="fixed inset-0 z-[200] flex flex-col items-center bg-[#FACC15] px-10 pt-[16vh] sm:pt-[18vh]">
      <img
        src="/lom.png"
        alt=""
        draggable={false}
        className="h-40 w-40 shrink-0 object-contain sm:h-48 sm:w-48"
      />

      <div className="mt-12 flex w-full max-w-md flex-col items-center gap-2 text-center font-['SimSun','Songti_SC','STSong','Noto_Serif_SC',serif]">
        <p className="text-xs font-normal leading-snug text-neutral-900">{statusLine}</p>
        <p className="min-h-[2.25rem] text-[11px] font-normal leading-relaxed text-neutral-800/90">
          {detailLine || '\u00a0'}
        </p>
      </div>

      {phase === 'error' && (
        <button
          type="button"
          className="tv-focusable pill-focus mt-8 rounded-full bg-neutral-900 px-8 py-3 text-sm font-medium text-[#FACC15]"
          onClick={retry}
        >
          重试
        </button>
      )}
    </div>
  )
}
