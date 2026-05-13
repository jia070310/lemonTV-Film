import { VERSION_UPDATE_DISMISS_STORAGE_KEY } from '@/config/storageKeys'
import {
  clearAllHomePersistCache,
  estimateHomePersistByteSize,
} from '@/lib/homePersistCache'
import { clearHomeSessionMemory } from '@/lib/homeSessionCache'

function estimateDismissBannerBytes(): number {
  if (typeof localStorage === 'undefined') return 0
  const k = VERSION_UPDATE_DISMISS_STORAGE_KEY
  const v = localStorage.getItem(k)
  if (v === null) return 0
  return (k.length + v.length) * 2
}

/** 可被「清除缓存」安全清理的本地存储估算大小（不含收藏/历史/播放进度等） */
export function getLocalTransientCacheEstimateBytes(): number {
  return estimateHomePersistByteSize() + estimateDismissBannerBytes()
}

export function formatApproxByteSize(bytes: number): string {
  if (bytes <= 0) return '0 B'
  if (bytes < 1024) return `${Math.round(bytes)} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

export type ClearTransientAppCacheResult = {
  /** 清理前按 localStorage 估算的可释放字节数 */
  approximateBytesFreed: number
  persistKeysRemoved: number
  cacheApiStoresDeleted: number
  errors: readonly string[]
}

/**
 * 清除惰性数据：首页会话+持久化、版本更新横幅忽略记录、Cache Storage（若环境支持）。
 * 不触碰收藏、观看历史、播放进度、播放设置。
 */
export async function clearTransientAppCache(): Promise<ClearTransientAppCacheResult> {
  const errors: string[] = []
  const approximateBytesFreed = getLocalTransientCacheEstimateBytes()

  clearHomeSessionMemory()
  const persistKeysRemoved = clearAllHomePersistCache()

  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem(VERSION_UPDATE_DISMISS_STORAGE_KEY)
    }
  } catch (e) {
    errors.push(
      e instanceof Error ? e.message : '无法清除版本更新横幅本地记录'
    )
  }

  let cacheApiStoresDeleted = 0
  if (typeof caches !== 'undefined' && typeof caches.keys === 'function') {
    try {
      const names = await caches.keys()
      for (const name of names) {
        if (await caches.delete(name)) cacheApiStoresDeleted++
      }
    } catch (e) {
      errors.push(e instanceof Error ? e.message : '网络缓存（Cache Storage）清理失败')
    }
  }

  return {
    approximateBytesFreed,
    persistKeysRemoved,
    cacheApiStoresDeleted,
    errors,
  }
}
