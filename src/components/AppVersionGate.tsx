import { VersionUpdateDialog } from '@/components/VersionUpdateDialog'
import { useVersionUpdate } from '@/context/VersionUpdateContext'

/** 挂载在 Router 内，统一渲染全局版本更新弹窗 */
export function AppVersionGate() {
  const {
    dialogOpen,
    remote,
    closeUpdateDialog,
    startDownload,
    currentVersionName,
  } = useVersionUpdate()

  if (!dialogOpen || !remote) return null

  return (
    <VersionUpdateDialog
      remote={remote}
      currentVersionName={currentVersionName}
      onClose={closeUpdateDialog}
      onDownload={startDownload}
    />
  )
}
