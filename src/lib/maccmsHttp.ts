import { Capacitor, CapacitorHttp } from '@capacitor/core'

/**
 * MACCMS 请求：Android/iOS 走原生 Http，不受 WebView 混合内容 / CORS 限制；
 * 浏览器 / `npm run dev` 仍用 fetch（配合 Vite /maccms 代理）。
 */
export async function maccmsRequestJson<T>(url: string): Promise<T> {
  if (Capacitor.isNativePlatform()) {
    const res = await CapacitorHttp.get({
      url,
      responseType: 'json',
      connectTimeout: 20000,
      readTimeout: 60000,
    })
    if (res.status < 200 || res.status >= 300) {
      throw new Error(`MACCMS HTTP ${res.status}`)
    }
    const raw = res.data
    if (raw != null && typeof raw === 'object') {
      return raw as T
    }
    if (typeof raw === 'string') {
      return JSON.parse(raw) as T
    }
    throw new Error('MACCMS 返回体为空')
  }

  const r = await fetch(url)
  if (!r.ok) {
    throw new Error(`MACCMS HTTP ${r.status}`)
  }
  return (await r.json()) as T
}
