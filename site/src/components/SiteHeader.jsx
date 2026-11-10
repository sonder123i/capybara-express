import CardNav from './reactbits/CardNav.jsx'
import { BrandMark } from './Common.jsx'
import { LINKS } from '../data.js'

const Logo = () => (
  <span className="cn-brand">
    <BrandMark />
    <span className="cn-brand-word">CAPYBARA</span>
  </span>
)

const ITEMS = [
  {
    label: '方便',
    description: 'EFFORTLESS — 到站件自己进来，按取件点分好。',
    links: [
      { label: '这一节', href: '#convenience' },
      { label: '省心', href: '#calm' },
    ],
  },
  {
    label: '安全',
    description: 'PRIVATE — 没有服务器，没有统计，数据留在本机。',
    links: [
      { label: '数据边界', href: '#security' },
      { label: '边界', href: '#boundaries' },
    ],
  },
  {
    label: '省心',
    description: 'CALM — 打开就知道这一件在哪、还剩多久。',
    links: [
      { label: '保管时钟', href: '#calm' },
      { label: '安装', href: '#install' },
    ],
  },
  {
    label: '边界',
    description: 'BOUNDARIES — 做不到的不吹。',
    links: [
      { label: '使用边界', href: '#boundaries' },
      { label: '源码', href: LINKS.github },
    ],
  },
  {
    label: '安装',
    description: 'INSTALL — Android 8.0+，直接拿 APK。',
    links: [
      { label: '下载 APK', href: LINKS.apk },
      { label: '全部版本', href: LINKS.releases },
    ],
  },
]

export default function SiteHeader({ progress }) {
  return (
    <header className="site-header">
      <div className="site-progress" aria-hidden="true">
        <i style={{ transform: `scaleX(${progress})` }} />
      </div>
      <CardNav
        logo={<Logo />}
        logoAlt="Capybara Express"
        logoHref="#top"
        items={ITEMS}
        cta={{ label: '下载 APK', href: LINKS.apk }}
        theme="dark"
        surface="glass"
        openOn="click"
        cardEffect="rise"
        stagger={0.1}
        bounce={0.18}
        menuLabel
        speed={1}
        cardHeight={190}
        maxWidth={1180}
        intro
        ariaLabel="主导航"
        style={{
          '--cn-panel': 'rgba(14, 14, 18, 0.48)',
          '--cn-solid': '#0e0e12',
          '--cn-tile': 'linear-gradient(180deg, rgba(32, 31, 37, 0.74), rgba(16, 16, 20, 0.72))',
          '--cn-shadow': '0 24px 48px -20px rgba(0, 0, 0, 0.55), 0 6px 16px -8px rgba(0, 0, 0, 0.4)',
          '--cn-muted': 'rgba(244, 244, 245, 0.6)',
        }}
      />
    </header>
  )
}
