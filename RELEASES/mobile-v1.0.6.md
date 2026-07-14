# mobile-v1.0.6 (10006)

**发布日期：** 2026-07-14

**包名：** `com.lemon.yingshi.mobile`

## 概述

本版本与 TV 端同步播放项展示逻辑：详情页与缓存选择统一使用接口返回的集名/版本名，电影单版本也会直观显示，避免缓存弹窗出现 01/02 这类误导序号。

## 新功能与改进

- **播放版本展示**：电影即使只有一个版本（如 HD国语）也会显示「播放版本」
- **原始集名**：详情页 / 缓存选择 / 播放器选集均显示接口名（第1集、第01集、1、一集、HD中字、粤语等）
- **电影多版本**：国语 / 粤语 / HD中字 等在详情页正常展示，缓存时可正确选择
- **缓存流程**：选线路后若仅 1 条地址则直接缓存；多版本时弹出选择框并显示真实名称

## 安装升级

1. 下载 `LomenMobile-release-v1.0.6.apk`
2. 在手机上覆盖安装（数据与设置保留）
3. GitHub Release 标签请使用 **`mobile-v1.0.6`**
4. 若内置更新检测失败，可手动从 [Releases](https://github.com/jia070310/lemonTV-Film/releases) 下载安装

## 系统要求

- Android 7.0（API 24）及以上
- 可访问所配置的 MacCMS 站点（需开启视频 API）

## 构建命令

```bash
./gradlew :app-mobile:assembleRelease
```

输出：`app-mobile/build/outputs/apk/release/LomenMobile-release-v1.0.6.apk`
