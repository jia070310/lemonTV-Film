import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'com.lemontv.app',
  appName: '柠檬影视TV',
  webDir: 'dist',
  server: {
    /**
     * 必须为 http：若用 https，页面源为 https://localhost，对 http://内网 CMS 的 fetch
     * 会被当作「混合内容」拦截，表现为 Failed to fetch（与 usesCleartextTraffic 无关）。
     * 正式上架若坚持 https 壳，需给 CMS 配 HTTPS，或改用原生 Http 插件请求接口。
     */
    androidScheme: 'http',
  },
  android: {
    buildOptions: {
      keystorePath: undefined,
      keystoreAlias: undefined,
    },
  },
  /** 原生层走 OkHttp，内网 HTTP CMS 可正常访问（与 WebView fetch 无关） */
  plugins: {
    CapacitorHttp: {
      enabled: true,
    },
  },
}

export default config
