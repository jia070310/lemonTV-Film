# mobile-v1.0.4 (10004)

**发布日期：** 2026-07-14

**包名：** `com.lemon.yingshi.mobile`

## 概述

本版本修复预缓存进度条显示问题，并优化播放器进度条上方时间气泡，与 TV 端一致显示 `00:00 / 00:00` 格式。

## 新功能与改进

- **预缓存进度条**：缓冲条按实际预缓存覆盖时间点显示，不再占满整条进度
- **播放时间气泡**：进度条上方始终显示 `当前时间 / 总时长`，修复开始播放时只显示黄色方块的问题
- **共享层同步**：与 TV 端共用 `prefetchedEndPositionMs` 预缓存进度计算逻辑

## 安装升级

1. 下载 `LomenMobile-release-v1.0.4.apk`
2. 在手机上覆盖安装（数据与设置保留）
3. GitHub Release 标签请使用 **`mobile-v1.0.4`**
4. 若内置更新检测失败，可手动从 [Releases](https://github.com/jia070310/lemonTV-Film/releases) 下载安装

## 系统要求

- Android 7.0（API 24）及以上
- 可访问所配置的 MacCMS 站点（需开启视频 API）

## 构建命令

```bash
./gradlew :app-mobile:assembleRelease
```

输出：`app-mobile/build/outputs/apk/release/LomenMobile-release-v1.0.4.apk`
