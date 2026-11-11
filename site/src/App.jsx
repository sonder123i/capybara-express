import { useEffect, useState, useLayoutEffect } from 'react'
import { initSiteMotion } from './lib/motion.js'
import Iridescence from './components/reactbits/Iridescence.jsx'
import Rail from './components/Rail.jsx'
import Hero from './components/Hero.jsx'
import MarqueeBand from './components/MarqueeBand.jsx'
import ClickSpark from './components/reactbits/ClickSpark.jsx'
import Convenience from './components/Convenience.jsx'
import Specimens from './components/Specimens.jsx'
import Security from './components/Security.jsx'
import Interstitial from './components/Interstitial.jsx'
import Calm from './components/Calm.jsx'
import Boundaries from './components/Boundaries.jsx'
import Install from './components/Install.jsx'
import SiteFooter from './components/SiteFooter.jsx'
import { NAV } from './data.js'

export default function App() {
  const [active, setActive] = useState('')
  const [compact, setCompact] = useState(
    () => typeof window !== 'undefined' && window.matchMedia('(max-width: 680px)').matches,
  )

  useEffect(() => {
    const mq = window.matchMedia('(max-width: 680px)')
    const onChange = (e) => setCompact(e.matches)
    mq.addEventListener('change', onChange)
    return () => mq.removeEventListener('change', onChange)
  }, [])

  useLayoutEffect(() => initSiteMotion(), [])

  useEffect(() => {
    // React renders after the browser's first hash pass; restore deep links.
    if (window.location.hash) {
      const target = document.getElementById(window.location.hash.slice(1))
      if (target) window.setTimeout(() => target.scrollIntoView({ behavior: 'auto' }), 60)
    }

  }, [])

  useEffect(() => {
    let frame = 0
    const measure = () => {
      frame = 0
      let current = ''
      const line = window.innerHeight * 0.36
      for (const item of NAV) {
        const el = document.getElementById(item.id)
        if (el && el.getBoundingClientRect().top <= line) current = item.id
      }
      setActive(current)
    }
    const onScroll = () => {
      if (!frame) frame = window.requestAnimationFrame(measure)
    }
    measure()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll)
    return () => {
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
      if (frame) window.cancelAnimationFrame(frame)
    }
  }, [])

  return (
    <>
      <div className="opening" aria-hidden="true">
        <div className="opening-inner">
          <div className="opening-line"><span>CAPYBARA</span></div>
          <div className="opening-line"><span>EXPRESS</span></div>
          <div className="opening-sub"><span>卡皮巴拉快递助手 · LOCAL-FIRST</span></div>
          <div className="opening-rule"><i /></div>
        </div>
      </div>
      <div className="site-field" aria-hidden="true">
        <Iridescence
          color={[1, 1, 1]}
          speed={0.55}
          amplitude={0.08}
          mouseReact
          scale={1.15}
          detail={compact ? 5 : 7}
          rotation={-8}
          warp={0.9}
          hueShift={-18}
          saturation={1.12}
          brightness={1.28}
          contrast={1.06}
          stir={0.35}
          sheen={0.28}
          clickSwirl
          grain={0.12}
          fade={0.42}
          opacity={0.78}
          resolution={compact ? 0.4 : 0.65}
        />
      </div>
      <div className="site-field-veil" aria-hidden="true" />
      <a className="skip-link" href="#main">
        跳到正文
      </a>
      <div id="top" />
      <Rail active={active} />
      <ClickSpark
        style={{ zIndex: 2 }}
        sparkColor="#c21f72"
        sparkSize={10}
        sparkRadius={26}
        sparkCount={9}
        sparkWidth={1.5}
        duration={520}
        glow={0.35}
        randomness={0.25}
        layer="front"
      >
        <main id="main">
        <Hero />
        <MarqueeBand />
        <Convenience />
        <Specimens />
        <Security />
        <Interstitial />
        <Calm />
        <Boundaries />
        <Install />
        </main>
      </ClickSpark>
      <SiteFooter />
    </>
  )
}
