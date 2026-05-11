/**
 * 与 MACCMS 后台「库分类详细信息」「分类ID」说明一致，用于首页分区与筛选选项。
 */
export const HOME_NAV_CATEGORIES = ['电视剧', '电影', '综艺', '动漫'] as const
export type HomeNavCategory = (typeof HOME_NAV_CATEGORIES)[number]

/** 首页 Tab：各主类下允许的 type_id（子类），用于全站列表中过滤「最新」 */
export const TYPE_IDS_BY_HOME_CATEGORY: Record<HomeNavCategory, number[]> = {
  电视剧: [13, 14, 15, 16, 23],
  电影: [6, 7, 8, 9, 10, 11, 12, 19, 20, 21, 37],
  综艺: [24, 25, 26, 27],
  动漫: [28, 29, 30, 31],
}

export type MaccmsFilterKey = 'type' | 'plot' | 'area' | 'lang' | 'year' | 'sort'

export const MACCMS_FILTER_KEYS: MaccmsFilterKey[] = [
  'type',
  'plot',
  'area',
  'lang',
  'year',
  'sort',
]

export type MaccmsFilterOptionRow = {
  type: string[]
  plot: string[]
  area: string[]
  lang: string[]
  year: string[]
  sort: string[]
}

/** 选项文案 → 对应 type_id；「全部」表示该主类下全部子类 */
export const FILTER_TYPE_TO_IDS: Record<HomeNavCategory, Record<string, number[]>> = {
  电视剧: {
    全部: [13, 14, 15, 16, 23],
    国产剧: [13],
    港台剧: [14],
    日韩剧: [15],
    欧美剧: [16],
    短剧: [23],
  },
  电影: {
    全部: [6, 7, 8, 9, 10, 11, 12, 19, 20, 21, 37],
    动作片: [6],
    喜剧片: [7],
    爱情片: [8],
    科幻片: [9],
    恐怖片: [10],
    剧情片: [11],
    战争片: [12],
    动画片: [19],
    奇幻片: [20],
    悬疑片: [21],
    动漫电影: [37],
  },
  综艺: {
    全部: [24, 25, 26, 27],
    大陆综艺: [24],
    日韩综艺: [25],
    港台综艺: [26],
    欧美综艺: [27],
  },
  动漫: {
    全部: [28, 29, 30, 31],
    国产动漫: [28],
    日韩动漫: [29],
    港台动漫: [30],
    欧美动漫: [31],
  },
}

const FILTER_OPTIONS_BY_CATEGORY: Record<HomeNavCategory, MaccmsFilterOptionRow> = {
  电影: {
    type: [
      '全部',
      '动作片',
      '喜剧片',
      '爱情片',
      '科幻片',
      '恐怖片',
      '剧情片',
      '战争片',
      '动画片',
      '奇幻片',
      '悬疑片',
      '伦理片',
      '动漫电影',
    ],
    plot: [
      '全部',
      '喜剧',
      '爱情',
      '恐怖',
      '动作',
      '科幻',
      '剧情',
      '战争',
      '警匪',
      '犯罪',
      '动画',
      '奇幻',
      '武侠',
      '冒险',
      '枪战',
      '悬疑',
      '惊悚',
      '经典',
      '青春',
      '文艺',
      '微电影',
      '古装',
      '历史',
      '运动',
      '农村',
      '儿童',
      '网络电影',
    ],
    area: [
      '全部',
      '大陆',
      '香港',
      '台湾',
      '美国',
      '法国',
      '英国',
      '日本',
      '韩国',
      '德国',
      '泰国',
      '印度',
      '意大利',
      '西班牙',
      '加拿大',
      '其他',
    ],
    lang: ['全部', '国语', '英语', '粤语', '闽南语', '韩语', '日语', '法语', '德语', '其它'],
    year: [
      '全部',
      '2026',
      '2025',
      '2024',
      '2023',
      '2022',
      '2021',
      '2020',
      '2019',
      '2018',
      '2017',
      '2016',
      '2015',
      '2014',
    ],
    sort: ['时间排序', '人气排序', '评分排序'],
  },
  电视剧: {
    type: ['全部', '国产剧', '港台剧', '日韩剧', '欧美剧', '短剧'],
    plot: [
      '全部',
      '喜剧',
      '爱情',
      '恐怖',
      '动作',
      '科幻',
      '剧情',
      '战争',
      '警匪',
      '犯罪',
      '动画',
      '奇幻',
      '武侠',
      '冒险',
      '枪战',
      '悬疑',
      '惊悚',
      '经典',
      '青春',
      '文艺',
      '微电影',
      '古装',
      '历史',
      '运动',
      '农村',
      '儿童',
      '网络电影',
    ],
    area: [
      '全部',
      '大陆',
      '香港',
      '台湾',
      '美国',
      '法国',
      '英国',
      '日本',
      '韩国',
      '德国',
      '泰国',
      '印度',
      '意大利',
      '西班牙',
      '加拿大',
      '其他',
    ],
    lang: ['全部', '国语', '英语', '粤语', '闽南语', '韩语', '日语', '其它'],
    year: [
      '全部',
      '2026',
      '2025',
      '2024',
      '2023',
      '2022',
      '2021',
      '2020',
      '2019',
      '2018',
      '2017',
      '2016',
      '2015',
      '2014',
    ],
    sort: ['时间排序', '人气排序', '评分排序'],
  },
  综艺: {
    type: ['全部', '大陆综艺', '日韩综艺', '港台综艺', '欧美综艺'],
    plot: [
      '全部',
      '选秀',
      '情感',
      '访谈',
      '播报',
      '旅游',
      '音乐',
      '美食',
      '纪实',
      '曲艺',
      '生活',
      '游戏互动',
      '财经',
      '求职',
    ],
    area: ['全部', '内地', '港台', '日韩', '欧美'],
    lang: ['全部', '国语', '英语', '粤语', '闽南语', '韩语', '日语', '其它'],
    year: [
      '全部',
      '2026',
      '2025',
      '2024',
      '2023',
      '2022',
      '2021',
      '2020',
      '2019',
      '2018',
      '2017',
      '2016',
      '2015',
      '2014',
    ],
    sort: ['时间排序', '人气排序', '评分排序'],
  },
  动漫: {
    type: ['全部', '国产动漫', '日韩动漫', '港台动漫', '欧美动漫'],
    plot: [
      '全部',
      '情感',
      '科幻',
      '热血',
      '推理',
      '搞笑',
      '冒险',
      '萝莉',
      '校园',
      '动作',
      '机战',
      '运动',
      '战争',
      '少年',
      '少女',
      '社会',
      '原创',
      '亲子',
      '益智',
      '励志',
      '其他',
    ],
    area: ['全部', '国产', '日本', '欧美', '其他'],
    lang: ['全部', '国语', '英语', '粤语', '闽南语', '韩语', '日语', '其它'],
    year: [
      '全部',
      '2026',
      '2025',
      '2024',
      '2023',
      '2022',
      '2021',
      '2020',
      '2019',
      '2018',
      '2017',
      '2016',
      '2015',
      '2014',
    ],
    sort: ['时间排序', '人气排序', '评分排序'],
  },
}

export function defaultHomeCategory(): HomeNavCategory {
  return '电视剧'
}

export function parseHomeCategory(param: string | null): HomeNavCategory {
  if (param && (HOME_NAV_CATEGORIES as readonly string[]).includes(param)) {
    return param as HomeNavCategory
  }
  return defaultHomeCategory()
}

export function getFilterLabels(): Record<MaccmsFilterKey, string> {
  return {
    type: '类型',
    plot: '剧情',
    area: '地区',
    lang: '语言',
    year: '年份',
    sort: '排序',
  }
}

export function getFilterModalTitles(): Record<MaccmsFilterKey, string> {
  return {
    type: '选择类型',
    plot: '选择剧情',
    area: '选择地区',
    lang: '选择语言',
    year: '选择年份',
    sort: '排序方式',
  }
}

export function getFilterOptionsForCategory(cat: HomeNavCategory): Record<
  MaccmsFilterKey,
  { title: string; options: readonly string[] }
> {
  const row = FILTER_OPTIONS_BY_CATEGORY[cat]
  const titles = getFilterModalTitles()
  return {
    type: { title: titles.type, options: row.type },
    plot: { title: titles.plot, options: row.plot },
    area: { title: titles.area, options: row.area },
    lang: { title: titles.lang, options: row.lang },
    year: { title: titles.year, options: row.year },
    sort: { title: titles.sort, options: row.sort },
  }
}

/** 地区别名：库内字段可能与选项文案略有差异 */
const AREA_ALIASES: Record<string, string[]> = {
  大陆: ['大陆', '内地', '国产'],
  内地: ['内地', '大陆'],
  国产: ['国产', '大陆', '内地'],
  香港: ['香港', '港台'],
  台湾: ['台湾', '港台'],
  日韩: ['日韩', '日本', '韩国'],
  欧美: ['欧美', '美国', '英国', '法国'],
}

export function areaMatchesFilter(vodArea: string, filterArea: string): boolean {
  if (filterArea === '全部') return true
  const a = (vodArea || '').trim()
  if (!a) return false
  const aliases = AREA_ALIASES[filterArea]
  if (aliases) {
    return aliases.some(x => a.includes(x) || x.includes(a))
  }
  return a.includes(filterArea) || filterArea.includes(a)
}

export function langMatchesFilter(vodLang: string, filterLang: string): boolean {
  if (filterLang === '全部') return true
  const s = (vodLang || '').trim()
  if (!s) return false
  if (s.includes(filterLang)) return true
  if (filterLang === '其它' || filterLang === '其他') {
    return !['国语', '英语', '粤语', '闽南语', '韩语', '日语', '法语', '德语'].some(k =>
      s.includes(k)
    )
  }
  return false
}

export function classMatchesPlot(vodClass: string, plot: string): boolean {
  if (plot === '全部') return true
  const c = (vodClass || '').replace(/\s/g, '')
  if (!c) return false
  const parts = c.split(/[,，|]/)
  return parts.some(p => p.includes(plot) || plot.includes(p))
}
