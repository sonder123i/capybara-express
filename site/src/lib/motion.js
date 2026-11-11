import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

gsap.registerPlugin(ScrollTrigger)
gsap.ticker.lagSmoothing(500, 33)

const prefersReduced = () =>
  typeof window !== 'undefined' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches

const setMask = (el) => {
  if (!el) return
  gsap.set(el, { yPercent: 132, scaleY: 1.42, transformOrigin: '50% 100%' })
}

const setFadeUp = (el, y = 30) => {
  if (!el) return
  gsap.set(el, { y, autoAlpha: 0 })
}

/*
 * 全站动效编排：
 * - 开场：全屏 opening（遮罩揭开的字标 + 进度线），上滑退场
 * - Hero：标题逐行遮罩揭开 + 压缩归位，丝带/玻璃/条码依次进场
 * - 滚动：每个 section 大标题先进场，卡片 stagger，图片轻微视差
 * - 全部 transform / clip-path / autoAlpha，once 触发，scrub 只用在 4 处
 */
export function initSiteMotion() {
  if (prefersReduced()) return () => {}

  const ctx = gsap.context(() => {
    ScrollTrigger.config({ ignoreMobileResize: true })
    gsap.config({ nullTargetWarn: false })

    /* ================= sections（滚动漫场） ================= */

    document.querySelectorAll('.poster-section').forEach((section) => {
      const board = section.querySelector('.poster-board')
      if (!board) return

      const skip = new Set([
        'bnd-stack',
        'bnd-left',
        'source-grid',
        'note-grid',
        'output-grid',
        'parcel-grid',
        'specimen-grid',
      ])
      const blocks = [...board.children].filter(
        (el) => ![...el.classList].some((c) => skip.has(c)),
      )

      const head = section.querySelector('.poster-head')
      const en = head?.querySelector('.poster-en')
      const cn = head?.querySelector('.poster-cn')
      const lede = head?.querySelector('.poster-lede')
      const holo = section.querySelector('.holo-x, .holo-c, .holo-ring')
      const bigs = section.querySelectorAll('.poster-big')
      const alphaOnly = section.querySelectorAll('.bnd-left, .bnd-stack')

      gsap.set(blocks, { y: 76, autoAlpha: 0 })
      setMask(en)
      setFadeUp(cn, 46)
      setFadeUp(lede, 30)
      gsap.set(bigs, { autoAlpha: 0, scaleY: 0.5, transformOrigin: '50% 100%' })
      if (holo)
        gsap.set(holo, {
          autoAlpha: 0,
          scale: 0.55,
          rotate: -16,
          transformOrigin: '50% 50%',
        })
      gsap.set(alphaOnly, { autoAlpha: 0 })

      const tl = gsap.timeline({
        scrollTrigger: { trigger: section, start: 'top 74%', once: true },
        defaults: { ease: 'expo.out' },
      })

      tl.to(blocks, { y: 0, autoAlpha: 1, duration: 1.15, stagger: 0.1 }, 0)
      if (en) tl.to(en, { yPercent: 0, scaleY: 1, duration: 1.35 }, 0.14)
      if (bigs.length)
        tl.to(bigs, { autoAlpha: 1, scaleY: 1, duration: 1.25 }, 0.3)
      if (holo)
        tl.to(holo, { autoAlpha: 1, scale: 1, rotate: 0, duration: 1.5 }, 0.28)
      if (cn) tl.to(cn, { y: 0, autoAlpha: 1, duration: 1 }, 0.42)
      if (lede) tl.to(lede, { y: 0, autoAlpha: 1, duration: 1 }, 0.52)
      if (alphaOnly.length)
        tl.to(alphaOnly, { autoAlpha: 1, duration: 1.1 }, 0.22)

      const route = section.querySelector('.poster-route')
      if (route) {
        const lines = route.querySelectorAll('i')
        const labels = route.querySelectorAll('span')
        gsap.set(lines, { scaleX: 0, transformOrigin: '0% 50%' })
        gsap.set(labels, { y: 10, autoAlpha: 0 })
        tl.to(labels, { y: 0, autoAlpha: 1, duration: 0.8, stagger: 0.08 }, 0.55)
        tl.to(
          lines,
          { scaleX: 1, duration: 1.25, ease: 'power3.inOut', stagger: 0.16 },
          0.55,
        )
      }

      section
        .querySelectorAll('.source-grid, .note-grid, .output-grid, .parcel-grid, .specimen-grid')
        .forEach((grid) => {
          const items = [...grid.children]
          gsap.set(items, { y: 52, autoAlpha: 0 })
          tl.to(items, { y: 0, autoAlpha: 1, duration: 1.05, stagger: 0.12 }, 0.5)
        })

      section.querySelectorAll('.parcel-tile').forEach((tile) => {
        gsap.fromTo(
          tile,
          { yPercent: -2 },
          {
            yPercent: 7,
            ease: 'none',
            scrollTrigger: {
              trigger: tile,
              start: 'top bottom',
              end: 'bottom top',
              scrub: 0.8,
            },
          },
        )
      })

      section
        .querySelectorAll('.poster-quote, .poster-calim-signoff')
        .forEach((el) => {
          gsap.set(el, { autoAlpha: 0, y: 44, scale: 0.985 })
          tl.to(el, { autoAlpha: 1, y: 0, scale: 1, duration: 1.2 }, 0.6)
        })
    })

    /* ================= 非海报区的过渡带 ================= */

    ;['.marquee-band', '.interstitial'].forEach((sel) => {
      const el = document.querySelector(sel)
      if (!el) return
      gsap.set(el, { autoAlpha: 0, y: 36 })
      gsap.to(el, {
        autoAlpha: 1,
        y: 0,
        duration: 1.1,
        ease: 'expo.out',
        scrollTrigger: { trigger: el, start: 'top 94%', once: true },
      })
    })

    const footer = document.querySelector('.site-footer')
    if (footer) {
      gsap.set(footer, { autoAlpha: 0 })
      gsap.to(footer, {
        autoAlpha: 1,
        duration: 0.9,
        ease: 'power2.out',
        scrollTrigger: { trigger: footer, start: 'top 96%', once: true },
      })
    }

    /* ================= hero 滚动视差 ================= */

    gsap.to('.hero-poster', {
      yPercent: -7,
      autoAlpha: 0.32,
      ease: 'none',
      scrollTrigger: {
        trigger: '.hero',
        start: 'top top',
        end: 'bottom top',
        scrub: 0.6,
      },
    })
    gsap.to('.hero-sculpture', {
      yPercent: 8,
      ease: 'none',
      scrollTrigger: {
        trigger: '.hero',
        start: 'top top',
        end: 'bottom top',
        scrub: 0.9,
      },
    })

    /* ================= opening + hero 进场 ================= */

    const overlay = document.querySelector('.opening')
    if (!overlay) return

    document.documentElement.classList.add('opening-lock')
    window.scrollTo(0, 0)

    // hero 初始状态（开场遮罩还在时就压好，揭开后直接演）
    setFadeUp('.hero-topline', -18)
    setFadeUp('.hero-kicker', 14)
    setMask('.hero-title-line')
    setFadeUp('.hero-name', 24)
    setFadeUp('.hero-tagline-proximity', 24)
    setFadeUp('.hero-tagline', 26)
    gsap.set('.hero-title-rule', { scaleX: 0, transformOrigin: '0% 50%' })
    setFadeUp('.hero-lede', 26)
    setFadeUp('.hero-cta-row', 30)
    setFadeUp('.hero-meta', 16)
    gsap.set('.hero-sculpture', { autoAlpha: 0, scale: 1.07, y: 46 })
    gsap.set('.hero-console', { autoAlpha: 0, y: 64, scale: 0.94 })
    gsap.set('.hero-chip', { autoAlpha: 0, y: 18, scale: 0.9 })
    gsap.set('.hero-bars', { autoAlpha: 0, x: -54 })
    gsap.set(['.hero-stage-label', '.hero-stage-caption'], { autoAlpha: 0 })
    gsap.set('.hero-side-nav a', { autoAlpha: 0, x: 16 })
    setFadeUp('.hero-facts-row', 18)

    gsap.set(overlay, { display: 'flex', clipPath: 'inset(0% 0% 0% 0%)' })
    setMask('.opening-line > span')
    setFadeUp('.opening-sub > span', 26)
    gsap.set('.opening-rule i', { scaleX: 0, transformOrigin: '0% 50%' })

    const heroIntro = gsap.timeline({ defaults: { ease: 'expo.out' } })
    heroIntro
      .to('.hero-topline', { y: 0, autoAlpha: 1, duration: 0.9 }, 0)
      .to('.hero-kicker', { y: 0, autoAlpha: 1, duration: 0.9 }, 0.06)
      .to(
        '.hero-title-line',
        {
          yPercent: 0,
          scaleY: 1,
          duration: 1.35,
          stagger: 0.15,
        },
        0.1,
      )
      .to('.hero-name', { y: 0, autoAlpha: 1, duration: 1 }, 0.62)
      .to('.hero-tagline-proximity', { y: 0, autoAlpha: 1, duration: 1 }, 0.68)
      .to('.hero-tagline', { y: 0, autoAlpha: 1, duration: 1 }, 0.74)
      .to(
        '.hero-title-rule',
        { scaleX: 1, duration: 1, ease: 'power3.inOut' },
        0.82,
      )
      .to('.hero-lede', { y: 0, autoAlpha: 1, duration: 1 }, 0.92)
      .to('.hero-cta-row', { y: 0, autoAlpha: 1, duration: 1 }, 1.02)
      .to('.hero-meta', { y: 0, autoAlpha: 1, duration: 0.9 }, 1.18)
      .to('.hero-sculpture', { autoAlpha: 1, scale: 1, y: 0, duration: 1.6 }, 0.2)
      .to('.hero-console', { autoAlpha: 1, y: 0, scale: 1, duration: 1.5, ease: 'power3.out' }, 0.45)
      .to('.hero-chip', { autoAlpha: 1, y: 0, scale: 1, duration: 0.9, stagger: 0.14, ease: 'back.out(1.6)' }, 1.0)
      .to('.hero-bars', { autoAlpha: 1, x: 0, duration: 1.15 }, 0.65)
      .to(
        ['.hero-stage-label', '.hero-stage-caption'],
        { autoAlpha: 1, duration: 0.8 },
        0.9,
      )
      .to(
        '.hero-side-nav a',
        { autoAlpha: 1, x: 0, duration: 0.8, stagger: 0.07 },
        0.95,
      )
      .to('.hero-facts-row', { y: 0, autoAlpha: 1, duration: 0.9 }, 1.32)

    const master = gsap.timeline({
      onComplete: () => {
        overlay.style.display = 'none'
        document.documentElement.classList.remove('opening-lock')
        ScrollTrigger.refresh()
      },
    })

    master
      .to(
        '.opening-line > span',
        { yPercent: 0, scaleY: 1, duration: 1.1, stagger: 0.14 },
        0.1,
      )
      .to('.opening-rule i', { scaleX: 1, duration: 1, ease: 'power2.inOut' }, 0.2)
      .to('.opening-sub > span', { y: 0, autoAlpha: 1, duration: 0.8 }, 0.6)
      .to(
        '.opening-inner',
        { yPercent: -7, autoAlpha: 0, duration: 0.5, ease: 'power2.in' },
        1.45,
      )
      .to(
        overlay,
        { clipPath: 'inset(0% 0% 100% 0%)', duration: 0.85, ease: 'expo.inOut' },
        1.5,
      )
      .add(heroIntro, 1.85)
  })

  // Webfont swap changes section heights; re-measure triggers once fonts settle.
  if (typeof document !== 'undefined' && document.fonts?.ready) {
    document.fonts.ready.then(() => ScrollTrigger.refresh())
  }

  return () => ctx.revert()
}
