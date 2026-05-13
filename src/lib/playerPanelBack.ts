/** 播放器内子面板（选集/倍速等）打开时，硬件返回先关闭面板，避免直接触发「再按一次退出」 */
type PanelCloser = () => boolean

let panelCloser: PanelCloser | null = null

export function setPlayerPanelBackCloser(fn: PanelCloser | null): void {
  panelCloser = fn
}

/** @returns true 表示已消费返回（已关面板），不应再执行路由级返回 */
export function consumePlayerPanelBack(): boolean {
  return panelCloser?.() ?? false
}
