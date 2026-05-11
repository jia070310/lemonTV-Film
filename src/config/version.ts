/** 与 android/app/build.gradle 中 versionName 保持一致，用于与 Release tag 比对 */
export const APP_VERSION_NAME = '1.0'

/** 开源仓库主页（展示在「关于」中） */
export const APP_REPO_PAGE_URL = 'https://github.com/jia070310/lemonTV-Film'

/** GitHub Releases API：latest */
export const GITHUB_RELEASES_LATEST_API =
  'https://api.github.com/repos/jia070310/lemonTV-Film/releases/latest'

/**
 * 与 LomenTV VersionCheckService 一致：x.y.z → 可比较大整数
 * 例：1.0 → 100，1.0.1 → 10001
 */
export function parseVersionCode(versionName: string): number {
  const parts = versionName.replace(/^v/i, '').split('.').filter(Boolean)
  let acc = 0
  for (const p of parts) {
    const n = parseInt(p, 10)
    acc = acc * 100 + (Number.isFinite(n) ? n : 0)
  }
  return acc
}

export const CURRENT_VERSION_CODE = parseVersionCode(APP_VERSION_NAME)
