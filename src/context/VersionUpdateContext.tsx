import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import { APP_VERSION_NAME } from '@/config/version'
import {
  fetchLatestRelease,
  isNewerThanCurrent,
  type RemoteReleaseInfo,
} from '@/lib/githubRelease'
import { VERSION_UPDATE_DISMISS_STORAGE_KEY } from '@/config/storageKeys'

type VersionUpdateContextValue = {
  currentVersionName: string
  hasUpdate: boolean
  remote: RemoteReleaseInfo | null
  isChecking: boolean
  checkError: string | null
  refreshCheck: () => Promise<void>
  /** 首页黄色提示条是否展示 */
  showHomeBanner: boolean
  dismissHomeBanner: () => void
  dialogOpen: boolean
  openUpdateDialog: () => void
  closeUpdateDialog: () => void
  /** 使用加速器链接拉起下载（WebView / 浏览器） */
  startDownload: () => void
}

const VersionUpdateContext = createContext<VersionUpdateContextValue | null>(
  null
)

export function VersionUpdateProvider({
  children,
}: {
  children: React.ReactNode
}) {
  const [remote, setRemote] = useState<RemoteReleaseInfo | null>(null)
  const [isChecking, setIsChecking] = useState(false)
  const [checkError, setCheckError] = useState<string | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [bannerDismissedForVersion, setBannerDismissedForVersion] = useState<
    string | null
  >(() => {
    try {
      return localStorage.getItem(VERSION_UPDATE_DISMISS_STORAGE_KEY)
    } catch {
      return null
    }
  })

  const mounted = useRef(true)
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  const refreshCheck = useCallback(async () => {
    setIsChecking(true)
    setCheckError(null)
    try {
      const info = await fetchLatestRelease()
      if (!mounted.current) return
      setRemote(info)
      if (!info) {
        setCheckError(null)
      }
    } catch (e) {
      if (!mounted.current) return
      setRemote(null)
      setCheckError(e instanceof Error ? e.message : '检查更新失败')
    } finally {
      if (mounted.current) setIsChecking(false)
    }
  }, [])

  useEffect(() => {
    void refreshCheck()
  }, [refreshCheck])

  const hasUpdate = Boolean(remote && isNewerThanCurrent(remote))

  const showHomeBanner =
    hasUpdate &&
    remote != null &&
    bannerDismissedForVersion !== remote.versionName

  const dismissHomeBanner = useCallback(() => {
    if (!remote) return
    try {
      localStorage.setItem(VERSION_UPDATE_DISMISS_STORAGE_KEY, remote.versionName)
    } catch {
      /* ignore */
    }
    setBannerDismissedForVersion(remote.versionName)
  }, [remote])

  useEffect(() => {
    if (!showHomeBanner || !remote) return
    const t = window.setTimeout(() => dismissHomeBanner(), 10_000)
    return () => window.clearTimeout(t)
  }, [showHomeBanner, remote, dismissHomeBanner])

  const openUpdateDialog = useCallback(() => setDialogOpen(true), [])
  const closeUpdateDialog = useCallback(() => setDialogOpen(false), [])

  const startDownload = useCallback(() => {
    if (!remote) return
    const url = remote.downloadUrl
    window.open(url, '_blank', 'noopener,noreferrer')
  }, [remote])

  const value = useMemo(
    () => ({
      currentVersionName: APP_VERSION_NAME,
      hasUpdate,
      remote,
      isChecking,
      checkError,
      refreshCheck,
      showHomeBanner,
      dismissHomeBanner,
      dialogOpen,
      openUpdateDialog,
      closeUpdateDialog,
      startDownload,
    }),
    [
      hasUpdate,
      remote,
      isChecking,
      checkError,
      refreshCheck,
      showHomeBanner,
      dismissHomeBanner,
      dialogOpen,
      startDownload,
    ]
  )

  return (
    <VersionUpdateContext.Provider value={value}>
      {children}
    </VersionUpdateContext.Provider>
  )
}

export function useVersionUpdate(): VersionUpdateContextValue {
  const v = useContext(VersionUpdateContext)
  if (!v) {
    throw new Error(
      'useVersionUpdate must be used within VersionUpdateProvider'
    )
  }
  return v
}

export function useVersionUpdateOptional(): VersionUpdateContextValue | null {
  return useContext(VersionUpdateContext)
}
