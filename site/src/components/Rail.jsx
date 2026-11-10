import { NAV, VERSION } from '../data.js'

export default function Rail({ active }) {
  return (
    <nav className="rail" aria-label="章节索引">
      <span className="rail-word">INDEX</span>
      <span className="rail-line" aria-hidden="true" />
      <ol>
        {NAV.map((item) => (
          <li key={item.id}>
            <a
              className={active === item.id ? 'is-active' : ''}
              href={`#${item.id}`}
              aria-label={`${item.index} ${item.label}`}
            >
              <span className="rail-num">{item.index}</span>
              <span className="rail-label">{item.label}</span>
            </a>
          </li>
        ))}
      </ol>
      <span className="rail-line rail-line--short" aria-hidden="true" />
      <span className="rail-ver">CE {VERSION}</span>
    </nav>
  )
}
