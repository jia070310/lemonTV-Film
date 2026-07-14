# mobile-v1.0.5 (10005)

**发布日期：** 2026-07-14

**包名：** `com.lemon.yingshi.mobile`

## 概述

本版本与 TV 端同步筛选逻辑：服务端扩展筛选项、筛选结果更准，并补齐一级下的二级分类选择；资源设置页按钮布局与样式调整。

## 新功能与改进

- **服务端筛选项**：解析 MacCMS `type_extend`；剧情可跟随扩展，地区/语言/年份使用统一本地选项
- **筛选准确性**：剧情、地区、语言、年份组合筛选不再混入无关结果
- **二级分类**：当前一级分类有子类时展示「分类」芯片（全部 + 二级）；无子类时不显示
- **资源设置**：测试连通性在左（描边）、保存配置在右（黄底主按钮）

## 安装升级

1. 下载 `LomenMobile-release-v1.0.5.apk`
2. 在手机上覆盖安装（数据与设置保留）
3. GitHub Release 标签请使用 **`mobile-v1.0.5`**
4. 若内置更新检测失败，可手动从 [Releases](https://github.com/jia070310/lemonTV-Film/releases) 下载安装

## 系统要求

- Android 7.0（API 24）及以上
- 可访问所配置的 MacCMS 站点（需开启视频 API）

## 构建命令

```bash
./gradlew :app-mobile:assembleRelease
```

输出：`app-mobile/build/outputs/apk/release/LomenMobile-release-v1.0.5.apk`
