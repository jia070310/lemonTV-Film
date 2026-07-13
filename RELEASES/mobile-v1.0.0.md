# mobile-v1.0.0 (10000)

**发布日期：** 2026-07-13

**包名：** `com.lemon.yingshi.mobile`

## 概述

Lemon 影视 **手机版首发**。接入 MacCMS 站点 API，支持首页浏览、筛选、推荐、详情选集、在线播放与离线缓存，与 TV 版共用核心播放与数据层，版本号独立维护。

## 主要功能

- **MacCMS 接入**：设置中配置站点地址，拉取分类、详情与播放源
- **底部导航**：首页 / 推荐 / 我的
- **首页**：最近播放、最新推荐（level=9）、分类栏目懒加载与磁盘缓存
- **筛选**：多条件筛选影视，支持「更多」进入完整列表
- **推荐 Tab**：快速探测推荐等级 9；无数据时提示「服务器无推荐影片」
- **详情页**：简介、选集、收藏、离线缓存、立即播放
- **播放器**：ExoPlayer + HLS 多线程预缓存、变速、选集、跳过片头片尾、记忆续播
- **第三方播放地址解析**：自动将 `/share/` 分享页解析为真实 m3u8 直链
- **我的**：观看历史、收藏、离线下载、缓存管理、设置、关于与更新检测

## 体验与稳定性

- 首页栏目骨架屏 + 滚动懒加载，减少首屏等待
- 封面缓存统计包含 Coil 海报与观看历史截帧
- 退出播放页后立即停止后台播放
- Debug / Release 使用同一签名，可互相覆盖安装

## 安装升级

1. 下载 `LomenMobile-release-v1.0.0.apk`
2. 在手机上覆盖安装（数据与设置保留）
3. GitHub Release 标签请使用 **`mobile-v1.0.0`**（与 TV 版 `v1.0.x` 区分）
4. 若内置更新检测失败，可手动从 [Releases](https://github.com/jia070310/lemonTV-Film/releases) 下载安装

## 系统要求

- Android 7.0（API 24）及以上
- 可访问所配置的 MacCMS 站点（需开启视频 API）

## 构建命令

```bash
./gradlew :app-mobile:assembleRelease
```

输出：`app-mobile/build/outputs/apk/release/LomenMobile-release-v1.0.0.apk`
