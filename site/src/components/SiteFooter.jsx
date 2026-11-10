import { LINKS, NAV, VERSION } from '../data.js'
import { ArrowUpRight, Barcode, BrandMark } from './Common.jsx'

export default function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="shell footer-barcode">
        <Barcode tall />
        <span>CAPYBARA EXPRESS / CE-{VERSION} / MADE FOR ANDROID</span>
        <Barcode tall />
      </div>

      <div className="shell footer-grid">
        <div className="footer-brand">
          <BrandMark />
          <h2>
            卡皮巴拉
            <br />
            快递助手
          </h2>
          <p>把取快递这件事，安静地做完。</p>
        </div>

        <div className="footer-col">
          <span className="footer-col-title">页面</span>
          {NAV.map((item) => (
            <a key={item.id} href={`#${item.id}`}>
              <span>{item.index}</span>
              {item.label}
            </a>
          ))}
        </div>

        <div className="footer-col">
          <span className="footer-col-title">代码</span>
          <a href={LINKS.github} target="_blank" rel="noreferrer">
            GitHub
            <ArrowUpRight />
          </a>
          <a href={LINKS.gitee} target="_blank" rel="noreferrer">
            Gitee
            <ArrowUpRight />
          </a>
          <a href={LINKS.releases} target="_blank" rel="noreferrer">
            Releases
            <ArrowUpRight />
          </a>
          <a href={LINKS.issues} target="_blank" rel="noreferrer">
            Issues
            <ArrowUpRight />
          </a>
        </div>

        <div className="footer-col">
          <span className="footer-col-title">说明</span>
          <a href={LINKS.readme} target="_blank" rel="noreferrer">
            README
            <ArrowUpRight />
          </a>
          <a href={LINKS.privacy} target="_blank" rel="noreferrer">
            隐私
            <ArrowUpRight />
          </a>
          <a href={`${LINKS.readme}#许可证`} target="_blank" rel="noreferrer">
            许可证
            <ArrowUpRight />
          </a>
          <a href={`${LINKS.github}/blob/main/docs/verification-checklist.md`} target="_blank" rel="noreferrer">
            核对清单
            <ArrowUpRight />
          </a>
        </div>
      </div>

      <div className="shell footer-signoff">
        <span className="footer-signoff-mark" aria-hidden="true">
          ✳
        </span>
        <span className="footer-signoff-en">SLOWLY.</span>
        <span className="footer-signoff-zh">慢一点，也能到。</span>
      </div>

      <div className="shell footer-bottom">
        <span>© 2026 卡皮巴拉快递助手 · GPL-3.0</span>
        <span>本站不设 Cookie、不引第三方脚本、不收集访问数据。字体走系统，图标自己画。</span>
        <a href="#top">回到顶部 ↑</a>
      </div>
    </footer>
  )
}
