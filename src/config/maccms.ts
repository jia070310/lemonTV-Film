/**
 * MACCMS10 采集/开放 API（provide）与测试环境地址。
 * 生产构建可在 .env 中覆盖：VITE_MACCMS_BASE_URL=https://your-domain/
 */
export const MACCMS_BASE_URL =
  (import.meta.env.VITE_MACCMS_BASE_URL as string | undefined)?.replace(/\/$/, '') ||
  'http://192.168.3.20:7963'

/** 首页海报位：与后台「推荐」级别一致，常见为幻灯/首页推荐设为 9 */
export const MACCMS_HERO_RECOMMEND_LEVEL = 9

/** 首页顶部轮播海报条数（与后台推荐位数量一致） */
export const MACCMS_HOME_HERO_COUNT = 5

export const MACCMS_HOME_GRID_LIMIT = 24

/**
 * 首页某主类（如综艺）长期无数据：多为 CMS 子分类 `type_id` 与内置表不一致。
 * 在 `.env` 用逗号分隔覆盖，例如 `VITE_MACCMS_HOME_IDS_VARIETY=32,33`（与后台「视频 → 分类」ID 一致）。
 * 详见 `src/data/maccmsTaxonomy.ts` 的 `getHomeGridTypeIds`。
 */

/** 筛选页单次拉取再本地过滤时的池大小上限（provide 单页最大 100） */
export const MACCMS_FILTER_FETCH_PAGE_SIZE = 100

/** 开发环境通过 Vite 代理避免浏览器跨域；正式包走完整 Base URL */
export function maccmsOrigin(): string {
  if (import.meta.env.DEV) return ''
  return MACCMS_BASE_URL
}

export function maccmsApiUrl(path: string): string {
  const p = path.startsWith('/') ? path : `/${path}`
  if (import.meta.env.DEV) return `/maccms${p}`
  return `${MACCMS_BASE_URL}${p}`
}

const BASE = () => MACCMS_BASE_URL.replace(/\/$/, '')

/**
 * 将 CMS 返回的相对路径、mac: 占位图转为可在 WebView/img 中加载的绝对 URL。
 */
export function absoluteMaccmsAssetUrl(href: string | undefined | null): string {
  const s = (href || '').trim()
  if (!s) return ''
  if (/^https?:\/\//i.test(s) || s.startsWith('//')) return s
  if (/^mac:/i.test(s)) {
    return s.replace(/^mac:/i, `${BASE()}/`)
  }
  return s.startsWith('/') ? `${BASE()}${s}` : `${BASE()}/${s}`
}
