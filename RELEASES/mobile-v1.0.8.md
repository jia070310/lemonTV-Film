# mobile-v1.0.8 (10008)

**发布日期：** 2026-10-06

**包名：** `com.lemon.yingshi.mobile`

## 概述

与 TV 端同步：多 MacCMS 源、按源隐私、首页推荐 9 走采集接口。手机端资源页不再提供扫码；隐私弹窗服务器下拉改为深色双行样式。

## 新功能与改进

- **多源管理**：名称、列表测试、删除确认；当前源不通时自动切换
- **按源隐私**：关键词与隐藏分类在弹窗内选择服务器后保存
- **服务器下拉**：深色面板、名称 + 地址、当前项高亮
- **去掉扫码入口**（手机本机即可配置）
- **最新推荐**：优先接口 `level=9`，网页筛选页只作补漏

## 安装升级

1. 下载 `LomenMobile-release-v1.0.8.apk`
2. 在手机上覆盖安装（数据与设置保留）
3. GitHub Release 标签请使用 **`mobile-v1.0.8`**
4. 若内置更新检测失败，可手动从 [Releases](https://github.com/jia070310/lemonTV-Film/releases) 下载安装

## 系统要求

- Android 7.0（API 24）及以上
- 可访问所配置的 MacCMS 站点（需开启视频 API）

## 构建命令

```bash
./gradlew :app-mobile:assembleRelease
```

输出：`app-mobile/build/outputs/apk/release/LomenMobile-release-v1.0.8.apk`
