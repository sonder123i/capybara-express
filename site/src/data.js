export const VERSION = '0.4.7'
export const APK_BYTES = '12,760,701'
export const APK_SIZE = '12.8 MB'

export const LINKS = {
  apk: 'https://gitee.com/sonder123i/capybara-express/releases/download/v0.4.7/showcode-0.4.7.apk',
  releases: 'https://github.com/sonder123i/capybara-express/releases',
  releaseTag: 'https://github.com/sonder123i/capybara-express/releases/tag/v0.4.7',
  github: 'https://github.com/sonder123i/capybara-express',
  gitee: 'https://gitee.com/sonder123i/capybara-express',
  issues: 'https://github.com/sonder123i/capybara-express/issues',
  readme: 'https://github.com/sonder123i/capybara-express/blob/main/README.md',
  privacy: 'https://github.com/sonder123i/capybara-express/blob/main/README.md#隐私',
}

export const NAV = [
  { id: 'convenience', index: '01', label: '方便', en: 'EFFORTLESS' },
  { id: 'security', index: '02', label: '安全', en: 'PRIVATE' },
  { id: 'calm', index: '03', label: '省心', en: 'CALM' },
  { id: 'boundaries', index: '04', label: '边界', en: 'BOUNDARIES' },
  { id: 'install', index: '05', label: '安装', en: 'INSTALL' },
]

export const SCREENS = [
  {
    file: 'd-home-l.jpg',
    plate: '01',
    title: '首页',
    note: '按取件点分组，最急的在最上面',
    alt: '首页：丰巢快递柜 2 件、菜鸟驿站 3 件，按取件点分组，分别标出过期风险与安全。',
  },
  {
    file: 'd-onsite-l.jpg',
    plate: '02',
    title: '现场 · 柜机',
    note: '格口紧贴取件码，超时费用写在下面',
    alt: '柜机现场模式：取件码 8-3-1024，格口 A12，已超时提示 0.5 元每 12 小时、上限 3 元。',
  },
  {
    file: 'station.jpg',
    plate: '03',
    title: '现场 · 驿站',
    note: '货架号，不是格口号',
    alt: '菜鸟驿站现场模式：取件码 5-2-3391，货架 3排12号。',
  },
  {
    file: 'detail.jpg',
    plate: '04',
    title: '件详情',
    note: '商品、运单号、最新轨迹原文',
    alt: '件详情：商品信息、取件码、格口、运单号与最新轨迹短信原文。',
  },
  {
    file: 'accounts.jpg',
    plate: '05',
    title: '平台账号',
    note: '淘宝 / 拼多多 / 京东，可选绑定',
    alt: '平台账号设置：拼多多、淘宝、京东三个平台的绑定入口。',
  },
  {
    file: 'thresholds.jpg',
    plate: '06',
    title: '临期阈值',
    note: '丰巢剩余 6 小时、驿站关门前 90 分钟，都可以改',
    alt: '临期阈值设置：丰巢剩余免费不超过 6 小时、驿站关门前不超过 90 分钟。',
  },
  {
    file: 'alldone.jpg',
    plate: '07',
    title: '全部取完',
    note: '收工卡告诉你下一站在哪',
    alt: '全部取完的收工卡：本月共取 6 件，并提示下一个取件点。',
  },
  {
    file: 'd-home-d.jpg',
    plate: '08',
    title: '暗色',
    note: '同一套信息，换一种光线',
    alt: '暗色首页：与亮色相同的取件点分组和状态色。',
  },
]

export const asset = (name) => `${import.meta.env.BASE_URL}assets/${name}`

export const FINGERPRINT =
  'e18262c9d03e18f111a87774d0887953adc686b2650c4f5035372686ecc674d3'
export const APK_SHA =
  '794c84da7bdeaf410e32a7cbdeedf217c69a959bf975d54874bcb3a0262707d0'
