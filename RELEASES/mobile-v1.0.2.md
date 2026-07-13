# mobile-v1.0.2 (10002)

**发布日期：** 2026-07-13

**包名：** `com.lemon.yingshi.mobile`

## 概述

本版本完善离线缓存流程与稳定性，修复更新安装与电影多线路选集显示问题。

## 新功能与改进

- **离线缓存两步选择**：点击「加入缓存」先选播放线路，再选集数；标题显示当前线路名
- **离线下载稳定性**：下载前解析分享页/中转链接为真实 m3u8；校验播放列表内容，拒绝 HTML 误下载
- **并行下载**：最多 3 个任务同时下载，多任务时动态调整 HLS 分片线程数
- **路径安全**：修复 episodeId 含冒号导致本地缓存路径无效、分片写入失败的问题
- **应用内更新安装**：新增 `UpdateInstallCoordinator`，修复 Android 8+ 安装权限与 FileProvider 跳转
- **电影多线路**：与 TV 端共用 `MacCmsPlayLayout`，多线路电影不再误显示为选集

## 安装升级

1. 下载 `LomenMobile-release-v1.0.2.apk`
2. 在手机上覆盖安装（数据与设置保留）
3. GitHub Release 标签请使用 **`mobile-v1.0.2`**
4. 若内置更新检测失败，可手动从 [Releases](https://github.com/jia070310/lemonTV-Film/releases) 下载安装

## 系统要求

- Android 7.0（API 24）及以上
- 可访问所配置的 MacCMS 站点（需开启视频 API）

## 构建命令

```bash
./gradlew :app-mobile:assembleRelease
```

输出：`app-mobile/build/outputs/apk/release/LomenMobile-release-v1.0.2.apk`
