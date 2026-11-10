import { useEffect, useRef, useState } from 'react'
import { APK_SHA, APK_SIZE, FINGERPRINT, LINKS, VERSION } from '../data.js'
import { ArrowRight, ArrowUpRight, Barcode } from './Common.jsx'
import {
  BarcodeFoot,
  HoloC,
  PosterCard,
  PosterCompanion,
  PosterHead,
} from './Poster.jsx'
import GlassIcons from './reactbits/GlassIcons.jsx'
import StarBorder from './reactbits/StarBorder.jsx'
import TearTicket from './reactbits/TearTicket/TearTicket.jsx'
import SpotlightCard from './reactbits/SpotlightCard.jsx'

function groupHash(hash) {
  return hash.match(/.{1,8}/g).join(' ')
}

function useWideTicket() {
  const [wide, setWide] = useState(
    () => typeof window !== 'undefined' && window.matchMedia('(min-width: 981px)').matches,
  )
  useEffect(() => {
    const query = window.matchMedia('(min-width: 981px)')
    const sync = () => setWide(query.matches)
    query.addEventListener('change', sync)
    return () => query.removeEventListener('change', sync)
  }, [])
  return wide
}

function CopyButton({ value, label = '复制' }) {
  const [copied, setCopied] = useState(false)
  const timer = useRef(null)

  useEffect(() => () => window.clearTimeout(timer.current), [])

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value)
    } catch {
      const area = document.createElement('textarea')
      area.value = value
      area.setAttribute('readonly', '')
      area.style.position = 'fixed'
      area.style.opacity = '0'
      document.body.appendChild(area)
      area.select()
      try {
        document.execCommand('copy')
      } catch {
        /* the value is visible on screen; selection still works */
      }
      area.remove()
    }
    setCopied(true)
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setCopied(false), 1800)
  }

  return (
    <button type="button" className="copy-btn" onClick={copy} aria-live="polite">
      {copied ? '已复制' : label}
    </button>
  )
}

const Icon = ({ children }) => (
  <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {children}
  </svg>
)

const SPEC_ICONS = [
  {
    icon: (
      <Icon>
        <rect x="6" y="2.5" width="12" height="19" rx="2.5" />
        <path d="M9.5 18.5h5" />
        <circle cx="12" cy="6.5" r=".8" fill="currentColor" stroke="none" />
      </Icon>
    ),
    color: 'blue',
    label: 'ANDROID 8.0+',
    href: LINKS.releases,
  },
  {
    icon: (
      <Icon>
        <rect x="4" y="4" width="16" height="16" rx="3" />
        <path d="M8 12l2.5 2.5L16 9" />
      </Icon>
    ),
    color: 'purple',
    label: 'LOCAL ONLY',
    href: '#security',
  },
  {
    icon: (
      <Icon>
        <path d="M7 17.5h9.5a4.5 4.5 0 0 0 .8-8.9A6 6 0 0 0 6.4 10 3.8 3.8 0 0 0 7 17.5Z" />
        <path d="M4 3l16 18" />
      </Icon>
    ),
    color: 'pink',
    label: 'NO CLOUD',
    href: '#security',
  },
  {
    icon: (
      <Icon>
        <path d="M6.5 8.5A6 6 0 1 1 7 16" />
        <path d="M6.5 4v4.5H11" />
        <circle cx="12" cy="12" r="2.2" />
      </Icon>
    ),
    color: 'graphite',
    label: 'GPL-3.0',
    href: LINKS.github,
    target: '_blank',
  },
]

function VerifyItem({ no, title, hash, command, note }) {
  return (
    <div className="verify-item">
      <span className="verify-no">{no}</span>
      <h3>{title}</h3>
      <div className="verify-hash">
        <code>{groupHash(hash)}</code>
        <CopyButton value={hash} />
      </div>
      <pre>
        <code>{command}</code>
      </pre>
      <p>{note}</p>
    </div>
  )
}

export default function Install() {
  const wideTicket = useWideTicket()
  const [torn, setTorn] = useState(false)

  return (
    <section className="poster-section install-poster" id="install" aria-labelledby="install-title">
      <div className="shell poster-board">
        <PosterHead
          className="span-7"
          no="05"
          en="INSTALL"
          cn="拿走它。"
          lede="下载，装上，开始用。不注册，不订阅，不需要为它建一个账号；平台账号绑定是可选的，不绑也能手动导入。没有应用商店，直接拿 APK。"
          tone="dark"
          id="install-title"
        >
          <HoloC />
        </PosterHead>

        <PosterCompanion
          className="span-5"
          no="05"
          kicker="RELEASE / APK"
          big={VERSION}
          tone="pastel"
        >
          <ul className="poster-index">
            <li>
              <span>01</span>Android 8.0+<em>minSdk 26</em>
            </li>
            <li>
              <span>02</span>{APK_SIZE}<em>APK</em>
            </li>
            <li>
              <span>03</span>GPL-3.0<em>OPEN SOURCE</em>
            </li>
          </ul>
          <BarcodeFoot label={`APK / ${VERSION} / GPL-3.0`} />
        </PosterCompanion>

        {wideTicket ? (
          <div className="dl-tear span-12">
            <div className="poster-meta dl-tear-meta">
              <span>05 / RELEASE / TEAR STUB</span>
              <span>APK / {VERSION}</span>
            </div>
            <TearTicket
              className="dl-tear__ticket"
              width={1500}
              height={360}
              stubSize={310}
              holes={18}
              holeSize={6}
              radius={0}
              background="#ffffff"
              color="#08090b"
              borderColor="rgba(8, 9, 11, 0.18)"
              stubBackground="linear-gradient(135deg, #ffb7e1, #cbb8ff 30%, #8fe5ff 65%, #b9ffd8)"
              tearAngle={24}
              stretch={24}
              resistance={0.5}
              tilt
              tiltMax={3}
              recenter
              torn={torn}
              ariaLabel="撕下副券，提取 APK"
              onTear={() => {
                setTorn(true)
                window.open(LINKS.apk, '_blank', 'noopener,noreferrer')
              }}
              stub={
                <div className="tt-stub">
                  <span className="tt-stub__kicker">RELEASE STUB / 05</span>
                  <strong>{VERSION}</strong>
                  <Barcode />
                  <span className="tt-stub__hint">
                    按住齿孔 · 撕下副券
                    <br />
                    提取 APK
                  </span>
                  <em>{torn ? '已撕下' : 'TEAR HERE'}</em>
                </div>
              }
            >
              <div className="tt-body">
                <div className="tt-body__left">
                  <span className="dl-kicker">当前版本 / LATEST</span>
                  <strong className="dl-version">{VERSION}</strong>
                  <div className="dl-specs">
                    <span>Android 8.0+</span>
                    <span>minSdk 26</span>
                    <span>{APK_SIZE}</span>
                    <span>GPL-3.0</span>
                  </div>
                  <p>APK 直接下载 · 同一个签名包 · 无广告、无统计 SDK</p>
                </div>
                <div className="tt-body__right">
                  <StarBorder
                    as="a"
                    href={LINKS.apk}
                    className="dl-star"
                    theme="dark"
                    color="#c21f72"
                    trailColor="#7ee8fa"
                    borderColor="rgba(8, 9, 11, 0.2)"
                    backgroundColor="#08090b"
                    textColor="#f7f8fa"
                    radius={0}
                    thickness={1.2}
                    duration={5}
                    trailLength={0.3}
                    glow={0.6}
                    sparkle
                  >
                    <span className="dl-star-inner">
                      <span className="dl-star-text">
                        <span className="btn-main">下载 {VERSION}</span>
                        <span className="btn-sub">Gitee · 国内直连</span>
                      </span>
                      <ArrowRight />
                    </span>
                  </StarBorder>
                  <a className="btn btn--line btn--xl" href={LINKS.releaseTag} target="_blank" rel="noreferrer">
                    <span className="btn-main">GitHub 备用源</span>
                    <span className="btn-sub">同一个签名包</span>
                    <ArrowUpRight />
                  </a>
                  <p>
                    没有 iOS 版：iOS 读不到短信正文和别的 App 通知。
                    <a href={LINKS.releases} target="_blank" rel="noreferrer">
                      全部版本
                      <ArrowUpRight />
                    </a>
                  </p>
                </div>
              </div>
            </TearTicket>
            <div className="dl-tear__foot">
              <span>主联 = 说明与下载按钮 · 副券 = 可撕提货凭证</span>
              {torn ? (
                <button type="button" onClick={() => setTorn(false)}>
                  重置副券
                </button>
              ) : (
                <span>撕下副券会打开 APK 下载</span>
              )}
            </div>
          </div>
        ) : (
          <div className="dl-ticket span-12">
            <div className="poster-meta dl-ticket-meta">
              <span>05 / RELEASE</span>
              <span>APK / {VERSION}</span>
            </div>
            <div className="dl-ticket-left">
              <span className="dl-kicker">当前版本 / LATEST</span>
              <strong className="dl-version">{VERSION}</strong>
              <div className="dl-specs">
                <span>Android 8.0+</span>
                <span>minSdk 26</span>
                <span>{APK_SIZE}</span>
                <span>GPL-3.0</span>
              </div>
              <p>APK 直接下载 · 同一个签名包 · 无广告、无统计 SDK</p>
            </div>

            <div className="dl-ticket-right">
              <StarBorder
                as="a"
                href={LINKS.apk}
                className="dl-star"
                theme="dark"
                color="#ff9edb"
                trailColor="#7ee8fa"
                borderColor="rgba(247, 248, 250, 0.2)"
                backgroundColor="#08090b"
                textColor="#f7f8fa"
                radius={0}
                thickness={1.2}
                duration={5}
                trailLength={0.3}
                glow={0.7}
                sparkle
              >
                <span className="dl-star-inner">
                  <span className="dl-star-text">
                    <span className="btn-main">下载 {VERSION}</span>
                    <span className="btn-sub">Gitee · 国内直连</span>
                  </span>
                  <ArrowRight />
                </span>
              </StarBorder>
              <a className="btn btn--line btn--xl" href={LINKS.releaseTag} target="_blank" rel="noreferrer">
                <span className="btn-main">GitHub 备用源</span>
                <span className="btn-sub">同一个签名包</span>
                <ArrowUpRight />
              </a>
              <p>
                没有 iOS 版。不是不想做：iOS 读不到短信正文，也读不到别的 App 的通知。
                <a href={LINKS.releases} target="_blank" rel="noreferrer">
                  全部版本
                  <ArrowUpRight />
                </a>
              </p>
            </div>
          </div>
        )}

        <div className="verify span-8">
          <div className="verify-head">
            <div>
              <span>两道校验 / TWO CHECKS</span>
              <strong>不需要信任这个页面。</strong>
            </div>
            <span className="verify-head-note">校验值比任何隐私政策都算数</span>
          </div>

          <div className="verify-grid">
            <SpotlightCard
              className="verify-spotlight"
              theme="light"
              spotlightColor="#c21f72"
              intensity={0.14}
              spotlightSize={280}
              borderGlow={0.24}
            >
              <VerifyItem
                no="01"
                title="文件有没有在下发路上被换掉"
                hash={APK_SHA}
                command={`sha256sum -c SHA256SUMS.txt   # ${APK_SIZE}`}
                note="这份清单和 APK 出自同一个仓库，挡的是传输损坏和镜像篡改，挡不了冒充发布者。"
              />
            </SpotlightCard>
            <SpotlightCard
              className="verify-spotlight"
              theme="light"
              spotlightColor="#121318"
              intensity={0.12}
              spotlightSize={280}
              borderGlow={0.18}
            >
              <VerifyItem
                no="02"
                title="这个包是谁签的"
                hash={FINGERPRINT}
                command={`apksigner verify --print-certs showcode-${VERSION}.apk`}
                note="Android 的签名密钥无法轮换。对得上＝同一个维护者；对不上就不是新版本，是另一个人发的包，别装。"
              />
            </SpotlightCard>
          </div>

          <div className="verify-foot">
            <p>
              签名密钥在仓库外。当前 Release 由维护者本机签名后上传；仓库里的自动签名 CI 需要把密钥
              配进 Actions secrets 才会启用。两道校验的算法不变。
            </p>
            <a href={`${LINKS.github}`} target="_blank" rel="noreferrer">
              源码与构建说明
              <ArrowUpRight />
            </a>
          </div>
        </div>

        <PosterCard
          className="span-4 install-spec-card"
          tone="dark"
          label="SPEC / PLATFORM"
          no="05"
        >
          <div className="install-spec-head">
            <span>ANDROID · LOCAL · OPEN</span>
            <span>{VERSION}</span>
          </div>
          <GlassIcons
            items={SPEC_ICONS}
            size={64}
            gap={30}
            columns={2}
            roundness={0.52}
            refraction={0.8}
            bevel={0.6}
            dispersion={0.12}
            frost={0.18}
            shine={0.82}
            tint="#ffffff"
            tintOpacity={0.08}
            iconColor="#ffffff"
            plate="tilt"
            tilt={24}
            spread={0.4}
            hover="lift"
            parallax={0.6}
            labels="always"
            intro={false}
            theme="dark"
          />
        </PosterCard>

        <div className="install-foot-row span-12">
          <div className="install-foot-mark">
            <Barcode tall />
            <span>ANDROID ONLY · GPL-3.0 · 2026</span>
          </div>
          <p>
            没有上架任何应用商店（包括 F-Droid）。只从 GitHub / Gitee 的 Release 分发；
            两边是同一个 APK，同一个签名。
          </p>
          <div className="install-links">
            <a href={LINKS.github} target="_blank" rel="noreferrer">
              GitHub
              <ArrowUpRight />
            </a>
            <a href={LINKS.gitee} target="_blank" rel="noreferrer">
              Gitee
              <ArrowUpRight />
            </a>
            <a href={LINKS.issues} target="_blank" rel="noreferrer">
              反馈
              <ArrowUpRight />
            </a>
          </div>
        </div>
      </div>
    </section>
  )
}
