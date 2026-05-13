import { useEffect, useState } from 'react'
import { cn } from '@/lib/utils'
import {
  getPlaybackSettings,
  savePlaybackSettings,
} from '@/lib/playbackSettingsStorage'
import { APP_REPO_PAGE_URL, APP_VERSION_NAME } from '@/config/version'
import { useVersionUpdate } from '@/context/VersionUpdateContext'
import {
  clearTransientAppCache,
  formatApproxByteSize,
  getLocalTransientCacheEstimateBytes,
} from '@/lib/appCacheManagement'
import { TvConfirmDialog } from '@/components/TvConfirmDialog'
import { useTvSpatialMainEntry, useTvSpatialNode } from '@/tv/spatial'
import {
  Play,
  Monitor,
  HardDrive,
  Info,
  Check,
  RefreshCw,
  Download,
} from 'lucide-react'

interface SwitchProps {
  checked: boolean
  onChange: (v: boolean) => void
  spatialProps?: { 'data-spatial-id': string; tabIndex: 0 }
}

function TvSwitch({ checked, onChange, spatialProps }: SwitchProps) {
  const toggle = () => onChange(!checked)
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      {...spatialProps}
      className={cn(
        'tv-focusable relative w-12 h-7 rounded-full transition-all duration-300 outline-none',
        checked ? 'bg-primary' : 'bg-muted'
      )}
      onClick={toggle}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          toggle()
        }
      }}
    >
      <div
        className={cn(
          'absolute top-0.5 w-6 h-6 rounded-full bg-foreground shadow-md transition-all duration-300',
          checked ? 'left-[calc(100%-26px)]' : 'left-0.5'
        )}
      />
    </button>
  )
}

interface SelectOptionProps {
  options: string[]
  value: string
  onChange: (v: string) => void
}

function TvSelect({ options, value, onChange }: SelectOptionProps) {
  return (
    <div className="flex gap-2 flex-wrap">
      {options.map(opt => (
        <button
          key={opt}
          className={cn(
            'tv-focusable pill-focus px-4 py-1.5 rounded-full text-sm transition-all',
            value === opt
              ? 'tv-tab-selected'
              : 'bg-secondary text-secondary-foreground hover:bg-surface-hover'
          )}
          tabIndex={0}
          onClick={() => onChange(opt)}
        >
          {opt}
        </button>
      ))}
    </div>
  )
}

interface SettingItemProps {
  icon: React.ReactNode
  title: string
  description?: string
  children: React.ReactNode
}

function SettingItem({ icon, title, description, children }: SettingItemProps) {
  return (
    <div className="flex items-start justify-between gap-6 py-5 border-b border-border last:border-none">
      <div className="flex items-start gap-4">
        <div className="w-10 h-10 rounded-xl bg-secondary flex items-center justify-center flex-shrink-0 text-muted-foreground">
          {icon}
        </div>
        <div>
          <h4 className="text-base font-medium text-foreground">{title}</h4>
          {description && (
            <p className="text-sm text-muted-foreground mt-0.5">{description}</p>
          )}
        </div>
      </div>
      <div className="flex-shrink-0 mt-1">{children}</div>
    </div>
  )
}

export function SettingsPage() {
  useTvSpatialMainEntry('settings-auto-skip')
  const {
    hasUpdate,
    remote,
    isChecking,
    checkError,
    refreshCheck,
    openUpdateDialog,
  } = useVersionUpdate()

  const spatialSkip = useTvSpatialNode(
    'settings-auto-skip',
    () => ({ down: 'settings-auto-next' }),
    []
  )
  const spatialNext = useTvSpatialNode(
    'settings-auto-next',
    () => ({
      up: 'settings-auto-skip',
      down: 'settings-clear-cache',
    }),
    []
  )
  const spatialClearCache = useTvSpatialNode(
    'settings-clear-cache',
    () => ({
      up: 'settings-auto-next',
      down: 'settings-version-update',
    }),
    []
  )
  const spatialVersionUpdate = useTvSpatialNode(
    'settings-version-update',
    () => ({
      up: 'settings-clear-cache',
    }),
    []
  )

  const [autoSkip, setAutoSkip] = useState(() => getPlaybackSettings().autoSkipIntroOutro)
  const [autoPlayNext, setAutoPlayNext] = useState(() => getPlaybackSettings().autoPlayNext)
  const [defaultSpeed, setDefaultSpeed] = useState(() => getPlaybackSettings().defaultSpeed)
  const [defaultQuality, setDefaultQuality] = useState('高清')
  const [showCleared, setShowCleared] = useState(false)
  const [cacheEstimateBytes, setCacheEstimateBytes] = useState(0)
  const [isClearingCache, setIsClearingCache] = useState(false)
  const [lastClearMessage, setLastClearMessage] = useState<string | null>(null)
  const [clearCacheConfirmOpen, setClearCacheConfirmOpen] = useState(false)

  const refreshCacheEstimate = () => {
    setCacheEstimateBytes(getLocalTransientCacheEstimateBytes())
  }

  useEffect(() => {
    const s = getPlaybackSettings()
    setAutoSkip(s.autoSkipIntroOutro)
    setAutoPlayNext(s.autoPlayNext)
    setDefaultSpeed(s.defaultSpeed)
    refreshCacheEstimate()
  }, [])

  const runClearCache = async () => {
    setIsClearingCache(true)
    setLastClearMessage(null)
    try {
      const result = await clearTransientAppCache()
      refreshCacheEstimate()
      const parts: string[] = []
      if (result.persistKeysRemoved > 0) {
        parts.push(`已移除 ${result.persistKeysRemoved} 条本地存储`)
      }
      if (result.cacheApiStoresDeleted > 0) {
        parts.push(`已清理 ${result.cacheApiStoresDeleted} 个网络缓存仓库`)
      }
      if (result.approximateBytesFreed > 0) {
        parts.push(`约释放 ${formatApproxByteSize(result.approximateBytesFreed)}`)
      }
      if (parts.length === 0) {
        parts.push('当前无可清理的本地缓存')
      }
      if (result.errors.length > 0) {
        parts.push(`部分步骤异常：${result.errors.join('；')}`)
      }
      setLastClearMessage(parts.join(' · '))
      setShowCleared(true)
      setTimeout(() => setShowCleared(false), 2800)
    } catch (e) {
      setLastClearMessage(
        e instanceof Error ? `清理失败：${e.message}` : '清理失败'
      )
      setShowCleared(true)
      setTimeout(() => setShowCleared(false), 2800)
    } finally {
      setIsClearingCache(false)
    }
  }

  return (
    <>
    <div className="h-full flex flex-col bg-background overflow-hidden p-8">
      <h2 className="text-3xl font-bold text-foreground mb-8">设置</h2>

      <div className="flex-1 overflow-y-auto thin-scrollbar">
        {/* Playback Settings */}
        <div className="bg-card rounded-2xl p-6 mb-6">
          <div className="flex items-center gap-3 mb-4">
            <Play size={20} className="text-primary" />
            <h3 className="text-lg font-bold text-foreground">播放设置</h3>
          </div>
          <SettingItem
            icon={<Play size={18} />}
            title="自动跳过片头片尾"
            description="关闭时播放器不会自动跳过；开启后按播放器内片头/片尾时长与分项开关生效"
          >
            <TvSwitch
              checked={autoSkip}
              onChange={(v) => {
                setAutoSkip(v)
                savePlaybackSettings({ autoSkipIntroOutro: v })
              }}
              spatialProps={spatialSkip}
            />
          </SettingItem>
          <SettingItem
            icon={<Monitor size={18} />}
            title="自动播放下一集"
            description="当前集播放结束后自动继续"
          >
            <TvSwitch
              checked={autoPlayNext}
              onChange={(v) => {
                setAutoPlayNext(v)
                savePlaybackSettings({ autoPlayNext: v })
              }}
              spatialProps={spatialNext}
            />
          </SettingItem>
          <SettingItem
            icon={<Monitor size={18} />}
            title="默认倍速"
            description="新播放的默认播放速度"
          >
            <TvSelect
              options={['0.5x', '1.0x', '1.25x', '1.5x', '2.0x']}
              value={defaultSpeed}
              onChange={(v) => {
                setDefaultSpeed(v)
                savePlaybackSettings({ defaultSpeed: v })
              }}
            />
          </SettingItem>
        </div>

        {/* Quality Settings */}
        <div className="bg-card rounded-2xl p-6 mb-6">
          <div className="flex items-center gap-3 mb-4">
            <Monitor size={20} className="text-primary" />
            <h3 className="text-lg font-bold text-foreground">画质设置</h3>
          </div>
          <SettingItem
            icon={<Monitor size={18} />}
            title="默认清晰度"
            description="优先使用的视频清晰度"
          >
            <TvSelect
              options={['标清', '高清', '超清', '蓝光']}
              value={defaultQuality}
              onChange={setDefaultQuality}
            />
          </SettingItem>
        </div>

        {/* Cache Management */}
        <div className="bg-card rounded-2xl p-6 mb-6">
          <div className="flex items-center gap-3 mb-4">
            <HardDrive size={20} className="text-primary" />
            <h3 className="text-lg font-bold text-foreground">缓存管理</h3>
          </div>
          {lastClearMessage && (
            <p className="text-xs text-muted-foreground mb-3 leading-relaxed">
              {lastClearMessage}
            </p>
          )}
          <SettingItem
            icon={<HardDrive size={18} />}
            title="清除缓存"
            description={`本地可清理约 ${formatApproxByteSize(cacheEstimateBytes)}（首页海报与列表、版本更新横幅「已忽略」记录）；不会删除收藏、观看历史、播放进度与播放设置。若环境支持会同时尝试清理 Cache Storage。Android WebView 磁盘图片缓存主要由系统管理。`}
          >
            <button
              type="button"
              disabled={isClearingCache}
              {...spatialClearCache}
              className={cn(
                'tv-focusable pill-focus px-5 py-2 rounded-full text-sm font-medium transition-all',
                showCleared
                  ? 'bg-green-500 text-white'
                  : 'bg-secondary text-secondary-foreground hover:bg-surface-hover',
                isClearingCache && 'opacity-60 pointer-events-none'
              )}
              onClick={() => {
                if (!isClearingCache) setClearCacheConfirmOpen(true)
              }}
            >
              {showCleared ? (
                <span className="flex items-center gap-1">
                  <Check size={14} /> 已清除
                </span>
              ) : isClearingCache ? (
                '处理中…'
              ) : (
                '清除缓存'
              )}
            </button>
          </SettingItem>
        </div>

        {/* About */}
        <div className="bg-card rounded-2xl p-6">
          <div className="flex items-center gap-3 mb-4">
            <Info size={20} className="text-primary" />
            <h3 className="text-lg font-bold text-foreground">关于应用</h3>
          </div>
          <div className="py-2 space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-4">
              <div className="min-w-0 flex-1">
                <h4 className="text-base font-medium text-foreground">
                  版本更新
                </h4>
                <p className="text-sm text-muted-foreground mt-1">
                  仓库最新
                  {isChecking
                    ? '：检查中…'
                    : remote
                      ? `：v${remote.versionName}`
                      : '：—'}
                </p>
                <p className="text-sm text-muted-foreground mt-0.5">
                  当前应用：v{APP_VERSION_NAME}
                </p>
                {hasUpdate && (
                  <p className="text-sm text-primary mt-1.5 font-medium">
                    有更新 · 已全局提醒（首页横幅、侧栏设置红点）
                  </p>
                )}
                {checkError && (
                  <p className="text-xs text-destructive mt-1">{checkError}</p>
                )}
              </div>
              <button
                type="button"
                {...spatialVersionUpdate}
                className="relative tv-focusable pill-focus inline-flex shrink-0 items-center gap-2 px-5 py-2.5 rounded-full text-sm font-medium bg-secondary text-secondary-foreground hover:bg-surface-hover"
                onClick={() => {
                  if (hasUpdate && remote) {
                    openUpdateDialog()
                  } else {
                    void refreshCheck()
                  }
                }}
              >
                <RefreshCw
                  size={16}
                  className={cn(isChecking && 'animate-spin')}
                />
                {isChecking
                  ? '检查中…'
                  : hasUpdate && remote
                    ? '查看更新'
                    : '检查更新'}
                {!isChecking && hasUpdate && (
                  <Download size={16} className="opacity-90" />
                )}
                {hasUpdate && (
                  <span className="absolute -right-0.5 -top-0.5 h-2.5 w-2.5 rounded-full bg-red-500 ring-2 ring-card" />
                )}
              </button>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted-foreground">应用名称</span>
              <span className="text-sm text-foreground font-medium">柠檬影视 TV</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted-foreground">版本号</span>
              <span className="text-sm text-foreground font-medium">
                v{APP_VERSION_NAME}
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted-foreground">开发团队</span>
              <span className="text-sm text-foreground font-medium">Lemon Team</span>
            </div>
            <div className="pt-2 border-t border-border">
              <p className="text-sm text-muted-foreground mb-1">开源仓库</p>
              <button
                type="button"
                tabIndex={0}
                className="tv-focusable w-full rounded-lg px-3 py-2 text-left text-sm text-primary break-all underline-offset-2 hover:underline"
                onClick={() =>
                  window.open(APP_REPO_PAGE_URL, '_blank', 'noopener,noreferrer')
                }
              >
                {APP_REPO_PAGE_URL}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    {clearCacheConfirmOpen && (
      <TvConfirmDialog
        spatialIdPrefix="settings-cache-clear"
        title="确定清除缓存？"
        onCancel={() => setClearCacheConfirmOpen(false)}
        onConfirm={() => {
          setClearCacheConfirmOpen(false)
          void runClearCache()
        }}
      >
        <div className="space-y-3 text-sm leading-relaxed text-muted-foreground">
          <p>
            将清除：首页海报与列表的本地数据、版本更新横幅的「已忽略」记录；若浏览器支持，还会尝试清理
            Cache Storage。
          </p>
          <p>不会清除：收藏、观看历史、播放进度、播放设置。</p>
        </div>
      </TvConfirmDialog>
    )}
    </>
  )
}
