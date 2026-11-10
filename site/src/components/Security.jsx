import { asset, LINKS, VERSION } from '../data.js'
import { ArrowUpRight } from './Common.jsx'
import Shredder from './reactbits/Shredder/Shredder.jsx'
import StickerPeel from './reactbits/StickerPeel/StickerPeel.jsx'
import HalftoneReveal from './reactbits/HalftoneReveal/HalftoneReveal.jsx'
import FaultyTerminal from './reactbits/FaultyTerminal/FaultyTerminal.jsx'
import StatusMark from './reactbits/StatusMark/StatusMark.jsx'
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
          tone="white"
          label="01 / SEALED"
          no="01"
        >
          <div className="no-cloud">
            <NoCloud />
            <span>NO CLOUD</span>
          </div>
          <div className="envelope-wrap">
            <div className="envelope">
              <div className="env-flap" aria-hidden="true" />
              <svg className="env-fold" viewBox="0 0 100 70" preserveAspectRatio="none" aria-hidden="true">
                <path d="M0 0 L50 40 L100 0" />
                <path d="M0 70 L38 40" />
                <path d="M100 70 L62 40" />
              </svg>
              <div className="env-body">
                <div className="env-address">
                  <p>
                    <span>收件人</span>
                    <strong>这台手机</strong>
                  </p>
                  <p>
                    <span>寄件人</span>
                    <strong>你自己</strong>
                  </p>
                  <p>
                    <span>内容</span>
                    <strong>取件码、取件点、时间</strong>
                  </p>
                  <p>
                    <span>备注</span>
                    <strong>不出门</strong>
                  </p>
                </div>
                <div className="env-reveal">
                  <HalftoneReveal
                    src={asset('icon.png')}
                    mode="mono"
                    shape="dot"
                    cellSize={5}
                    inkColor="#c21f72"
                    accentColor="#121318"
                    paperColor="#ffffff"
                    contrast={1.1}
                    revealRadius={118}
                    softness={0.75}
                    linger={1.6}
                    clickBurst
                    intro={false}
                  />
                  <span className="env-reveal-label">本地显影 / HALFTONE</span>
                </div>
                <div className="env-stamp" aria-hidden="true">
                  <span className="stamp-barcode" />
                  <span>LOCAL</span>
                  <em>CE / {VERSION}</em>
                </div>
                <div className="env-seal env-seal-sticker">
                  <img className="env-seal-fallback" src={asset('seal-local.svg')} alt="" />
                  <StickerPeel
                    imageSrc={asset('seal-local.svg')}
                    width={140}
                    rotate={-11}
                    finish="holo"
                    peelDirection={18}
                    initialPosition="center"
                    className="env-sticker"
                  />
                </div>
                <div className="env-lines" aria-hidden="true">
                  <i />
                  <i />
                  <i />
                </div>
                <div className="env-air" aria-hidden="true" />
              </div>
            </div>
            <p className="envelope-caption">
              <span className="caption-mark" aria-hidden="true">
                ↳
              </span>
              数据确实只走这一条路：从短信、通知或平台账号，到本机 SQLite。没有下一站。
            </p>
          </div>
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
