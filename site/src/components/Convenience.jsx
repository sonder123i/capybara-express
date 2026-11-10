import { asset, VERSION } from '../data.js'
import { Tag } from './Common.jsx'
import ViewportShaderGate from './ViewportShaderGate.jsx'
import ElectricBorder from './reactbits/ElectricBorder/ElectricBorder.jsx'
import LaserFlow from './reactbits/LaserFlow/LaserFlow.jsx'
import SplitFlapText from './reactbits/SplitFlapText/SplitFlapText.jsx'
import TiltedCard from './reactbits/TiltedCard/TiltedCard.jsx'
import {
  BarcodeFoot,
  HoloX,
  PosterCard,
  PosterCompanion,
  PosterHead,
} from './Poster.jsx'

const SOURCES = [
  {
    index: 'A',
    title: '平台账号',
    body: '淘宝 / 拼多多 / 京东。登录在官方页面完成，凭据加密存本机。',
    tag: '主路径',
    tone: 'blue',
  },
  {
    index: 'B',
    title: '短信 / 通知',
    body: '取件码、取件点、格口或货架，从原文里认出来。装完回填最近 14 天。',
    tag: '补充',
    tone: 'ink',
  },
  {
    index: 'C',
    title: '手动 / 选中文字',
    body: '一条没被认出来的，自己补一条；长按单号也能直接带进来。',
    tag: '兜底',
    tone: 'ink',
  },
]

const POINTS = [
  {
    name: '丰巢快递柜（3号楼下）',
    count: '2',
    unit: '件',
    state: '1 件已超时',
    tone: 'risk',
    codes: ['8-3-1024 · A12', '6-2-0588 · B07'],
    extra: '',
  },
  {
    name: '菜鸟驿站（阳光花园小区东门）',
    count: '3',
    unit: '件',
    state: '都还安全',
    tone: 'safe',
    codes: ['5-2-3391 · 3排12号', '76416 · 2排03号'],
    extra: '+1',
  },
]

function PointCode({ code }) {
  const [main, ...rest] = code.split(' · ')
  return (
    <span className="point-code">
      <span className="point-code__flap">
        <SplitFlapText
          text={main}
          padTo={main.length}
          loop={false}
          flipDuration={0.16}
          stagger={0.035}
          flipsPerChar={5}
          fontSize={12}
          gap={2}
          tileRadius={2}
          tileColor="#101014"
          textColor="#f7f8fa"
          depth={0}
          glare={0.08}
          charset="0123456789-"
        />
      </span>
      {rest.length ? <span className="point-code__rest">· {rest.join(' · ')}</span> : null}
    </span>
  )
}

const PARCELS = [
  {
    id: 'received',
    no: '01',
    kicker: 'BOX / PAPER',
    title: '箱子里装的，才是你等的那一件。',
    image: 'parcels/thank-you-box.webp',
    alt: '贴着谢谢惠顾面单的纸箱',
  },
  {
    id: 'transit',
    no: '02',
    kicker: 'TAPE / TRANSIT',
    title: '胶带负责路上，不负责提醒你。',
    image: 'parcels/blue-tape-boxes.webp',
    alt: '蓝色封箱胶带封住的纸箱',
  },
  {
    id: 'sealed',
    no: '03',
    kicker: 'INNER / SEALED',
    title: '拆到最后一层，才算真的拿到。',
    image: 'parcels/soft-storm-box.webp',
    alt: '蓝色纸箱里的银色封口袋',
  },
]

const NOTES = [
  {
    index: '01',
    title: '不是更漂亮的列表',
    body: '第一屏按取件点分，点内再按紧急度排。最急的永远在最上面，而不是最新到的。',
    tone: 'white',
  },
  {
    index: '02',
    title: '不用记，也不用找',
    body: '码、格口或货架、还剩多久，在同一张卡上。到了地方，打开就是那一眼。',
    tone: 'pastel',
  },
  {
    index: '03',
    title: '搜索只过滤，不重排',
    body: '按取件码、取件点、快递公司过滤；码不带分隔符也搜得到。结果仍然是「哪个点有几件」。',
    tone: 'white',
  },
]

export default function Convenience() {
  return (
    <section className="poster-section" id="convenience" aria-labelledby="convenience-title">
      <div className="shell poster-board">
        <PosterHead
          className="span-8"
          no="01"
          en="EFFORTLESS"
          cn="件，自己进来。"
          lede="能收的自动收，收不到的补一条。到站件不必再散落在三个 App、四条短信和一张截图里。"
          tone="dark"
          id="convenience-title"
        >
          <HoloX />
        </PosterHead>

        <PosterCompanion
          className="span-4"
          no="01"
          kicker="IRIDESCENT / LOCAL-FIRST"
          big="01."
          tone="pastel"
        >
          <ul className="poster-index">
            {SOURCES.map((source) => (
              <li key={source.index}>
                <span>{source.index}</span>
                {source.title}
                <em>{source.tag}</em>
              </li>
            ))}
          </ul>
          <BarcodeFoot label={`CE-${VERSION} / ONE PARSER`} />
          <span className="poster-companion-note">ONE LOCAL PARSER / ZERO CLOUD</span>
        </PosterCompanion>

        <div className="span-12 route-flow" aria-hidden="true">
          <ViewportShaderGate className="route-flow__laser" rootMargin="180px 0px">
            <LaserFlow
              color="#ff5bbd"
              beamPosition={0.52}
              surfaceLevel={0.12}
              intensity={1.65}
              beamWidth={1.15}
              flare={1.25}
              spread={2.1}
              fog={0.32}
              dust={0.75}
              streaks={1.3}
              dots={0.55}
              pulse={1.2}
              speed={1.15}
              theme="dark"
            />
          </ViewportShaderGate>
          <div className="poster-route route-flow__labels">
            <span>INPUT</span>
            <i />
            <span>LOCAL PARSER</span>
            <i />
            <span>GROUPED BY POINT</span>
          </div>
        </div>

        <div className="span-12 source-grid">
          {SOURCES.map((source, index) => (
            <PosterCard
              key={source.index}
              className="source-card"
              tone={['dark', 'pastel', 'white'][index]}
              label={`${source.index} / INPUT`}
              no={`0${index + 1}`}
            >
              <span className="source-letter">{source.index}</span>
              <h3>{source.title}</h3>
              <p>{source.body}</p>
              <Tag tone={source.tone}>{source.tag}</Tag>
            </PosterCard>
          ))}
        </div>

        <PosterCard
          className="span-4 parser-card"
          tone="dark"
          label="LOCAL / PARSER"
          no="04"
          foot
        >
          <ElectricBorder
            className="parser-electric"
            color="#ff7ac8"
            glowColor="#7ee8fa"
            borderRadius={4}
            thickness={1.1}
            strands={2}
            arcs={1}
            intensity={0.72}
            glow={0.65}
            speed={0.8}
            chaos={0.45}
            spread={0.5}
            theme="dark"
          >
            <div className="poster-chip">
              <span className="poster-chip-grid" />
              <strong>本地</strong>
              <em>PARSER / {VERSION}</em>
            </div>
          </ElectricBorder>
          <p>
            在本机解析、归组、排序。
            <br />
            没有一条数据往外走。
          </p>
        </PosterCard>

        <div className="span-8 output-grid">
          {POINTS.map((point, index) => (
            <TiltedCard
              key={point.name}
              className="point-tilt"
              containerWidth="100%"
              containerHeight="230px"
              imageWidth="100%"
              imageHeight="230px"
              showTooltip={false}
              flipOnClick={false}
              rotateAmplitude={6}
              scaleOnHover={1.015}
              glare={0.18}
              shadow={0}
              radius={0}
              intro={false}
            >
              <article className={`point-poster point-poster--${point.tone}`}>
                <span className="point-poster__rail" aria-hidden="true" />
                <div className="point-poster__meta">
                  <span>POINT / 0{index + 1}</span>
                  <span>DEMO DATA</span>
                </div>
                <div className="point-poster__count">
                  {point.count}
                  <em>{point.unit}</em>
                </div>
                <h3>{point.name}</h3>
                <p className={`point-poster__state point-poster__state--${point.tone}`}>
                  <span className="state-dot" aria-hidden="true" />
                  {point.state}
                </p>
                <div className="point-poster__codes">
                  {point.codes.map((code) => (
                    <PointCode key={code} code={code} />
                  ))}
                  {point.extra ? <span>{point.extra}</span> : null}
                </div>
              </article>
            </TiltedCard>
          ))}
          <p className="poster-note">
            <span>?</span>
            认不出来的那一条，会放进「没认出来」等你确认——不猜一个点，也不假装它不存在。
          </p>
        </div>

        <section className="span-12 parcel-strip" aria-label="纸箱与封签材质研究">
          <div className="parcel-strip__head">
            <span>PARCEL / MATERIALS</span>
            <span>纸箱、胶带、内袋 / 取件之前</span>
          </div>
          <p className="parcel-strip__note">
            包裹由纸箱、胶带和内袋组成。App 不拆箱，也不替你跑腿；
            它只把「哪个点、几件、还剩多久」提前摆好。
          </p>
          <div className="parcel-grid">
            {PARCELS.map((parcel) => (
              <figure className="parcel-tile" key={parcel.id}>
                <TiltedCard
                  imageSrc={asset(parcel.image)}
                  altText={parcel.alt}
                  containerWidth="100%"
                  containerHeight="300px"
                  imageWidth="100%"
                  imageHeight="300px"
                  showTooltip={false}
                  flipOnClick={false}
                  rotateAmplitude={6}
                  scaleOnHover={1.02}
                  glare={0.24}
                  shadow={0.3}
                  radius={0}
                  intro={false}
                />
                <figcaption>
                  <span>{parcel.kicker}</span>
                  <strong>{parcel.title}</strong>
                  <em>{parcel.no} / 03</em>
                </figcaption>
              </figure>
            ))}
          </div>
        </section>

        <article className="poster-quote span-12">
          <span className="poster-quote__mark" aria-hidden="true">
            “
          </span>
          <p>
            翻三个 App、在短信里找那串码、到了柜子不知道第几格——
            <br />
            这些动作，本来就不该存在。
          </p>
        </article>

        <div className="span-12 note-grid">
          {NOTES.map((note) => (
            <PosterCard
              key={note.index}
              className="note-card"
              tone={note.tone}
              label={`NOTE / ${note.index}`}
              no={note.index}
            >
              <span className="note-card__index">{note.index}</span>
              <h3>{note.title}</h3>
              <p>{note.body}</p>
            </PosterCard>
          ))}
        </div>
      </div>
    </section>
  )
}
