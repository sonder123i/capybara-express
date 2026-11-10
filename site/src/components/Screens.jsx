import { useEffect, useRef, useState } from 'react'
import { asset, LINKS, SCREENS } from '../data.js'
import { ArrowRight, ArrowUpRight, Reveal, SectionHead } from './Common.jsx'

const LAST = SCREENS.length - 1

export default function Screens() {
  const [open, setOpen] = useState(null)
  const closeRef = useRef(null)
  const lastTrigger = useRef(null)

  const close = () => setOpen(null)
  const next = () => setOpen((value) => (value === null ? null : (value + 1) % SCREENS.length))
  const prev = () =>
    setOpen((value) => (value === null ? null : (value - 1 + SCREENS.length) % SCREENS.length))

  useEffect(() => {
    if (open === null) return
    lastTrigger.current = document.activeElement
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    closeRef.current?.focus()
    return () => {
      document.body.style.overflow = previousOverflow
      lastTrigger.current?.focus?.()
    }
  }, [open === null])

  useEffect(() => {
    if (open === null) return
    const onKey = (event) => {
      if (event.key === 'Escape') close()
      if (event.key === 'ArrowRight') next()
      if (event.key === 'ArrowLeft') prev()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open])

  const current = open === null ? null : SCREENS[open]

  return (
    <section className="band band--paper screens" id="screens" aria-labelledby="screens-title">
      <div className="shell">
        <Reveal>
          <SectionHead
            index="05"
            en="PROOF"
            label="画面"
            title={
              <span id="screens-title">
                不是
                <br />
                设计稿。
              </span>
            }
            lede="这些是 release 包在真机上跑出来的画面。图里的件是演示数据。"
          />
        </Reveal>

        <Reveal className="sheet-meta" delay={60}>
          <span>PLATE 01—08</span>
          <span>700 × 1244</span>
          <span>真机截屏 / RELEASE 0.4.6</span>
          <span className="sheet-meta-hint">点击放大 ↗</span>
        </Reveal>

        <div className="screen-sheet">
          {SCREENS.map((screen, index) => (
            <Reveal
              as="figure"
              className="screen-plate"
              key={screen.file}
              delay={(index % 4) * 50}
            >
              <button
                type="button"
                className="screen-open"
                onClick={(event) => {
                  lastTrigger.current = event.currentTarget
                  setOpen(index)
                }}
                aria-label={`查看大图：${screen.title}，${screen.note}`}
              >
                <img
                  src={asset(screen.file)}
                  alt={screen.alt}
                  width="700"
                  height="1244"
                  loading="lazy"
                  decoding="async"
                />
                <span className="screen-hover" aria-hidden="true">
                  查看大图
                  <ArrowUpRight />
                </span>
              </button>
              <figcaption>
                <span className="screen-plate-no">{screen.plate}</span>
                <span className="screen-plate-title">{screen.title}</span>
                <span className="screen-plate-note">{screen.note}</span>
              </figcaption>
            </Reveal>
          ))}
        </div>

        <Reveal as="p" className="sheet-foot" delay={80}>
          <span className="sheet-foot-mark" aria-hidden="true">
            ✳
          </span>
          所有画面来自同一个 release 包。没有渲染图，没有补画，也没有把界面 P 得更整齐。
          <a href={LINKS.releaseTag} target="_blank" rel="noreferrer">
            对照版本
            <ArrowUpRight />
          </a>
        </Reveal>
      </div>

      {current ? (
        <div className="lightbox" role="dialog" aria-modal="true" aria-labelledby="lightbox-title" onClick={close}>
          <div className="lightbox-inner" onClick={(event) => event.stopPropagation()}>
            <div className="lightbox-bar">
              <span>
                PLATE {current.plate} / 08
              </span>
              <div className="lightbox-actions">
                <button type="button" onClick={prev} aria-label="上一张">
                  <ArrowRight className="flip" />
                </button>
                <button type="button" onClick={next} aria-label="下一张">
                  <ArrowRight />
                </button>
                <button ref={closeRef} type="button" className="lightbox-close" onClick={close} aria-label="关闭大图">
                  ×
                </button>
              </div>
            </div>
            <div className="lightbox-figure">
              <img src={asset(current.file)} alt={current.alt} width="700" height="1244" />
            </div>
            <div className="lightbox-caption" id="lightbox-title">
              <strong>{current.title}</strong>
              <span>{current.note}</span>
              <em>
                {current.plate} / {String(LAST + 1).padStart(2, '0')}
              </em>
            </div>
          </div>
        </div>
      ) : null}
    </section>
  )
}
