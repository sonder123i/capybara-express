import CurvedLoop from './reactbits/CurvedLoop.jsx'
import ScrollVelocity from './reactbits/ScrollVelocity/ScrollVelocity.jsx'

const LINE =
  'LOCAL-FIRST · NO ACCOUNT · NO ADS · NO ANALYTICS · GPL-3.0 · WORK OFFLINE'

const TAPE_DARK =
  'PARCELS ARRIVE · HASSLE DOESN’T · ZERO CLOUD · LOCAL SQLITE'
const TAPE_HOLO =
  '01 EFFORTLESS · 01.5 SPECIMENS · 02 PRIVATE · 03 CALM · 04 BOUNDARIES · 05 INSTALL'

export default function MarqueeBand() {
  return (
    <section className="marquee-band" aria-label="产品边界">
      <span className="marquee-band-label">THE QUIET INTERFACE / 01—05</span>
      <CurvedLoop
        marqueeText={LINE}
        separator="✦"
        separatorColor="#c21f72"
        color="#08090b"
        shape="arc"
        direction="left"
        speed={0.9}
        curveAmount={240}
        fontSize={76}
        fontWeight={600}
        letterSpacing={-1}
        edgeFade={0.15}
        interactive
      />

      <div className="velocity-tape">
        <div className="velocity-tape__row velocity-tape__row--dark">
          <ScrollVelocity
            texts={[TAPE_DARK]}
            velocity={42}
            numCopies={4}
            separator="✦"
            gap={22}
            pauseOnHover
            draggable
            skewWithVelocity={2.2}
            scaleWithVelocity={0.04}
            velocityMapping={{ input: [0, 1400], output: [0, 1.5] }}
            className="velocity-tape__text"
            fontSize={48}
            fontWeight={600}
            color="#f7f8fa"
          />
        </div>
        <div className="velocity-tape__row velocity-tape__row--holo">
          <ScrollVelocity
            texts={[TAPE_HOLO]}
            velocity={30}
            numCopies={4}
            separator="·"
            gap={18}
            pauseOnHover
            draggable
            skewWithVelocity={1.6}
            velocityMapping={{ input: [0, 1400], output: [0, 1.2] }}
            className="velocity-tape__text velocity-tape__text--holo"
            fontSize={48}
            fontWeight={600}
            color="#08090b"
          />
        </div>
      </div>
    </section>
  )
}
