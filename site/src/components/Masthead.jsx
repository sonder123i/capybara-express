import { useEffect, useState } from 'react'
import { LINKS, NAV, VERSION } from '../data.js'
import { ArrowRight, ArrowUpRight, BrandMark } from './Common.jsx'

export default function Masthead({ active, progress }) {
  const [open, setOpen] = useState(false)
  const [scrolled, setScrolled] = useState(false)

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 24)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  useEffect(() => {
    if (!open) return
    const onKey = (event) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('keydown', onKey)
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = previous
    }
  }, [open])

  return (
    <>
      <header className={`masthead ${scrolled ? 'is-scrolled' : ''}`}>
        <div className="masthead-inner">
          <a className="brand" href="#top" aria-label="卡皮巴拉快递助手，回到顶部">
            <BrandMark />
            <span className="brand-type">
              <strong>卡皮巴拉</strong>
              <span>快递助手</span>
            </span>
          </a>

          <nav className="main-nav" aria-label="页面章节">
            {NAV.map((item) => (
              <a
                key={item.id}
                className={`nav-link ${active === item.id ? 'is-active' : ''}`}
                href={`#${item.id}`}
              >
                <span className="nav-index">{item.index}</span>
                {item.label}
              </a>
            ))}
          </nav>

          <div className="masthead-actions">
            <a className="version-link" href={LINKS.releaseTag} target="_blank" rel="noreferrer">
              v{VERSION}
              <ArrowUpRight />
            </a>
            <a className="btn btn--ink btn--sm masthead-download" href={LINKS.apk}>
              下载 APK
              <ArrowRight />
            </a>
            <button
              className="menu-toggle"
              type="button"
              aria-expanded={open}
              aria-controls="mobile-menu"
              onClick={() => setOpen((value) => !value)}
            >
              <span className="menu-toggle-label">{open ? '收起' : '目录'}</span>
              <span className={`menu-lines ${open ? 'is-open' : ''}`} aria-hidden="true">
                <i />
                <i />
              </span>
            </button>
          </div>
        </div>
        <div className="read-progress" aria-hidden="true">
          <span style={{ transform: `scaleX(${progress})` }} />
        </div>
      </header>

      <div id="mobile-menu" className={`mobile-menu ${open ? 'is-open' : ''}`}>
        <div className="mobile-menu-inner">
          <p className="mobile-menu-kicker">
            目录 <span>/ INDEX</span>
          </p>
          <nav aria-label="移动端章节导航">
            {NAV.map((item) => (
              <a key={item.id} href={`#${item.id}`} onClick={() => setOpen(false)}>
                <span>{item.index}</span>
                <strong>{item.label}</strong>
                <em>{item.en}</em>
              </a>
            ))}
          </nav>
          <div className="mobile-menu-foot">
            <a className="btn btn--ink" href={LINKS.apk} onClick={() => setOpen(false)}>
              下载 APK · 0.4.9
              <ArrowRight />
            </a>
            <a href={LINKS.github} target="_blank" rel="noreferrer">
              源码 ↗ GitHub
            </a>
          </div>
        </div>
      </div>
    </>
  )
}
