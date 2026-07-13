# mobile-v1.0.1 (10001)

**发布日期：** 2026-07-13

**包名：** `com.lemon.yingshi.mobile`

## 概述

手机版与 TV 版统一接入 `shared` 核心模块，播放与 MacCMS 数据层双端共用；优化播放地址解析失败时的提示与重试体验。

## 改进

- **共用 shared 模块**：播放、MacCMS 接入、收藏、历史等与 TV 端共用实现，减少双端行为差异
- **第三方播放地址解析**：继续支持 `/share/` 分享页 → m3u8 直链；与 MacCMS 解析接口联动
- **播放器**：解析中加载提示；`clearError` 同时清除解析错误状态

## 安装升级

1. 下载 `LomenMobile-release-v1.0.1.apk`
2. 在手机上覆盖安装（数据与设置保留）
3. GitHub Release 标签请使用 **`mobile-v1.0.1`**
4. 若内置更新检测失败，可手动从 [Releases](https://github.com/jia070310/lemonTV-Film/releases) 下载安装

## 系统要求

- Android 7.0（API 24）及以上
- 可访问所配置的 MacCMS 站点（需开启视频 API）

## 构建命令

```bash
./gradlew :app-mobile:assembleRelease
```

输出：`app-mobile/build/outputs/apk/release/LomenMobile-release-v1.0.1.apk`
