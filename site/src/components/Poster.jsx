import { VERSION } from '../data.js'
import { Barcode } from './Common.jsx'

export function PosterMeta({ left, right }) {
  return (
    <div className="poster-meta">
      <span>{left}</span>
      <span>{right}</span>
    </div>
  )
}

export function PosterHead({ no, en, cn, lede, tone = 'dark', children, className = '', id }) {
  return (
    <article className={`poster-card poster-head poster-card--${tone} ${className}`.trim()}>
      <PosterMeta left={`${no} / ${en}`} right="CAPYBARA EXPRESS" />
      <div className="poster-head-copy">
        <h2 className="poster-head-title" id={id}>
          <span className="poster-en">{en}</span>
          <span className="poster-cn">{cn}</span>
        </h2>
        <p className="poster-lede">{lede}</p>
      </div>
      {children}
      <div className="poster-foot">
        <span>{VERSION} / LOCAL-FIRST</span>
        <span>{no} / 05</span>
      </div>
    </article>
  )
}

export function PosterCompanion({ no, kicker, big, children, tone = 'pastel', className = '' }) {
  return (
    <article className={`poster-card poster-companion poster-card--${tone} ${className}`.trim()}>
      <PosterMeta left={kicker} right={no} />
      <div className="poster-big">{big}</div>
      <div className="poster-companion-body">{children}</div>
      <div className="poster-foot">
        <span>SPECIMEN / {no}</span>
        <span>CE-{VERSION}</span>
      </div>
    </article>
  )
}

export function PosterCard({
  label,
  no,
  children,
  tone = 'white',
  className = '',
  foot = false,
  ...rest
}) {
  return (
    <article className={`poster-card poster-card--${tone} ${className}`.trim()} {...rest}>
      <PosterMeta left={label} right={no} />
      {children}
      {foot ? (
        <div className="poster-foot">
          <span>CE-{VERSION}</span>
          <span>{no}</span>
        </div>
      ) : null}
    </article>
  )
}

export function HoloX({ className = '' }) {
  return (
    <span className={`holo-x ${className}`.trim()} aria-hidden="true">
      <i />
      <i />
    </span>
  )
}

export function HoloC({ className = '' }) {
  return <span className={`holo-c ${className}`.trim()} aria-hidden="true" />
}

export function HoloRing({ className = '' }) {
  return <span className={`holo-ring ${className}`.trim()} aria-hidden="true" />
}

export function BarcodeFoot({ label = `CE-${VERSION}` }) {
  return (
    <div className="poster-barcode-foot" aria-hidden="true">
      <Barcode />
      <span>{label}</span>
    </div>
  )
}
