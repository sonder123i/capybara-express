import { useEffect, useId, useRef, useState } from 'react'
import { asset } from '../data.js'

export function ArrowRight({ className = '' }) {
  return (
    <svg className={`arrow ${className}`} viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4 12h15M13 6l6 6-6 6" fill="none" stroke="currentColor" strokeWidth="1.8" />
    </svg>
  )
}

export function ArrowDown({ className = '' }) {
  return (
    <svg className={`arrow ${className}`} viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 4v15M6 13l6 6 6-6" fill="none" stroke="currentColor" strokeWidth="1.8" />
    </svg>
  )
}

export function ArrowUpRight({ className = '' }) {
  return (
    <svg className={`arrow ${className}`} viewBox="0 0 24 24" aria-hidden="true">
      <path d="M7 17 17 7M8 7h9v9" fill="none" stroke="currentColor" strokeWidth="1.8" />
    </svg>
  )
}

export function BrandMark({ className = '' }) {
  return (
    <img
      className={`brand-mark ${className}`.trim()}
      src={asset('logo-mark.png')}
      alt=""
      width="128"
      height="128"
      draggable="false"
      aria-hidden="true"
    />
  )
}

const BARS = [
  2, 1, 3, 1, 1, 4, 2, 1, 1, 3, 2, 4, 1, 2, 1, 3, 1, 1, 4, 2, 3, 1, 2, 1, 1,
  4, 1, 2, 3, 1, 2, 4, 1, 1, 3, 2, 1, 4, 2, 1,
]

export function Barcode({ className = '', tall = false }) {
  return (
    <span className={`barcode ${tall ? 'barcode--tall' : ''} ${className}`} aria-hidden="true">
      {BARS.map((w, i) => (
        <i key={i} style={{ width: `${w}px` }} />
      ))}
    </span>
  )
}

export function Seal({
  ring = 'LOCAL PARSING · NO CLOUD · NO ACCOUNT · ',
  lines = ['本地', '解析'],
  className = '',
}) {
  const rawId = useId()
  const id = rawId.replace(/:/g, '')
  return (
    <svg
      className={`seal ${className}`}
      viewBox="0 0 200 200"
      role="img"
      aria-label={`${lines.join('')}印章`}
    >
      <defs>
        <path
          id={`seal-path-${id}`}
          d="M100,100 m-76,0 a76,76 0 1,1 152,0 a76,76 0 1,1 -152,0"
        />
        <filter id={`seal-rough-${id}`} x="-10%" y="-10%" width="120%" height="120%">
          <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="1" result="noise" />
          <feDisplacementMap in="SourceGraphic" in2="noise" scale="1.1" />
        </filter>
      </defs>
      <g filter={`url(#seal-rough-${id})`}>
        <circle cx="100" cy="100" r="88" fill="none" stroke="currentColor" strokeWidth="2.4" />
        <circle cx="100" cy="100" r="78" fill="none" stroke="currentColor" strokeWidth="1" />
        <text className="seal-ring" fill="currentColor">
          <textPath href={`#seal-path-${id}`} startOffset="0">
            {ring}
          </textPath>
        </text>
        <text className="seal-main" x="100" y="94" textAnchor="middle" fill="currentColor">
          {lines[0]}
        </text>
        <text className="seal-sub" x="100" y="126" textAnchor="middle" fill="currentColor">
          {lines[1]}
        </text>
      </g>
    </svg>
  )
}

export function Reveal({ as: Tag = 'div', delay = 0, className = '', children, style, ...rest }) {
  const ref = useRef(null)
  const [shown, setShown] = useState(false)

  useEffect(() => {
    const el = ref.current
    if (!el) return
    if (typeof IntersectionObserver === 'undefined') {
      setShown(true)
      return
    }
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      setShown(true)
      return
    }
    const io = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setShown(true)
          io.disconnect()
        }
      },
      { rootMargin: '0px 0px -6% 0px', threshold: 0.04 },
    )
    io.observe(el)
    return () => io.disconnect()
  }, [])

  return (
    <Tag
      ref={ref}
      className={`reveal ${shown ? 'is-in' : ''} ${className}`.trim()}
      style={{ '--reveal-delay': `${delay}ms`, ...(style || {}) }}
      {...rest}
    >
      {children}
    </Tag>
  )
}

export function SectionHead({ index, en, label, title, lede, dark = false, className = '' }) {
  return (
    <header className={`sec-head ${dark ? 'sec-head--dark' : ''} ${className}`.trim()}>
      <span className="sec-ghost" aria-hidden="true">
        {en}
      </span>
      <div className="sec-kicker">
        <span className="sec-index">{index}</span>
        <span className="sec-rule" aria-hidden="true" />
        <span className="sec-label">{label}</span>
        <span className="sec-en">{en}</span>
      </div>
      <h2 className="sec-title">{title}</h2>
      {lede ? <p className="sec-lede">{lede}</p> : null}
    </header>
  )
}

export function Tag({ children, tone = 'ink', className = '' }) {
  return <span className={`tag tag--${tone} ${className}`.trim()}>{children}</span>
}
