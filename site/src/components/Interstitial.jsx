import DecryptedText from './reactbits/DecryptedText/DecryptedText/DecryptedText.jsx'

export default function Interstitial() {
  return (
    <section className="interstitial" aria-label="安全到保管时钟的交接带">
      <div className="interstitial__grid" aria-hidden="true" />
      <div className="interstitial__scan" aria-hidden="true" />
      <div className="shell interstitial__row">
        <span className="interstitial__mark">02.5 / HANDOFF</span>
        <DecryptedText
          text="LOCAL PARSER VERIFIED · ZERO PACKETS EMITTED · ENTERING CUSTODY CLOCK"
          animateOn="view"
          speed={26}
          maxIterations={9}
          characters="ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#/-:."
          className="interstitial__code"
        />
        <span className="interstitial__mark">03 / CALM</span>
      </div>
    </section>
  )
}
