import {
  CURRENT_VERSION_CODE,
  GITHUB_RELEASES_LATEST_API,
  parseVersionCode,
} from '@/config/version'

/** 国内访问 GitHub 下载常用前缀（与 LomenTV VersionCheckService 一致） */
export function applyGhProxy(originalUrl: string): string {
  return `https://gh-proxy.org/${originalUrl}`
}

export type RemoteReleaseInfo = {
  versionName: string
  versionCode: number
  /** 经 gh-proxy 包裹后的直链 */
  downloadUrl: string
  originalDownloadUrl: string
  releaseNotes: string
  releaseDate: string
  htmlUrl: string
}

type GitHubReleaseJson = {
  tag_name: string
  body?: string
  published_at?: string
  html_url?: string
  assets?: Array<{ browser_download_url: string; name: string }>
}

export async function fetchLatestRelease(): Promise<RemoteReleaseInfo | null> {
  const res = await fetch(GITHUB_RELEASES_LATEST_API, {
    headers: {
      Accept: 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28',
    },
  })
  if (!res.ok) return null

  const json = (await res.json()) as GitHubReleaseJson
  const tagName = json.tag_name?.replace(/^v/i, '') ?? ''
  if (!tagName) return null

  const assets = json.assets ?? []
  const apk =
    assets.find(a => /\.apk$/i.test(a.name)) ?? assets[0]
  if (!apk?.browser_download_url) return null

  const originalDownloadUrl = apk.browser_download_url
  const versionCode = parseVersionCode(tagName)

  return {
    versionName: tagName,
    versionCode,
    originalDownloadUrl,
    downloadUrl: applyGhProxy(originalDownloadUrl),
    releaseNotes: (json.body ?? '（无更新说明）').trim(),
    releaseDate: json.published_at ?? '',
    htmlUrl: json.html_url ?? '',
  }
}

export function isNewerThanCurrent(remote: RemoteReleaseInfo): boolean {
  return remote.versionCode > CURRENT_VERSION_CODE
}
