# 柠檬影视 TV

面向电视与遥控器的影视浏览与播放客户端（Web UI + Capacitor Android 壳）。当前版本：**v1.0.0**。

## 功能概览

- 首页/筛选/详情浏览，详情页内嵌预览与全屏播放页  
- 多线路、选集、播放进度与观看历史（本地存储）  
- 播放设置：自动跳过片头片尾、自动下一集、默认倍速等（`localStorage` 持久化）  
- 应用内检查 GitHub Releases 更新（配置见 `src/config/version.ts`）

## 技术栈

- **前端**：React 18、TypeScript、Vite 6、Tailwind CSS、React Router  
- **跨端**：Capacitor 8（Android `minSdk` / `targetSdk` 以 `android/variables.gradle` 为准）

## 环境要求

- Node.js 18+（推荐 LTS）  
- JDK 17（与当前 `android/app/build.gradle` 中 `JavaVersion.VERSION_17` 一致）  
- Android SDK（通过 Android Studio 或命令行已配置 `ANDROID_HOME`）

## 本地开发

```bash
npm install
npm run dev
```

浏览器访问终端提示的本地地址即可调试 UI（部分 TV 焦点与 WebView 行为需在真机或模拟器上验证）。

## Web 构建

```bash
npm run build
```

产物输出到 `dist/`。

## Android 调试包（Debug）

```bash
npm run build
npx cap sync android
cd android
.\gradlew.bat assembleDebug
```

APK 路径：`android/app/build/outputs/apk/debug/app-debug.apk`

## Android 发行包（Release）

```bash
npm run build
npx cap sync android
cd android
.\gradlew.bat assembleRelease
```

可安装的 Release APK 路径：

`android/app/build/outputs/apk/release/app-release.apk`

### 发布签名（Release keystore）

`android/app/build.gradle` 会读取 **`android/keystore.properties`**（已在 `android/.gitignore` 中忽略，勿提交）。若该文件不存在，Release 会回退为 **debug 证书**（便于临时安装，不适合上架）。

**首次在本机生成密钥库与配置（Windows，需 JDK / `keytool`）：**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/create-android-release-keystore.ps1
```

会在 `android/` 下生成 `lemon-release.jks` 与 `keystore.properties`。**请立即备份** `.jks` 与密码；丢失后无法用同一签名覆盖安装已发布应用。

**改用已有 `.jks`：** 将文件放到 `android/`（或自定义路径），复制 `android/keystore.properties.example` 为 `keystore.properties`，填写 `storePassword`、`keyPassword`、`keyAlias`、`storeFile`（`storeFile` 为相对于 **`android/` 目录** 的文件名，例如 `lemon-release.jks`）。

> 参考：[Android 应用签名](https://developer.android.com/studio/publish/app-signing)

## 版本号约定

- **用户可见版本名**：`android/app/build.gradle` 的 `versionName` 与 `src/config/version.ts` 中的 `APP_VERSION_NAME` 须保持一致（当前为 `1.0.0`）。  
- **整型 versionCode**：`build.gradle` 的 `versionCode` 每次上架前需递增（当前与语义化版本对齐策略见仓库内注释）。

## 网络与 CMS 说明

应用通过内置接口拉取影视数据；若 CMS 为内网 HTTP，项目内 `capacitor.config.ts` 对 `androidScheme` 等已有相关说明，部署时请与安全策略一并评估。

## 开源与更新

- 仓库展示链接：`src/config/version.ts` 中的 `APP_REPO_PAGE_URL`  
- 版本比对使用 GitHub Releases（`GITHUB_RELEASES_LATEST_API`）

## 许可

以仓库内许可证文件为准（若未添加，请自行补充）。

---

**v1.0.0**：首个对外说明的正式版本号，与 Android `versionName` / 前端 `APP_VERSION_NAME` 统一为 `1.0.0`（界面展示为 v1.0.0）。
