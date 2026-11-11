import { LINKS, VERSION } from '../data.js'
import { ArrowUpRight } from './Common.jsx'
import Shredder from './reactbits/Shredder/Shredder.jsx'
import BorderGlow from './reactbits/BorderGlow/BorderGlow.jsx'
import DepthText from './reactbits/DepthText.jsx'
import SplitFlapText from './reactbits/SplitFlapText/SplitFlapText.jsx'
import FaultyTerminal from './reactbits/FaultyTerminal/FaultyTerminal.jsx'
import StatusMark from './reactbits/StatusMark/StatusMark.jsx'
import ViewportShaderGate from './ViewportShaderGate.jsx'
import {
  HoloC,
  PosterCard,
  PosterHead,
} from './Poster.jsx'

const SHRED_ITEMS = [
  { id: 'cloud', no: '01', label: '云端取件码备份', tag: 'REJECTED' },
  { id: 'ads', no: '02', label: '广告画像 SDK', tag: 'SHREDDED' },
  { id: 'stats', no: '03', label: '第三方统计遥测', tag: 'ZERO' },
  { id: 'account', no: '04', label: '账号注册与登录', tag: 'NONE' },
]

const AUDIT = [
  { key: '解析', value: '本机 CPU', note: '无网络出口', tag: 'LOCAL', tone: 'local' },
  { key: '取件数据', value: '本机 SQLite', note: '卸载即消失', tag: 'LOCAL', tone: 'local' },
  {
    key: '平台凭据',
    value: 'Android Keystore',
    note: 'AES-256-GCM · 只存本机',
    tag: 'ENCRYPTED',
    tone: 'local',
  },
  { key: '广告 / 统计', value: '不存在', note: '全仓零统计 SDK', tag: 'NONE', tone: 'none' },
  {
    key: '平台同步',
    value: '只在你说的时候',
    note: '只访问官方域名',
    tag: 'OPTIONAL',
    tone: 'optional',
  },
  {
    key: '更新检查',
    value: '6 小时最多一次',
    note: '可在设置 → 关于关闭',
    tag: 'OPTIONAL',
    tone: 'optional',
  },
]

function NoCloud() {
  return (
    <svg className="no-cloud-icon" viewBox="0 0 72 48" role="img" aria-label="没有云端">
      <path
        d="M22 39h34a12 12 0 0 0 1.2-23.9A16 16 0 0 0 26 19a10.5 10.5 0 0 0-4 20Z"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
      />
      <path d="M7 5 64 44" fill="none" stroke="var(--red)" strokeWidth="3.2" />
    </svg>
  )
}

function SealStamp() {
  return (
    <svg className="seal-vault__stamp" viewBox="0 0 240 240" role="img" aria-label="本机封存印章">
      <defs>
        <path id="sv-ring" d="M120,120 m-94,0 a94,94 0 1,1 188,0 a94,94 0 1,1 -188,0" />
        <path id="sv-ring-in" d="M120,120 m-78,0 a78,78 0 1,0 156,0 a78,78 0 1,0 -156,0" />
      </defs>
      <circle cx="120" cy="120" r="112" fill="none" stroke="currentColor" strokeWidth="2" />
      <circle cx="120" cy="120" r="102" fill="none" stroke="currentColor" strokeWidth="0.8" opacity="0.6" />
      <circle cx="120" cy="120" r="62" fill="none" stroke="currentColor" strokeWidth="1.2" />
      <text fill="currentColor" fontSize="10" letterSpacing="2.2" fontFamily="monospace">
        <textPath href="#sv-ring">PRIVATE BY DEFAULT · NO CLOUD · NO TRACKING ·</textPath>
      </text>
      <text fill="currentColor" fontSize="30" fontWeight="600" textAnchor="middle" fontFamily="var(--serif)">
        <textPath href="#sv-ring-in" startOffset="50%">不出门</textPath>
      </text>
      <text x="120" y="154" fill="currentColor" fontSize="9" letterSpacing="3" textAnchor="middle" fontFamily="monospace">LOCAL</text>
      <text x="120" y="176" fill="currentColor" fontSize="7" letterSpacing="2" textAnchor="middle" fontFamily="monospace">CE / {VERSION}</text>
    </svg>
  )
}

export default function Security() {
  return (
    <section
      className="poster-section poster-section--dark security-poster"
      id="security"
      aria-labelledby="security-title"
    >
      <div className="shell poster-board">
        <PosterCard
          className="span-5 zero-shred-card"
          tone="white"
          label="ZERO / SPECIMEN"
          no="02"
          foot
        >
          <div className="zero-stage">
            <div className="zero-stage__left">
              <span className="zero-stage__big" aria-label="零账号、零广告、零统计">
                0
              </span>
              <ul className="zero-index" aria-label="零账号、零广告、零统计">
                <li>
                  <span>00</span>账号<em>ACCOUNT</em>
                </li>
                <li>
                  <span>00</span>广告<em>ADS</em>
                </li>
                <li>
                  <span>00</span>统计 SDK<em>STATS</em>
                </li>
              </ul>
            </div>
            <div className="zero-stage__shredder">
              <span className="zero-stage__hint">DRAG IN / AUTO FEED</span>
              <ViewportShaderGate rootMargin="200px 0px">
              <Shredder
                items={SHRED_ITEMS}
                width={280}
                height={290}
                inset={8}
                gap={6}
                slitHeight={3}
                fallHeight={90}
                feedSpeed={170}
                stripWidth={8}
                autoFeed
                autoAnimate
                loop
                slitColor="#c21f72"
                color="#08090b"
                renderItem={(item) => (
                  <div className="shred-item">
                    <span>{item.no}</span>
                    <strong>{item.label}</strong>
                    <em>{item.tag}</em>
                  </div>
                )}
              />
              </ViewportShaderGate>
            </div>
          </div>
          <p className="zero-stage__note">
            商业 App 的隐私负资产，在这里被切碎。
            账号 / 广告 / 统计 SDK：各 0。
          </p>
        </PosterCard>

        <PosterHead
          className="span-7"
          no="02"
          en="PRIVATE"
          cn="它没有服务器。"
          lede="没有这个 App 的账号，没有云端，没有广告，也没有统计 SDK。取件码在手机上解析，也留在手机上。"
          tone="pastel"
          id="security-title"
        >
          <HoloC />
        </PosterHead>

        <PosterCard
          className="span-7 envelope-card"
          tone="dark"
          label="01 / SEALED"
          no="01"
        >
          <BorderGlow
            className="seal-vault"
            backgroundColor="#0a0b0f"
            borderRadius={14}
            edgeSensitivity={26}
            glowColor="194 31 114"
            glowRadius={46}
            glowIntensity={1.15}
            coneSpread={22}
            animated
            colors={['#ff9edb', '#7ee8fa', '#b8ffd9']}
            fillOpacity={0.5}
          >
            <div className="seal-vault__inner">
              <div className="seal-vault__head">
                <div className="no-cloud no-cloud--on-dark">
                  <NoCloud />
                  <span>NO CLOUD / 封存于本机</span>
                </div>
                <span className="seal-vault__tag">SEALED · 无出口</span>
              </div>

              <div className="seal-vault__hero">
                <div className="seal-vault__word">
                  <DepthText
                    text="LOCAL"
                    layers={26}
                    depth={2.2}
                    faceColor="#f7f8fa"
                    depthColor="#ff5bbd"
                    tilt={8}
                    autoOrbit
                    orbitSpeed={0.32}
                    fontSize="clamp(46px, 6.4vw, 92px)"
                    fontWeight={800}
                    shadow
                    intro={false}
                  />
                  <span className="seal-vault__word-sub">SQLite · 卸载即消失</span>
                </div>
                <div className="seal-vault__ledger" aria-hidden="true">
                  <span className="seal-vault__ledger-label">CLOUD UPLOADS</span>
                  <SplitFlapText
                    text="000000"
                    padTo={6}
                    loop
                    flipDuration={0.2}
                    stagger={0.06}
                    flipsPerChar={3}
                    cycleDelay={3400}
                    fontSize={26}
                    gap={3}
                    tileRadius={4}
                    tileColor="#101014"
                    textColor="#7ee8fa"
                    depth={0.25}
                    glare={0.1}
                    charset="0123456789"
                    words={['000000']}
                  />
                  <span className="seal-vault__ledger-note">本机解析 · 零上传</span>
                </div>
              </div>

              <dl className="seal-vault__manifest">
                <div>
                  <dt>收件人</dt>
                  <dd>这台手机</dd>
                </div>
                <div>
                  <dt>寄件人</dt>
                  <dd>你自己</dd>
                </div>
                <div>
                  <dt>内容</dt>
                  <dd>取件码 · 取件点 · 时间</dd>
                </div>
                <div>
                  <dt>备注</dt>
                  <dd>不出门，没有下一站</dd>
                </div>
              </dl>

              <div className="seal-vault__foot">
                <span className="stamp-barcode" aria-hidden="true" />
                <em>CE / {VERSION} · LOCAL-FIRST</em>
                <SealStamp />
              </div>
            </div>
          </BorderGlow>
          <p className="envelope-caption">
            <span className="caption-mark" aria-hidden="true">
              ↳
            </span>
            数据确实只走这一条路：从短信、通知或平台账号，到本机 SQLite。没有下一站。
          </p>
        </PosterCard>

        <PosterCard
          className="span-5 audit-card"
          tone="dark"
          label="02 / DATA BOUNDARY"
          no="02"
        >
          <div className="audit-terminal" aria-hidden="true">
            <FaultyTerminal
              scale={1.1}
              gridMul={[2, 1]}
              digitSize={1.3}
              timeScale={0.2}
              scanlineIntensity={0.12}
              glitchAmount={0.22}
              flickerAmount={0.5}
              noiseAmp={0.4}
              curvature={0.12}
              tint="#7ee8fa"
              mouseReact
              mouseStrength={0.1}
              brightness={0.45}
              glyphs="blocks"
              glow={0.16}
              scramble={0.35}
              vignette={0.35}
              pageLoadAnimation={false}
            />
          </div>
          <div className="audit">
            <div className="audit-head">
              <span>数据边界</span>
              <em>DATA BOUNDARY</em>
            </div>
            <dl>
              {AUDIT.map((row) => (
                <div className="audit-row" key={row.key}>
                  <dt>{row.key}</dt>
                  <dd>
                    <strong>{row.value}</strong>
                    <span>{row.note}</span>
                  </dd>
                  <span className={`audit-tag audit-tag--${row.tone}`}>
                    <StatusMark
                      status={row.tone === 'optional' ? 'pending' : 'done'}
                      size={11}
                      doneColor="currentColor"
                      strike={false}
                    />
                    {row.tag}
                  </span>
                </div>
              ))}
            </dl>
            <p className="audit-foot">
              不绑定平台账号，就不会读取订单，也不会跑同步；启动时仍会检查一次更新——
              6 小时最多一次，可在「设置 → 关于」关闭。
              <a href={LINKS.privacy} target="_blank" rel="noreferrer">
                源码里核对
                <ArrowUpRight />
              </a>
            </p>
          </div>
        </PosterCard>

        <article className="poster-privacy-quote span-12">
          <span>PRINCIPLE / 01</span>
          <p>
            隐私不是一句承诺，
            <br />
            是没有那条路。
          </p>
        </article>
      </div>
    </section>
  )
}
