import { Capacitor } from '@capacitor/core'
import { maccmsApiUrl } from '@/config/maccms'
import { maccmsRequestJson } from '@/lib/maccmsHttp'

export type StepResult = { ok: boolean; message: string }

function sleep(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms))
}

/** 设备是否具备网络链路（原生优先读 Network 插件） */
export async function checkDeviceNetwork(): Promise<StepResult> {
  if (Capacitor.isNativePlatform()) {
    try {
      const { Network } = await import('@capacitor/network')
      const s = await Network.getStatus()
      if (s.connected) return { ok: true, message: '网络已连接' }
      return { ok: false, message: '当前设备未连接网络' }
    } catch {
      /* 插件不可用时退回 */
    }
  }
  if (typeof navigator !== 'undefined' && navigator.onLine) {
    return { ok: true, message: '网络可用' }
  }
  return { ok: false, message: '浏览器报告处于离线状态' }
}

type MaccmsListProbe = { code?: number | string }

/** 探测 CMS 接口是否可达（与首页同源配置） */
export async function checkMaccmsServer(timeoutMs = 15000): Promise<StepResult> {
  const url = maccmsApiUrl('/api.php/provide/vod/?ac=list&pg=1')
  try {
    const data = await Promise.race([
      maccmsRequestJson<MaccmsListProbe>(url),
      sleep(timeoutMs).then(() => {
        throw new Error('timeout')
      }),
    ])
    const c = data?.code
    if (c === 1 || c === '1') {
      return { ok: true, message: '影视服务器可访问' }
    }
    return { ok: false, message: '服务器返回异常，请检查接口地址' }
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    if (msg === 'timeout') {
      return { ok: false, message: '连接服务器超时' }
    }
    return { ok: false, message: `无法连接服务器（${msg}）` }
  }
}
