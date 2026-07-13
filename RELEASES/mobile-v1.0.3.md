# mobile-v1.0.3 (10003)

**发布日期：** 2026-07-13

**包名：** `com.lemon.yingshi.mobile`

## 概述

本版本与 TV 端同步优化存储与播放预缓存，完善缓存管理界面，并在离线缓存列表展示总占用与单项大小。

## 新功能与改进

- **滚动预缓存**：HLS 仅预取播放头前方约 90~120 秒，退出播放或冷启动自动清理播放缓存
- **播放缓存分级上限**：64MB / 128MB / 192MB（按设备剩余空间）
- **缓冲 UI 优化**：800ms 防抖；已缓冲超过 5 秒不显示全屏加载
- **我的缓存**：新增首页数据缓存项；封面/首页缓存显示「已用 / 上限」，满额 LRU 覆盖
- **Coil 海报缓存**：固定 48MB 上限
- **离线缓存容量显示**：个人页与列表页展示总占用；每条缓存显示「占用 xx MB」
- **共享层统一**：`MediaStorageHelper`、`StorageFormatter` 移至 shared 模块

## 安装升级

1. 下载 `LomenMobile-release-v1.0.3.apk`
2. 在手机上覆盖安装（数据与设置保留）
3. GitHub Release 标签请使用 **`mobile-v1.0.3`**
4. 若内置更新检测失败，可手动从 [Releases](https://github.com/jia070310/lemonTV-Film/releases) 下载安装

## 系统要求

- Android 7.0（API 24）及以上
- 可访问所配置的 MacCMS 站点（需开启视频 API）

## 构建命令

```bash
./gradlew :app-mobile:assembleRelease
```

输出：`app-mobile/build/outputs/apk/release/LomenMobile-release-v1.0.3.apk`
