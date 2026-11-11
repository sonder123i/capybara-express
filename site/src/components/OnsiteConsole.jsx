import { useEffect, useRef, useState } from 'react'
import TiltedCard from './reactbits/TiltedCard/TiltedCard.jsx'
import SplitFlapText from './reactbits/SplitFlapText/SplitFlapText.jsx'
import StatusMark from './reactbits/StatusMark/StatusMark.jsx'

const pad = (n) => String(n).padStart(2, '0')
const clock = (total) => {
  const s = Math.max(0, Math.abs(Math.round(total)))
  return `${pad(Math.floor(s / 3600))}:${pad(Math.floor((s % 3600) / 60))}:${pad(s % 60)}`
}

const MODES = {
  locker: {
    carrier: '顺丰速运',
    code: '8-3-1024',
    cellLabel: '格口',
    cell: 'A12',
    tone: 'risk',
    tag: '已超时',
    base: 8073,
    dir: 1,
    fee: '0.5 元 / 12 小时 · 上限 3 元',
  },
  station: {
    carrier: '中通快递',
    code: '5-2-3391',
    cellLabel: '货架',
    cell: '3排12号',
    tone: 'safe',
    tag: '距关门',
    base: 27730,
    dir: -1,
    fee: '关门前 90 分钟提醒',
  },
}

export default function OnsiteConsole() {
  const [mode, setMode] = useState('locker')
  const [t, setT] = useState(0)
  const [copied, setCopied] = useState(false)
  const copyTimer = useRef(0)
  const m = MODES[mode]

  useEffect(() => {
    const id = window.setInterval(() => setT((v) => v + 1), 1000)
    return () => window.clearInterval(id)
  }, [])

  useEffect(() => () => window.clearTimeout(copyTimer.current), [])

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(m.code)
      setCopied(true)
      window.clearTimeout(copyTimer.current)
      copyTimer.current = window.setTimeout(() => setCopied(false), 1600)
    } catch {
      setCopied(false)
    }
  }

  const switchMode = (next) => {
    if (next === mode) return
    setMode(next)
    setCopied(false)
  }

  return (
    <div className="onsite">
      <span className="onsite__glow" aria-hidden="true" />
      <TiltedCard
        className="onsite__tilt"
        containerWidth="100%"
        containerHeight="100%"
        imageWidth="100%"
        imageHeight="100%"
        style={{ height: 'var(--onsite-h, 470px)' }}
        showTooltip={false}
        flipOnClick={false}
        rotateAmplitude={6}
        scaleOnHover={1.02}
        glare={0.18}
        shadow={0.4}
        radius={24}
        intro={false}
      >
        <div className={`onsite-panel onsite-panel--${m.tone}`} aria-label="现场模式实时演示（演示数据）">
          <span className="onsite-panel__scan" aria-hidden="true" />
          <span className="onsite-panel__grid" aria-hidden="true" />

          <div className="onsite-panel__head">
            <span className="onsite-panel__carrier">{m.carrier}</span>
            <span className="onsite-panel__live">
              <i aria-hidden="true" />
              现场模式
            </span>
          </div>

          <div className="onsite-switch" role="tablist" aria-label="取件点类型">
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'locker'}
              className={`onsite-switch__btn ${mode === 'locker' ? 'is-active' : ''}`}
              onClick={() => switchMode('locker')}
            >
              柜机
              <em>LOCKER</em>
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'station'}
              className={`onsite-switch__btn ${mode === 'station' ? 'is-active' : ''}`}
              onClick={() => switchMode('station')}
            >
              驿站
              <em>STATION</em>
            </button>
          </div>

          <div className="onsite-code">
            <span className="onsite-code__label">取件码 / PICKUP CODE</span>
            <div className="onsite-code__flap" key={mode}>
              <SplitFlapText
                text={m.code}
                padTo={m.code.length}
                loop={false}
                flipDuration={0.16}
                stagger={0.05}
                flipsPerChar={6}
                fontSize={30}
                gap={3}
                tileRadius={4}
                tileColor="#101014"
                textColor="#f7f8fa"
                depth={0.3}
                glare={0.1}
                charset="0123456789-"
              />
            </div>
            <button type="button" className={`onsite-copy ${copied ? 'is-copied' : ''}`} onClick={copy}>
              {copied ? '已复制 ✓' : '复制取件码'}
            </button>
          </div>

          <div className={`onsite-cell onsite-cell--${m.tone}`}>
            <span>{m.cellLabel}</span>
            <strong key={mode}>{m.cell}</strong>
          </div>

          <div className={`onsite-timer onsite-timer--${m.tone}`}>
            <StatusMark
              status={m.tone === 'risk' ? 'error' : 'done'}
              size={15}
              errorColor="#ff3141"
              doneColor="#00b578"
              strike={false}
            />
            <span className="onsite-timer__clock">
              {m.tag} {clock(m.base + m.dir * t)}
            </span>
            <em>{m.fee}</em>
          </div>

          <span className="onsite-panel__hint">长按可复制 · 演示数据 · 与真机一致</span>
        </div>
      </TiltedCard>
      <span className="onsite__caption">HAND-BUILT / 实时演示 · 点按切换柜机 / 驿站</span>
    </div>
  )
}