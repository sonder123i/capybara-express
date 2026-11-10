import { useEffect, useRef, useState } from 'react'

/**
 * Only mounts a heavy canvas / WebGL specimen while it is near the viewport.
 * Off-screen canvases are unmounted so the page never keeps more than a
 * handful of GL contexts alive at the same time.
 *
 * `children` may be a render function: ({ active, paused }) => node.
 */
export default function ViewportShaderGate({
  children,
  rootMargin = '320px 0px',
  unmountOnExit = true,
  fallback = null,
  className = '',
}) {
  const ref = useRef(null)
  const [inView, setInView] = useState(false)

  useEffect(() => {
    const el = ref.current
    if (!el) return undefined
    if (typeof IntersectionObserver === 'undefined') {
      setInView(true)
      return undefined
    }
    const io = new IntersectionObserver(
      ([entry]) => setInView(entry.isIntersecting),
      { rootMargin },
    )
    io.observe(el)
    return () => io.disconnect()
  }, [rootMargin])

  const render = () =>
    typeof children === 'function'
      ? children({ active: inView, paused: !inView })
      : children

  return (
    <div ref={ref} className={`shader-gate ${className}`.trim()} data-shader-active={inView}>
      {inView ? render() : unmountOnExit ? fallback : render()}
    </div>
  )
}
