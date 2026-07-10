# lemonTV-Film

一款专为 **Android TV / 电视盒子** 设计的 **MacCMS 影视客户端**，支持首页分类浏览、筛选搜索、详情选集、记忆续播、跳过片头片尾、在线更新等功能。

- 包名：`com.lemon.yingshi.tv`
- 当前版本：**v1.0.0**（versionCode **10000**）
- 安装包：[Releases](https://github.com/jia070310/lemonTV-Film/releases)

## 功能特性

### 内容浏览
- **MacCMS 接入**：在设置中配置站点 API 地址与密钥，即可拉取影视数据
- **首页分区**：电视剧、电影、综艺、演唱会等分类展示
- **筛选与搜索**：按类型、地区、年份等条件筛选；支持搜索历史
- **详情页**：展示简介、海报、选集与播放源；支持多集连续浏览

### 播放体验
- **ExoPlayer 播放**：基于 Media3，支持常见点播格式
- **记忆续播**：可在设置中开关；再次打开从上次进度继续
- **变速播放**：播放器内切换播放倍速
- **选集切换**：播放器内分页选集；支持上一集 / 下一集
- **跳过片头 / 片尾**：按**整部剧集**统一配置，对所有集数生效
- **续播提示**：有历史进度时可选择「继续播放」或「从头开始」

### TV 端优化
- **Jetpack Compose for TV**：针对遥控器焦点与大屏布局优化
- **焦点导航**：方向键移动焦点，OK 键确认，返回键逐级退出
- **控制栏自动隐藏**：播放中可呼出进度条与功能按钮

### 其他
- **最近观看**：记录观看历史与本地封面截图
- **版本更新**：从 GitHub Releases 检测并下载新版本
- **远程通知**：首页通知栏支持远程 JSON 配置

## 系统要求

- Android 5.0（API 21）及以上
- Android TV 或带遥控器的电视盒子
- 可访问所配置的 MacCMS 站点网络

## 快速开始

### 安装

从 [Releases](https://github.com/jia070310/lemonTV-Film/releases) 下载最新 `app-release.apk` 安装到电视或盒子。

### 配置 MacCMS

1. 打开应用，进入 **设置**
2. 在 **MacCMS 配置** 中填写站点 API 地址与密钥
3. 保存后返回首页，等待分类与内容加载

### 构建

```bash
git clone https://github.com/jia070310/lemonTV-Film.git
cd lemonTV-Film

# 调试包
./gradlew assembleDebug

# 发布包（需项目根目录 lomenTV.jks 签名文件）
./gradlew assembleRelease
```

Release 签名配置见 `app/build.gradle.kts` 中的 `signingConfigs.release`。

## 项目结构

```
app/src/main/java/com/lemon/yingshi/tv/
├── data/           # MacCMS API、本地数据库、偏好设置
├── domain/         # 业务模型与服务（播放、历史、更新等）
├── ui/             # Compose 界面（首页、详情、播放器、设置）
├── di/             # Hilt 依赖注入
└── service/        # 广播接收器等
```

## 技术栈

- Kotlin
- Jetpack Compose + Compose for TV
- Hilt
- Room
- Media3 (ExoPlayer)
- OkHttp + Kotlin Serialization
- Navigation Compose

## 播放器快捷键

| 按键 | 功能 |
|------|------|
| OK / 中心键 | 播放 / 暂停；显示控制栏 |
| 左 / 右 | 快退 / 快进（时长可在设置中配置） |
| 上 / 下 | 控制栏内移动焦点 |
| 返回 | 隐藏控制栏 / 关闭弹窗 / 退出播放 |

## 相关链接

- 项目仓库：<https://github.com/jia070310/lemonTV-Film>
- 4K 直播源（独立仓库）：<https://github.com/jia070310/4K-IPTV-M3U>

## 开源协议

见 [LICENSE](LICENSE) 文件。
