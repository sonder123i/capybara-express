import { useId, useState } from 'react'
import { ArrowRight } from './Common.jsx'
import LiquidChrome from './LiquidChrome.jsx'
import TiltedCard from './reactbits/TiltedCard/TiltedCard.jsx'

const FREE_HOURS = 18
const RULER_HOURS = 24
const DUE_REMAINING = 6

const STATES = {
  safe: {
    label: '安全',
    note: '离收费还早。这一件不用惦记。',
    color: 'var(--safe-on-dark)',
  },
  due: {
    label: '临期',
    note: '免费时间快用完。该顺路了。',
    color: 'var(--amber-on-dark)',
  },
  risk: {
    label: '过期风险',
    note: '已开始计费。越早拿越省。',
    color: 'var(--risk-on-dark)',
  },
}

function formatClock(minutes) {
  const abs = Math.abs(Math.round(minutes))
  const hh = Math.floor(abs / 60)
  const mm = abs % 60
  return `${String(hh).padStart(2, '0')}:${String(mm).padStart(2, '0')}`
}

function formatElapsed(hours) {
  const total = Math.round(hours * 60)
  const hh = Math.floor(total / 60)
  const mm = total % 60
  if (hh <= 0) return `${mm} 分钟`
  return mm === 0 ? `${hh} 小时` : `${hh} 小时 ${mm} 分`
}

export default function ClockDemo({ hours, onHoursChange }) {
  const [internal, setInternal] = useState(6.5)
  const elapsed = typeof hours === 'number' ? hours : internal
  const setElapsed = (next) => {
    if (onHoursChange) onHoursChange(next)
    else setInternal(next)
  }
  const sliderId = useId()

  const elapsedMinutes = Math.round(elapsed * 60)
  const remainingMinutes = FREE_HOURS * 60 - elapsedMinutes
  const state = remainingMinutes <= 0 ? 'risk' : remainingMinutes <= DUE_REMAINING * 60 ? 'due' : 'safe'
  const meta = STATES[state]
  const overdueMinutes = Math.max(0, -remainingMinutes)
  const fee = Math.min(3, Math.ceil(overdueMinutes / (12 * 60)) * 0.5)
  const progress = Math.min(1, Math.max(0, elapsed / RULER_HOURS))
  const timeLabel =
    remainingMinutes > 0
      ? `还剩 ${formatClock(remainingMinutes)}`
      : remainingMinutes === 0
        ? '免费时间刚好用完'
        : `已超时 ${formatClock(overdueMinutes)}`

  const presets = [
    { value: 0, label: '刚到站' },
    { value: 12, label: '还剩 6 小时' },
    { value: 20, label: '超时 2 小时' },
  ]

  return (
    <div className="clock-demo">
      <div className="clock-chrome" aria-hidden="true">
        <LiquidChrome
          color="#ffffff"
          speed={0.08}
          amplitude={0.22}
          detail={3}
          thickness={1}
          interactive={false}
          frequencyX={2.4}
          frequencyY={2.4}
          depth={0.8}
          roughness={0.36}
          exposure={0.95}
          rippleStrength={0.4}
          rippleSize={1}
          tilt={0}
          intro
          lightMode={false}
          maxDevicePixelRatio={1.5}
        />
      </div>
      <div className="clock-head">
        <div>
          <span className="clock-kicker">保管时钟 / CUSTODY CLOCK</span>
          <h3>一件快递的时间，长这样。</h3>
        </div>
        <span className="demo-tag demo-tag--light">交互演示 · 演示数据</span>
      </div>

      <div className="clock-main">
        <div className="clock-readout">
          <div className="clock-time-label">{remainingMinutes > 0 ? '距离开始收费' : '免费保管已结束'}</div>
          <div className={`clock-time ${state}`} aria-hidden="true">
            {remainingMinutes > 0 ? formatClock(remainingMinutes) : remainingMinutes === 0 ? '00:00' : `−${formatClock(overdueMinutes)}`}
          </div>
          <div className="clock-state" style={{ '--state-color': meta.color }}>
            <span className="state-dot" aria-hidden="true" />
            <strong>{meta.label}</strong>
            <span>{meta.note}</span>
          </div>

          <div className="clock-ruler">
            <div className="clock-ruler-track" aria-hidden="true">
              <span className="clock-ruler-safe" />
              <span className="clock-ruler-due" />
              <span className="clock-ruler-risk" />
              <span className="clock-ruler-fill safe" style={{ width: `${Math.min(progress, 0.5) * 100}%` }} />
              <span
                className="clock-ruler-fill due"
                style={{ left: '50%', width: `${Math.max(0, Math.min(progress, 0.75) - 0.5) * 100}%` }}
              />
              <span
                className="clock-ruler-fill risk"
                style={{ left: '75%', width: `${Math.max(0, progress - 0.75) * 100}%` }}
              />
              <span className="clock-ruler-marker" style={{ left: '50%' }}>
                <em>12h</em>
              </span>
              <span className="clock-ruler-marker" style={{ left: '75%' }}>
                <em>18h</em>
              </span>
            </div>
            <input
              id={sliderId}
              className="clock-range"
              type="range"
              min="0"
              max={RULER_HOURS}
              step="0.25"
              value={elapsed}
              onChange={(event) => setElapsed(Number(event.target.value))}
              aria-label="从到站开始经过的时间"
              aria-valuetext={`到站后 ${formatElapsed(elapsed)}，${meta.label}，${timeLabel}`}
            />
            <div className="clock-scale" aria-hidden="true">
              <span style={{ left: '0%' }}>到站 0h</span>
              <span style={{ left: '50%' }}>临期线 12h</span>
              <span style={{ left: '75%' }}>计费 18h</span>
              <span style={{ left: '100%' }}>24h</span>
            </div>
          </div>

          <div className="clock-presets">
            {presets.map((preset) => (
              <button
                key={preset.label}
                type="button"
                className={Math.abs(elapsed - preset.value) < 0.01 ? 'is-active' : ''}
                onClick={() => setElapsed(preset.value)}
              >
                {preset.label}
              </button>
            ))}
          </div>
        </div>

        <div className="clock-side">
          <TiltedCard
            className="clock-specimen-tilt"
            containerWidth="100%"
            containerHeight="210px"
            imageWidth="100%"
            imageHeight="210px"
            showTooltip={false}
            flipOnClick={false}
            rotateAmplitude={7}
            scaleOnHover={1.01}
            glare={0.2}
            shadow={0.35}
            radius={0}
            intro={false}
          >
          <article className="clock-specimen">
            <div className="specimen-top">
              <div>
                <span className="specimen-kicker">丰巢快递柜（3号楼下）</span>
                <h4>日式豚骨叉烧拉面 3袋装</h4>
              </div>
              <span className="specimen-count">1 / 2 件</span>
            </div>
            <div className="specimen-code-row">
              <div>
                <span className="field-label">取件码</span>
                <strong className="specimen-code">8-3-1024</strong>
              </div>
              <div>
                <span className="field-label">格口</span>
                <strong className="specimen-compartment">A12</strong>
              </div>
            </div>
            <div className={`specimen-status ${state}`}>
              <span className="state-dot" aria-hidden="true" />
              <span>{timeLabel}</span>
              <strong>{state === 'risk' ? (overdueMinutes === 0 ? '即将计费' : `当前费用 ¥${fee.toFixed(2)}`) : '免费保管中'}</strong>
            </div>
            <p className="specimen-foot">超时 0.5 元 / 12 小时 · 封顶 3 元</p>
          </article>
          </TiltedCard>

          <div className="next-stop">
            <span className="next-stop-label">取完这里</span>
            <ArrowRight />
            <div>
              <strong>菜鸟驿站（阳光花园小区东门）</strong>
              <span>3 件 · 都还安全</span>
            </div>
          </div>
        </div>
      </div>

      <div className="clock-foot">
        <p>
          按丰巢默认规则演示：18 小时免费保管，超时 0.5 元 / 12 小时，封顶 3 元。
          驿站按关门时间算，没有这 18 小时；临期阈值在设置里可以改。
        </p>
        <span className="clock-foot-note">默认阈值 · 可在设置调整</span>
      </div>
    </div>
  )
}
