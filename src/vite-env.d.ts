/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 首页/筛选「电视剧」卡片区 type_id，逗号分隔，覆盖内置映射 */
  readonly VITE_MACCMS_HOME_IDS_TV?: string
  readonly VITE_MACCMS_HOME_IDS_MOVIE?: string
  readonly VITE_MACCMS_HOME_IDS_VARIETY?: string
  readonly VITE_MACCMS_HOME_IDS_ANIME?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
