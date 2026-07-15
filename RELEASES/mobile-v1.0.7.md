# mobile-v1.0.7 (10007)

**发布日期：** 2026-07-15

**包名：** `com.lemon.yingshi.mobile`

## 概述

本版本与 TV 端同步新增「隐私设置」：支持敏感关键词过滤分类，以及手动隐藏分类；关闭一级时二级一并隐藏。首页与片库均生效。

## 新功能与改进

- **隐私设置入口**：设置页新增「隐私设置」
- **敏感关键词过滤**：多个关键词用英文逗号（,）分隔，例如：伦理,福利,写真
- **手动隐藏分类**：读取服务器分类后可开关隐藏；关闭一级时二级开关同步关闭
- **全局生效**：首页与筛选页均应用隐私过滤

## 安装升级

1. 下载 `LomenMobile-release-v1.0.7.apk`
2. 在手机上覆盖安装（数据与设置保留）
3. GitHub Release 标签请使用 **`mobile-v1.0.7`**
4. 若内置更新检测失败，可手动从 [Releases](https://github.com/jia070310/lemonTV-Film/releases) 下载安装

## 系统要求

- Android 7.0（API 24）及以上
- 可访问所配置的 MacCMS 站点（需开启视频 API）

## 构建命令

```bash
./gradlew :app-mobile:assembleRelease
```

输出：`app-mobile/build/outputs/apk/release/LomenMobile-release-v1.0.7.apk`
