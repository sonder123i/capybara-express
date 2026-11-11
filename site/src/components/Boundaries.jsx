import { LINKS, VERSION } from '../data.js'
import { ArrowUpRight, Seal } from './Common.jsx'
import {
  BarcodeFoot,
  HoloX,
  PosterCard,
  PosterCompanion,
  PosterHead,
} from './Poster.jsx'
import GlitchText from './reactbits/GlitchText/GlitchText.jsx'
import ScrollStack, { ScrollStackItem } from './reactbits/ScrollStack/ScrollStack.jsx'
import Ferrofluid from './reactbits/Ferrofluid/Ferrofluid.jsx'
import ViewportShaderGate from './ViewportShaderGate.jsx'

const CLAUSES = [
  {
    no: '01',
    title: '不猜取件点',
    body: '认不出属于哪个点时，宁可放进「没认出来」等你确认，也不猜一个名字，生成一张看起来很像回事的卡片。',
    tag: '主动放弃',
  },
  {
    no: '02',
    title: '不自动合并',
    body: '同一个驿站在不同短信里写法不同，App 只提示「可能是同一个点？」。合并前给你看两边各几件；合并后 5 秒内可撤销，历史永久保留。',
    tag: '主动放弃',
  },
  {
    no: '03',
    title: '不假装补全漏件',
    body: '有些件不发短信、也不推通知，只在平台自己的「取快递」里露一下。任何声称「不登录、不读屏、还能自动补上所有平台的漏件」的方案都在骗你。',
    tag: '不骗你',
  },
  {
    no: '04',
    title: '不重现会话码',
    body: '有些码每 30 秒换一张，依赖对方服务器。离线状态下任何 App 都算不出下一张。这里只把它当参照存着，并明说它可能已经作废。',
    tag: '系统限制',
  },
  {
    no: '05',
    title: '不做后台自动同步',
    body: '同步只在你打开 App、点「立即同步」时发生。不悄悄耗电，也不瞒着你登录失效——那两件事都比多收一件更贵。',
    tag: '主动放弃',
  },
  {
    no: '06',
    title: '不做 iOS，也不做锁屏组件',
    body: 'iOS 读不到短信正文，也读不到别的 App 的通知；第三方锁屏组件 Android 5.0 就被移除了。取快递时，你本来就会解锁手机。',
    tag: '系统限制',
  },
]

export default function Boundaries() {
  return (
    <section
      className="poster-section poster-section--dark boundaries-poster"
      id="boundaries"
      aria-labelledby="boundaries-title"
    >
      <div className="shell poster-board">
        <PosterHead
          className="span-6"
          no="04"
          en="BOUNDARIES"
          cn="有些事，它不做。"
          lede="做不到的不吹。这份克制不是功能缺失，是安全感的一部分——也让你知道，哪些时候还得靠自己。"
          tone="white"
          id="boundaries-title"
        >
          <HoloX />
        </PosterHead>

        <PosterCompanion
          className="span-6"
          no="04"
          kicker="NO / GUESSING · MERGING · PATCHING"
          big={
            <GlitchText
              className="bnd-no"
              mode="slices"
              trigger="hover"
              intensity={0.32}
              speed={0.45}
              offset={6}
              shadowColors={['#ff7ac8', '#7ee8fa']}
              fontSize="inherit"
              fontWeight={600}
            >
              NO
            </GlitchText>
          }
          tone="pastel"
        >
          <ul className="poster-index">
            <li>
              <span>01</span>不猜取件点<em>NO GUESS</em>
            </li>
            <li>
              <span>02</span>不自动合并<em>NO MERGE</em>
            </li>
            <li>
              <span>03</span>不假装补全<em>NO PATCH</em>
            </li>
          </ul>
          <BarcodeFoot label={`BOUNDARIES / ${VERSION}`} />
        </PosterCompanion>

        <div className="span-4 bnd-left">
          <span className="bnd-left-label">04 / BOUNDARIES / STICKY</span>
          <PosterCard
            className="bnd-margin-card"
            tone="dark"
            label="MARGIN / 04"
            no="04"
          >
            <div className="bnd-fluid" aria-hidden="true">
              <ViewportShaderGate rootMargin="220px 0px">
                <Ferrofluid
                  colors={['#ff7ac8', '#7ee8fa', '#f7f8fa']}
                  speed={0.32}
                  scale={1.5}
                  spikeHeight={0.9}
                  sharpness={2.8}
                  turbulence={0.8}
                  fluidity={0.55}
                  tilt={48}
                  magnetism={0.8}
                  brightness={0.9}
                  opacity={0.36}
                  mouseInteraction
                  intro
                  transparent
                />
              </ViewportShaderGate>
            </div>
            <Seal
              className="bnd-seal"
              ring="BOUNDARIES · NO GUESSING · NO PATCHING · "
              lines={['不猜', '不补']}
            />
            <blockquote>
              这份清单
              <br />
              不是免责声明。
              <br />
              <em>有些是主动放弃，有些是系统不给。</em>
            </blockquote>
            <p>
              错合并会让现场少显示一个码，不合并只是多一张卡片。后果不对称，所以宁可多一张卡，把判断交给你。
            </p>
            <a className="text-link" href={LINKS.issues} target="_blank" rel="noreferrer">
              发现哪条对不上？开 issue
              <ArrowUpRight />
            </a>
          </PosterCard>
          <p className="bnd-left-note">
            v{VERSION} · 6 条 / 1 份 · 这条清单也是 issue 过滤器
          </p>
        </div>

        <ScrollStack
          className="span-8 bnd-stack"
          useWindowScroll
          itemDistance={54}
          itemStackDistance={14}
          stackPosition="15%"
          baseScale={0.94}
          itemScale={0.03}
          tiltAmount={1.6}
          rotationAmount={0.35}
          dimAmount={0.06}
          blurAmount={0}
          holdDistance={130}
          smoothScroll={false}
        >
          {CLAUSES.map((clause, index) => (
            <ScrollStackItem
              key={clause.no}
              itemClassName={`bnd-stack-card ${index % 2 === 0 ? 'is-light' : 'is-dark'}`}
            >
              <div className="bnd-card-head">
                <span>CE-BND-{clause.no}</span>
                <em className={clause.tag === '系统限制' ? 'is-system' : ''}>{clause.tag}</em>
              </div>
              <h3>
                <span>{clause.no}</span>
                {clause.title}
              </h3>
              <p>{clause.body}</p>
              <div className="bnd-card-foot">
                <span>NO / {clause.no}</span>
                <span>BOUNDARIES</span>
              </div>
            </ScrollStackItem>
          ))}
        </ScrollStack>
      </div>
    </section>
  )
}
