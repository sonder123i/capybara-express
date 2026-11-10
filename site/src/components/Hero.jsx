import { useRef } from 'react'
import { asset, LINKS, VERSION } from '../data.js'
import { ArrowDown, ArrowRight, Barcode, Reveal } from './Common.jsx'
import ShinyText from './reactbits/ShinyText.jsx'
import Magnet from './reactbits/Magnet.jsx'
import DecryptedText from './reactbits/DecryptedText/DecryptedText/DecryptedText.jsx'
import VariableProximity from './reactbits/VariableProximity/VariableProximity.jsx'

const SIDE_NAV = [
  { id: 'convenience', label: '方便' },
  { id: 'security', label: '安全' },
  { id: 'calm', label: '省心' },
  { id: 'boundaries', label: '边界' },
  { id: 'install', label: '安装' },
]

const FACTS = ['不注册', '无广告', '无统计 SDK', 'GPL-3.0']


export default function Hero() {
  const proximityRef = useRef(null)

  return (
    <section className="hero hero--holo" aria-labelledby="hero-title">
      <svg className="hero-filter" width="0" height="0" aria-hidden="true" focusable="false">
        <defs>
          <filter id="hero-glass-warp" x="-20%" y="-20%" width="140%" height="140%">
            <feTurbulence type="fractalNoise" baseFrequency="0.008 0.018" numOctaves="1" seed="4" result="noise" />
            <feDisplacementMap in="SourceGraphic" in2="noise" scale="16" xChannelSelector="R" yChannelSelector="G" />
          </filter>
        </defs>
      </svg>
      <div className="shell hero-poster">
        <Reveal className="hero-topline">
          <p className="hero-eyebrow">
            <span className="live-dot" aria-hidden="true" />
            <span>ANDROID 取件面板</span>
            <span className="slash">/</span>
            <span>完全本地解析</span>
          </p>
          <p className="hero-edition">
            <span>v{VERSION}</span>
            <span>12.7 MB</span>
            <span>GPL-3.0</span>
          </p>
        </Reveal>

        <Reveal className="hero-copy" delay={60}>
          <div className="hero-kicker">
            <DecryptedText
              text="ANDROID PICKUP CONSOLE"
              animateOn="view"
              speed={30}
              maxIterations={9}
              characters="ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#/-:"
              className="hero-kicker-code"
            />
            <i aria-hidden="true" />
            <DecryptedText
              text={`LOCAL-FIRST / ${VERSION}`}
              animateOn="view"
              speed={34}
              maxIterations={7}
              characters="ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#/-:."
              className="hero-kicker-code"
            />
          </div>

          <h1 className="hero-title" id="hero-title" aria-label="Capybara Express — 卡皮巴拉快递助手">
            <span className="hero-title-line">
              <ShinyText
                text="CAPYBARA"
                color="#08090b"
                shineColor="#ff9edb"
                speed={5.4}
                delay={1.2}
                angle={110}
                shineWidth={26}
                softness={0.82}
                glow={0.05}
                trigger="loop"
              />
            </span>
            <span className="hero-title-line hero-title-line--2">
              <ShinyText
                text="EXPRESS"
                color="#08090b"
                shineColor="#7ee8fa"
                speed={7.2}
                delay={2.1}
                angle={110}
                shineWidth={22}
                softness={0.82}
                glow={0.04}
                trigger="loop"
              />
              <span className="hero-brand-dot" aria-hidden="true" />
            </span>
          </h1>

          <p className="hero-name">卡皮巴拉快递助手</p>
          <span className="hero-tagline-proximity" ref={proximityRef}>
            <VariableProximity
              label="PARCELS ARRIVE. HASSLE DOESN’T."
              containerRef={proximityRef}
              radius={132}
              falloff="gaussian"
              fromColor="rgba(8, 9, 11, 0.76)"
              toColor="#c21f72"
              scale={1.045}
              lift={2}
              className="hero-tagline-en"
            />
          </span>
          <p className="hero-tagline">
            快递到了，麻烦没到<span className="hero-tagline-dot">。</span>
          </p>
          <span className="hero-title-rule" aria-hidden="true" />

          <p className="hero-lede">
            到站件自己进来，按取件点分好。打开就知道这一件在哪、还剩多久——
            然后就可以把这件事忘了。
          </p>

          <div className="hero-cta-row">
            <Magnet
              padding={70}
              magnetStrength={2.6}
              scale={1.03}
              tilt={3}
              wrapperClassName="hero-magnet"
            >
              <a className="hero-download" href={LINKS.apk}>
                <span className="hero-download-icon">
                  <ArrowDown />
                </span>
                <span className="hero-download-text">
                  <strong>下载 APK</strong>
                  <em>Gitee · 国内直连 · {VERSION}</em>
                </span>
              </a>
            </Magnet>
            <a className="hero-secondary" href="#calm">
              看它怎么省心
              <ArrowRight />
            </a>
          </div>

          <div className="hero-meta">
            <Barcode />
            <span>CE-{VERSION} / ANDROID 8.0+ / LOCAL-FIRST</span>
            <span className="hero-meta-dot" aria-hidden="true" />
          </div>
        </Reveal>

        <Reveal className="hero-stage" delay={120}>
          <div className="hero-stage-grid" aria-hidden="true" />
          <span className="hero-stage-label">SPECIMEN 00 / IRIDESCENT LOCALITY</span>
          <span className="hero-stage-caption">LIQUID GLASS / NO CLOUD</span>

          <div className="hero-sculpture">
            <img
              className="hero-sculpture-base"
              src={asset('holo-ribbon-v4.webp')}
              alt=""
              width="1200"
              height="1200"
              fetchPriority="high"
            />
          </div>

          <div className="hero-glass-disc">
            <span
              className="hero-glass-lens"
              aria-hidden="true"
              style={{ backgroundImage: `url(${asset('holo-ribbon-v4.webp')})`, WebkitMaskImage: undefined }}
            />
            <span className="hero-glass-tint" aria-hidden="true" />
            <span className="hero-glass-label">
              LOCAL
              <em>100%</em>
            </span>
          </div>

          <div className="hero-bars">
            <span className="hero-bars-glow" aria-hidden="true" />
            <span
              className="hero-bars-lens"
              aria-hidden="true"
              style={{
                backgroundImage: `url(${asset('holo-ribbon-v4.webp')})`,
                WebkitMaskImage: `url(${asset('brand-bars.svg')})`,
                maskImage: `url(${asset('brand-bars.svg')})`,
              }}
            />
            <span className="hero-bars-caption">CE-{VERSION} / BRAND SPECIMEN</span>
          </div>

          <nav className="hero-side-nav" aria-label="首屏章节索引">
            {SIDE_NAV.map((item) => (
              <a key={item.id} href={`#${item.id}`}>
                <span className="hero-side-dot" aria-hidden="true" />
                <em>{item.label}</em>
              </a>
            ))}
          </nav>
        </Reveal>

        <Reveal className="hero-facts-row" delay={180}>
          <ul className="hero-facts" aria-label="产品边界">
            {FACTS.map((fact) => (
              <li key={fact}>{fact}</li>
            ))}
          </ul>
          <span className="hero-scroll" aria-hidden="true">
            SCROLL
            <i />
            01—05
          </span>
        </Reveal>
      </div>
    </section>
  )
}
