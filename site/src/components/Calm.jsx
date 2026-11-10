import { useState } from 'react'
import {
  BarcodeFoot,
  HoloRing,
  PosterCard,
  PosterCompanion,
  PosterHead,
} from './Poster.jsx'
import ClockDemo from './ClockDemo.jsx'
import CometDial from './reactbits/CometDial/CometDial.jsx'

const NOTES = [
  {
    index: '01',
    title: '只有三种颜色',
    body: '安全、临期、过期风险。颜色只回答「现在该不该去」，紧迫度靠倒计时数字一直在跳，不靠再加一档颜色。',
    tone: 'white',
  },
  {
    index: '02',
    title: '8 秒，界面自己退场',
    body: '举着手机对格口时，顶栏、分组、「已取」和复制提示一起消失，只留码、格口和倒计时。碰一下才回来。',
    tone: 'pastel',
  },
  {
    index: '03',
    title: '取完最后一件，下一站已经在了',
    body: '收工卡直接说下一个点是哪里、还有几件、还剩多久。没有别的点，就说今天没有了，不拿一张空卡片糊弄你。',
    tone: 'white',
  },
]

export default function Calm() {
  const [hours, setHours] = useState(6.5)

  return (
    <section className="poster-section" id="calm" aria-labelledby="calm-title">
      <div className="shell poster-board">
        <PosterHead
          className="span-5"
          no="03"
          en="CALM"
          cn="打开，就知道。"
          lede="第一屏不是一长串包裹，是「哪个点、几件、最急的还剩多久」。然后你就可以把这件事放下。"
          tone="dark"
          id="calm-title"
        >
          <HoloRing />
        </PosterHead>

        <PosterCompanion
          className="span-7 calm-companion"
          no="03"
          kicker="18 H / FREE HOLD"
          big="18"
          tone="pastel"
        >
          <p className="poster-time-copy">
            18H FREE · 丰巢默认 · 0.5元/12h · 封顶3元
          </p>
          <div className="calm-dial">
            <CometDial
              value={hours}
              onChange={setHours}
              min={0}
              max={24}
              step={0.25}
              unit="h"
              label="CUSTODY"
              size={238}
              sweep={300}
              thickness={5}
              accent="#c21f72"
              ink="#08090b"
              cometReach={160}
              speed={22}
            />
            <span className="calm-dial__caption">拖动表盘 · 与下方时钟联动</span>
          </div>
          <div className="poster-ruler" aria-hidden="true">
            {Array.from({ length: 36 }, (_, index) => (
              <i key={index} />
            ))}
          </div>
          <BarcodeFoot label="0.5 / 12H · CAP 3 · DEFAULT" />
        </PosterCompanion>

        <div className="span-12 poster-card poster-card--dark clock-poster">
          <div className="poster-meta">
            <span>03 / CUSTODY CLOCK</span>
            <span>LIVE DEMO</span>
          </div>
          <ClockDemo hours={hours} onHoursChange={setHours} />
        </div>

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

        <article className="poster-calim-signoff span-12">
          <span aria-hidden="true">“</span>
          你不需要记住任何事。打开它，就知道。
        </article>
      </div>
    </section>
  )
}
