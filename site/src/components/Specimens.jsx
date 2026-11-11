import { asset, VERSION } from '../data.js'
import ViewportShaderGate from './ViewportShaderGate.jsx'
import LaserFlow from './reactbits/LaserFlow/LaserFlow.jsx'
import HoloCard from './reactbits/HoloCard/HoloCard.jsx'
import TiltedCard from './reactbits/TiltedCard/TiltedCard.jsx'
import StatusMark from './reactbits/StatusMark/StatusMark.jsx'
import ElectricBorder from './reactbits/ElectricBorder/ElectricBorder.jsx'
import DecryptedText from './reactbits/DecryptedText/DecryptedText/DecryptedText.jsx'
import OnsiteConsole from './OnsiteConsole.jsx'
import { BarcodeFoot, HoloC, PosterCard, PosterCompanion, PosterHead } from './Poster.jsx'

const EVIDENCE = [
  {
    id: 'locker',
    image: 'crops/onsite-locker.webp',
    ratio: '622 / 450',
    no: '01',
    kicker: 'REAL CAPTURE / 柜机',
    note: '格口 A12 紧贴取件码，超时费用写在下面。',
    alt: '柜机现场模式真机截图局部：取件码 8-3-1024，格口 A12。',
  },
  {
    id: 'station',
    image: 'crops/onsite-station.webp',
    ratio: '622 / 472',
    no: '02',
    kicker: 'REAL CAPTURE / 驿站',
    note: '货架 3排12号，不是格口号。',
    alt: '菜鸟驿站现场模式真机截图局部：取件码 5-2-3391，货架 3排12号。',
  },
]

const FOILS = [
  {
    id: 'home-light',
    image: 'crops/home-groups-l.webp',
    preset: 'bursts',
    plate: '01',
    kicker: 'HOME / LIGHT',
    title: '首页 · 亮色',
    note: '按取件点分组，最急的在最上面。',
    alt: '亮色首页截图局部：丰巢快递柜 2 件标红，菜鸟驿站 3 件标绿。',
  },
  {
    id: 'home-dark',
    image: 'crops/home-groups-d.webp',
    preset: 'rainbow',
    plate: '02',
    kicker: 'HOME / DARK',
    title: '首页 · 暗色',
    note: '同一套信息，换一种光线。',
    alt: '暗色首页截图局部：与亮色相同的取件点分组和状态色。',
  },
  {
    id: 'detail',
    image: 'crops/detail-cards.webp',
    preset: 'shards',
    plate: '03',
    kicker: 'DETAIL / TRACE',
    title: '件详情',
    note: '商品、运单号、最新轨迹原文。',
    alt: '件详情截图局部：商品信息、取件码、格口、运单号与最新轨迹。',
  },
]

export default function Specimens() {
  return (
    <section className="poster-section specimens" id="screens" aria-labelledby="screens-title">
      <div className="shell poster-board">
        <PosterHead
          className="span-8"
          no="01.5"
          en="SPECIMENS"
          cn="真实界面，裁给你看。"
          lede="真机截图裁出的局部，状态栏裁掉，留下正在工作的那一块。中间这台是手绘的实时演示——点按切换柜机 / 驿站。"
          tone="dark"
          id="screens-title"
        >
          <HoloC />
        </PosterHead>

        <PosterCompanion
          className="span-4"
          no="01.5"
          kicker="CROPPED / REAL DEVICE"
          big="6"
          tone="pastel"
        >
          <ul className="poster-index">
            <li>
              <span>A</span>
              现场模式 ×2
              <em>柜机 / 驿站</em>
            </li>
            <li>
              <span>B</span>
              首页分组 ×2
              <em>亮色 / 暗色</em>
            </li>
            <li>
              <span>C</span>
              件详情 · 收工卡
              <em>轨迹 / 统计</em>
            </li>
          </ul>
          <BarcodeFoot label={`CE-${VERSION} / SIX CROPS`} />
          <span className="poster-companion-note">NO STATUS BAR / DEMO DATA</span>
        </PosterCompanion>

        <PosterCard
          className="span-12 specimen-stage"
          tone="dark"
          label="ONSITE / 现场模式"
          no="01.5-A"
          foot
        >
          <div className="specimen-stage__head">
            <h3>到了地方，打开就是那一眼。</h3>
            <DecryptedText
              text="ONSITE MODE / LIVE"
              animateOn="view"
              speed={28}
              maxIterations={8}
              characters="ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#/-:"
              className="specimen-stage__code"
            />
          </div>
          <ElectricBorder
            className="specimen-stage__frame"
            color="#7ee8fa"
            glowColor="#ff7ac8"
            borderRadius={8}
            thickness={1}
            strands={2}
            arcs={1}
            intensity={0.5}
            glow={0.4}
            speed={0.7}
            chaos={0.4}
            spread={0.5}
            theme="dark"
          >
            <div className="specimen-stage__body">
              <ViewportShaderGate className="specimen-stage__laser" rootMargin="240px 0px">
                <LaserFlow
                  color="#7ee8fa"
                  beamPosition={0.5}
                  surfaceLevel={0.1}
                  intensity={0.85}
                  beamWidth={1.5}
                  flare={0.7}
                  spread={2.6}
                  fog={0.18}
                  dust={0.35}
                  streaks={0.7}
                  dots={0.3}
                  pulse={0.8}
                  speed={0.65}
                  theme="dark"
                />
              </ViewportShaderGate>
              <span className="specimen-stage__spot" aria-hidden="true" />

              <div className="specimen-stage__grid">
                <div className="specimen-stage__hero">
                  <OnsiteConsole />
                </div>

                <div className="specimen-evidence">
                  <span className="specimen-evidence__rail" aria-hidden="true" />
                  {EVIDENCE.map((shot) => (
                    <figure className="specimen-evidence__item" key={shot.id}>
                      <span className="specimen-evidence__node" aria-hidden="true">
                        <StatusMark status="done" size={13} doneColor="#7ee8fa" strike={false} />
                      </span>
                      <div className="specimen-evidence__card" style={{ aspectRatio: shot.ratio }}>
                        <TiltedCard
                          containerWidth="100%"
                          containerHeight="100%"
                          imageWidth="100%"
                          imageHeight="100%"
                          showTooltip={false}
                          flipOnClick={false}
                          rotateAmplitude={5}
                          scaleOnHover={1.03}
                          glare={0.22}
                          shadow={0.35}
                          radius={12}
                          intro={false}
                        >
                          <img src={asset(shot.image)} alt={shot.alt} draggable={false} loading="lazy" decoding="async" />
                        </TiltedCard>
                      </div>
                      <figcaption className="specimen-evidence__caption">
                        <span>
                          {shot.kicker}
                          <em>{shot.no} / 02</em>
                        </span>
                        <p>{shot.note}</p>
                      </figcaption>
                    </figure>
                  ))}
                </div>
              </div>
            </div>
          </ElectricBorder>
        </PosterCard>

        <div className="span-12 specimen-grid">
          {FOILS.map((foil) => (
            <figure className="specimen-foil" key={foil.id}>
              <ViewportShaderGate
                className="specimen-foil__gate"
                rootMargin="260px 0px"
                fallback={
                  <img
                    className="specimen-foil__fallback"
                    src={asset(foil.image)}
                    alt={foil.alt}
                    loading="lazy"
                    decoding="async"
                  />
                }
              >
                <HoloCard
                  image={asset(foil.image)}
                  alt={foil.alt}
                  preset={foil.preset}
                  intensity={0.5}
                  edgeSparkle={0.5}
                  frame={5}
                  glare={0.2}
                  tiltMax={14}
                  hoverScale={1.04}
                  idle={false}
                  radius={12}
                  style={{ '--hc-w': '100%' }}
                />
              </ViewportShaderGate>
              <figcaption className="specimen-plate">
                <span>
                  {foil.kicker}
                  <em>{foil.plate} / 03</em>
                </span>
                <strong>{foil.title}</strong>
                <p>{foil.note}</p>
                <span className="specimen-plate__hint">HOVER / 悬停绽放全息</span>
              </figcaption>
            </figure>
          ))}
        </div>

        <p className="span-12 poster-note specimen-note">
          <span>?</span>
          状态栏、导航栏与无关留白已裁掉；留下的都是取件时真正会看的区域。商品与取件码均为演示数据。
        </p>

        <PosterCard
          className="span-12 specimen-done"
          tone="dark"
          label="ALL DONE / 收工"
          no="01.5-C"
          foot
        >
          <div className="specimen-done__grid">
            <div className="specimen-done__copy">
              <h3>取完了，就去干别的。</h3>
              <p>
                收工卡告诉你本月取了几件、下一站在哪。
                然后这一页就安静了——没有红点，没有「再逛逛」。
              </p>
              <StatusMark
                status="done"
                label="本月共取 6 件 · 全部完成"
                size={16}
                fontSize={13}
                doneColor="#00b578"
                strike={false}
                className="specimen-done__mark"
              />
            </div>
            <div className="specimen-done__card">
              <ViewportShaderGate
                rootMargin="260px 0px"
                fallback={
                  <img
                    className="specimen-foil__fallback"
                    src={asset('crops/alldone-card.webp')}
                    alt="全部取完的收工卡截图局部：本月共取 6 件，并提示下一个取件点。"
                    loading="lazy"
                    decoding="async"
                  />
                }
              >
                <HoloCard
                  image={asset('crops/alldone-card.webp')}
                  alt="全部取完的收工卡截图局部：本月共取 6 件，并提示下一个取件点。"
                  preset="glitter"
                  intensity={0.38}
                  edgeSparkle={0.45}
                  frame={5}
                  glare={0.18}
                  tiltMax={10}
                  hoverScale={1.03}
                  idle={false}
                  radius={12}
                  style={{ '--hc-w': '100%' }}
                />
              </ViewportShaderGate>
            </div>
          </div>
        </PosterCard>
      </div>
    </section>
  )
}