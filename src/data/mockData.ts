export interface Movie {
  id: string
  title: string
  poster: string
  backdrop?: string
  rating: number
  year: string
  genre: string
  area: string
  description?: string
  episodes?: number
  tag?: string
}

export const heroMovies: Movie[] = [
  {
    id: '1',
    title: '暗黑新娘',
    poster: '/images/movie-poster-1.png',
    backdrop: '/images/hero-banner-1.png',
    rating: 9.1,
    year: '2026',
    genre: '科幻',
    area: '美国',
    description: '在一个被永恒黑暗笼罩的世界中，一位神秘的新娘踏上了寻找光明之源的征途。她将面对无数黑暗势力的阻挠，揭开这个世界最深处的秘密。',
    episodes: 12,
    tag: '热门',
  },
  {
    id: '2',
    title: '深海危机',
    poster: '/images/movie-poster-2.png',
    backdrop: '/images/hero-banner-2.png',
    rating: 8.7,
    year: '2026',
    genre: '动作',
    area: '中国',
    description: '一支深海探险队在太平洋海底发现了一座远古文明遗迹，但等待他们的不仅是惊人的发现，还有来自深渊的致命威胁。',
    episodes: 24,
    tag: '新上线',
  },
  {
    id: '3',
    title: '星际穿越：新纪元',
    poster: '/images/movie-poster-3.png',
    backdrop: '/images/hero-banner-1.png',
    rating: 9.3,
    year: '2026',
    genre: '科幻',
    area: '美国',
    description: '人类文明面临前所未有的危机，一群勇敢的宇航员穿越虫洞，寻找适合人类生存的新家园。',
  },
  {
    id: '4',
    title: '烽火长城',
    poster: '/images/movie-poster-4.png',
    backdrop: '/images/hero-banner-2.png',
    rating: 8.9,
    year: '2025',
    genre: '战争',
    area: '中国',
    description: '明朝末年，边疆将士誓死守卫长城防线，抵御外族入侵的壮烈史诗。',
  },
  {
    id: '5',
    title: '花间月影',
    poster: '/images/movie-poster-5.png',
    backdrop: '/images/hero-banner-1.png',
    rating: 8.5,
    year: '2026',
    genre: '爱情',
    area: '中国',
    description: '一段跨越时空的唯美爱情故事，月光下的花园见证了两个灵魂的相遇。',
  },
]

export const movieList: Movie[] = [
  { id: '1', title: '暗黑新娘', poster: '/images/movie-poster-1.png', rating: 9.1, year: '2026', genre: '科幻', area: '美国', tag: '热门' },
  { id: '2', title: '深海危机', poster: '/images/movie-poster-2.png', rating: 8.7, year: '2026', genre: '动作', area: '中国', tag: '新上线' },
  { id: '3', title: '星际穿越', poster: '/images/movie-poster-3.png', rating: 9.3, year: '2026', genre: '科幻', area: '美国' },
  { id: '4', title: '烽火长城', poster: '/images/movie-poster-4.png', rating: 8.9, year: '2025', genre: '战争', area: '中国' },
  { id: '5', title: '花间月影', poster: '/images/movie-poster-5.png', rating: 8.5, year: '2026', genre: '爱情', area: '中国', tag: '热门' },
  { id: '6', title: '特种行动', poster: '/images/movie-poster-6.png', rating: 8.8, year: '2026', genre: '动作', area: '美国' },
  { id: '7', title: '魔法森林', poster: '/images/movie-poster-7.png', rating: 8.4, year: '2025', genre: '奇幻', area: '英国', tag: '正片' },
  { id: '8', title: '极速狂飙', poster: '/images/movie-poster-8.png', rating: 8.6, year: '2026', genre: '竞速', area: '美国' },
  { id: '9', title: '钢铁巨兽', poster: '/images/movie-poster-9.png', rating: 8.2, year: '2025', genre: '科幻', area: '日本', tag: '正片' },
  { id: '10', title: '暗夜追踪', poster: '/images/movie-poster-10.png', rating: 8.0, year: '2026', genre: '悬疑', area: '中国' },
  { id: '11', title: '古墓迷踪', poster: '/images/movie-poster-1.png', rating: 8.3, year: '2026', genre: '冒险', area: '中国', tag: '热门' },
  { id: '12', title: '未来都市', poster: '/images/movie-poster-2.png', rating: 8.9, year: '2025', genre: '科幻', area: '美国' },
  { id: '13', title: '江湖风云', poster: '/images/movie-poster-3.png', rating: 8.6, year: '2026', genre: '武侠', area: '中国' },
  { id: '14', title: '神秘岛', poster: '/images/movie-poster-4.png', rating: 8.1, year: '2025', genre: '冒险', area: '英国' },
  { id: '15', title: '时光旅人', poster: '/images/movie-poster-5.png', rating: 9.0, year: '2026', genre: '科幻', area: '美国', tag: '新上线' },
  { id: '16', title: '王朝崛起', poster: '/images/movie-poster-6.png', rating: 8.7, year: '2025', genre: '历史', area: '中国' },
  { id: '17', title: '机械之心', poster: '/images/movie-poster-7.png', rating: 8.4, year: '2026', genre: '科幻', area: '日本' },
  { id: '18', title: '都市奇缘', poster: '/images/movie-poster-8.png', rating: 8.2, year: '2026', genre: '爱情', area: '韩国' },
]

export const categories = ['电视剧', '电影', '综艺', '动漫'] as const
export const subCategories = ['热门', '电视', '电影', '综艺', '动漫'] as const

export const filterData = {
  type: { title: '选择类型', options: ['全部', '动作片', '喜剧片', '爱情片', '科幻片', '恐怖片', '剧情片', '战争片', '动画片', '奇幻片', '悬疑片'] },
  plot: { title: '选择剧情', options: ['全部', '喜剧', '爱情', '恐怖', '动作', '科幻', '剧情', '战争', '犯罪', '动画', '奇幻', '武侠', '冒险', '悬疑', '惊悚', '古装', '历史'] },
  area: { title: '选择地区', options: ['全部', '大陆', '香港', '台湾', '美国', '法国', '英国', '日本', '韩国', '德国', '泰国', '印度'] },
  lang: { title: '选择语言', options: ['全部', '国语', '英语', '粤语', '韩语', '日语', '法语', '德语'] },
  year: { title: '选择年份', options: ['全部', '2026', '2025', '2024', '2023', '2022', '2021', '2020'] },
  sort: { title: '排序方式', options: ['人气排序', '时间排序', '评分排序'] },
} as const

export type FilterKey = keyof typeof filterData
