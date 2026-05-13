/** 可用 HTML5 video 直接打开的地址（采集站多为 http(s) 直链或 m3u8） */
export function isWebPlayableUrl(url: string | undefined): boolean {
  if (!url || typeof url !== 'string') return false
  const t = url.trim().toLowerCase()
  return t.startsWith('http://') || t.startsWith('https://')
}
