import { useEffect, useMemo, useRef, useState } from 'react'
import { asset } from '../data.js'
import TiltedCard from './reactbits/TiltedCard/TiltedCard.jsx'
import SplitFlapText from './reactbits/SplitFlapText/SplitFlapText.jsx'
import StatusMark from './reactbits/StatusMark/StatusMark.jsx'

const pad = (n) => String(n).padStart(2, '0')
const clock = (total) => {
  const s = Math.max(0, Math.abs(Math.round(total)))
  return `${pad(Math.floor(s / 3600))}:${pad(Math.floor((s % 3600) / 60))}:${pad(s % 60)}`
}

function useTicker() {
  const [t, setT] = useState(0)
  useEffect(() => {
    const id = window.setInterval(() => setT((v) => v + 1), 1000)
    return () => window.clearInterval(id)
  }, [])
  return t
}

const GROUPS = [
  {
    id: 'locker',
    name: '丰巢快递柜（3号楼下）',
    sub: '快递柜 · 过期风险',
    tone: 'risk',
    address: '杭州市西湖区阳光花园 3号楼下丰巢柜',
    hours: '24 小时',
    walk: '步行 2 分钟',
    dist: '120 m',
    pin: { x: 92, y: 58 },
  },
  {
    id: 'station',
    name: '菜鸟驿站（阳光花园小区东门）',
    sub: '驿站 · 都还安全',
    tone: 'safe',
    address: '杭州市西湖区阳光花园小区东门菜鸟驿站',
    hours: '08:00 – 21:30',
    walk: '步行 6 分钟',
    dist: '450 m',
    pin: { x: 232, y: 96 },
  },
]

const PARCELS = [
  {
    id: 'p1', group: 'locker', thumb: 'crops/thumb-ramen.webp', code: '8-3-1024', cell: 'A12',
    name: '日式豚骨叉烧拉面 3袋装', price: '¥28.4 · 面说', tone: 'risk', tag: '过期风险',
    base: 8073, dir: 1, prefix: '已超时', waybill: 'SF1390 2244 761',
    trace: '【杭州】已放入丰巢柜 A12，超时费用累计中。',
  },
  {
    id: 'p2', group: 'locker', thumb: 'crops/thumb-bottle.webp', code: '6-2-0588', cell: 'B07',
    name: '氨基酸控油洗发水 500ml', price: '¥59.9 · 未可', tone: 'due', tag: '临期',
    base: 21588, dir: -1, prefix: '剩', waybill: 'YT7720 1188 305',
    trace: '【杭州】已放入丰巢柜 B07，请在临期前取走。',
  },
  {
    id: 'p3', group: 'station', thumb: 'crops/thumb-coffee.webp', code: '5-2-3391', cell: '3排12号',
    name: '意式拼配咖啡豆 500g 中深烘焙', price: '¥68.0 · 星巴克', tone: 'safe', tag: '安全',
    base: 27730, dir: -1, prefix: '距关门', waybill: 'JD0021 5533 902',
    trace: '【杭州】已入库菜鸟驿站，货架 3排12号。',
  },
  {
    id: 'p4', group: 'station', thumb: null, code: '76416', cell: '2排5号',
    name: '冻干双拼猫粮 2kg', price: '¥89.0 · 网易严选', tone: 'safe', tag: '安全',
    base: 27730, dir: -1, prefix: '距关门', waybill: 'ZTO5510 2277 640',
    trace: '【杭州】已入库菜鸟驿站，货架 2排5号。', extra: true,
  },
  {
    id: 'p5', group: 'station', thumb: null, code: '3-1-0871', cell: '1排3号',
    name: '陶瓷马克杯 350ml', price: '¥39.0 · 杂物社', tone: 'safe', tag: '安全',
    base: 27730, dir: -1, prefix: '距关门', waybill: 'YD4402 8811 275',
    trace: '【杭州】已入库菜鸟驿站，货架 1排3号。', extra: true,
  },
]

const TABS = [
  { id: 'list', label: '待取件' },
  { id: 'id', label: '身份码' },
  { id: 'map', label: '位置' },
]

const HEAD = {
  list: { title: '待取件', sub: '5 件 · 2 个取件点' },
  id: { title: '身份码', sub: '出示给驿站扫码枪' },
  map: { title: '取件点', sub: '2 个 · 步行可达' },
}

const BoxIcon = (
  <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.6">
    <path d="M3.5 7.5 12 3l8.5 4.5v9L12 21l-8.5-4.5z" />
    <path d="M3.5 7.5 12 12l8.5-4.5M12 12v9" />
  </svg>
)

const TabIcon = ({ id }) => {
  if (id === 'list') return LockerIcon
  if (id === 'id') return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.7">
      <rect x="4" y="4" width="6.5" height="6.5" rx="1" />
      <rect x="13.5" y="4" width="6.5" height="6.5" rx="1" />
      <rect x="4" y="13.5" width="6.5" height="6.5" rx="1" />
      <path d="M13.5 13.5h3v3h-3zM17 17h3v3h-3z" />
    </svg>
  )
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.7">
      <path d="M12 21s7-6.1 7-11a7 7 0 1 0-14 0c0 4.9 7 11 7 11z" />
      <circle cx="12" cy="10" r="2.6" />
    </svg>
  )
}

/* deterministic pseudo-QR for the identity specimen (demo token, not scannable) */
const hashSeed = (str) => {
  let h = 1779033703 ^ str.length
  for (let i = 0; i < str.length; i++) {
    h = Math.imul(h ^ str.charCodeAt(i), 3432918353)
    h = (h << 13) | (h >>> 19)
  }
  return h >>> 0
}
const mulberry = (a) => () => {
  a |= 0; a = (a + 0x6d2b79f5) | 0
  let t = Math.imul(a ^ (a >>> 15), 1 | a)
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296
}
const qrCells = (seed) => {
  const n = 21
  const rand = mulberry(hashSeed(`ce-id-${seed}`))
  const grid = Array.from({ length: n }, () => Array.from({ length: n }, () => rand() > 0.52))
  const finder = (r0, c0) => {
    for (let r = 0; r < 7; r++) for (let c = 0; c < 7; c++) {
      const edge = r === 0 || r === 6 || c === 0 || c === 6
      const core = r >= 2 && r <= 4 && c >= 2 && c <= 4
      grid[r0 + r][c0 + c] = edge || core
    }
  }
  finder(0, 0); finder(0, n - 7); finder(n - 7, 0)
  return grid
}

function ParcelRow({ parcel, t, open, onToggle, onCopy, copied }) {
  const p = parcel
  return (
    <div className={`app-row-wrap ${open ? 'is-open' : ''}`}>
      <button type="button" className="app-row" aria-expanded={open} onClick={onToggle}>
        {p.thumb
          ? <img className="app-row__thumb" src={asset(p.thumb)} alt="" width="40" height="40" />
          : <span className="app-row__thumb app-row__thumb--icon" aria-hidden="true">{BoxIcon}</span>}
        <span className="app-row__main">
          <span className="app-row__code">
            {p.code}
            <em>{p.cell}</em>
          </span>
          <span className="app-row__name">{p.name}</span>
          <span className="app-row__price">{p.price}</span>
        </span>
        <span className="app-row__side">
          <span className={`app-tag app-tag--${p.tone}`}>{p.tag}</span>
          <span className={`app-row__time app-row__time--${p.tone}`}>{p.prefix} {clock(p.base + p.dir * t)}</span>
        </span>
        <span className={`app-row__chev ${open ? 'is-open' : ''}`} aria-hidden="true">
          <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" strokeWidth="2"><path d="m6 9 6 6 6-6" /></svg>
        </span>
      </button>
      <div className="app-detail" aria-hidden={!open}>
        <div className="app-detail__inner">
          <span className="app-detail__line"><em>运单号</em>{p.waybill}</span>
          <span className="app-detail__line"><em>最新轨迹</em>{p.trace}</span>
          <button type="button" className={`app-copy ${copied ? 'is-copied' : ''}`} onClick={onCopy}>
            {copied ? '已复制 ✓' : '复制取件码'}
          </button>
        </div>
      </div>
    </div>
  )
}

function Group({ group, t, collapsed, onCollapse, openId, onToggleRow, onCopy, copiedId, showAll, onShowAll }) {
  const rows = PARCELS.filter((p) => p.group === group.id && (!p.extra || showAll))
  const extraCount = PARCELS.filter((p) => p.group === group.id && p.extra).length
  return (
    <div className={`app-group app-group--${group.tone} ${collapsed ? 'is-collapsed' : ''}`}>
      <span className="app-group__rail" aria-hidden="true" />
      <button type="button" className="app-group__head" aria-expanded={!collapsed} onClick={onCollapse}>
        <span className="app-group__icon" aria-hidden="true">{group.id === 'locker' ? LockerIcon : HomeIcon}</span>
        <strong>{group.name}</strong>
        <span className="app-group__count">
          {PARCELS.filter((p) => p.group === group.id).length}
          <em>件</em>
        </span>
        <span className={`app-group__chev ${collapsed ? '' : 'is-open'}`} aria-hidden="true">
          <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" strokeWidth="2"><path d="m6 9 6 6 6-6" /></svg>
        </span>
      </button>
      {!collapsed && (
        <>
          <span className="app-group__sub">{group.sub}</span>
          {rows.map((p) => (
            <ParcelRow
              key={p.id}
              parcel={p}
              t={t}
              open={openId === p.id}
              onToggle={() => onToggleRow(p)}
              onCopy={() => onCopy(p.code)}
              copied={copiedId === p.code}
            />
          ))}
          {extraCount > 0 && (
            <button type="button" className="app-group__more" onClick={onShowAll}>
              {showAll ? '收起额外 2 件' : `还有 ${extraCount} 件 · 76416 · 3-1-0871`}
            </button>
          )}
        </>
      )}
    </div>
  )
}

function IdView({ t, refresh, onRefresh, onCopy, copied }) {
  const seed = Math.floor(t / 60) + refresh
  const remain = 60 - (t % 60)
  const cells = useMemo(() => qrCells(seed), [seed])
  const code = '88421024'
  return (
    <div className="app-id">
      <div className="app-id__qr" aria-label="身份码二维码（演示）">
        <svg viewBox="0 0 21 21" shapeRendering="crispEdges">
          <rect width="21" height="21" fill="#fff" />
          {cells.map((row, r) => row.map((on, c) => on
            ? <rect key={`${r}-${c}`} x={c} y={r} width="1" height="1" fill="#14161c" />
            : null))}
        </svg>
      </div>
      <div className="app-id__code">
        <SplitFlapText
          key={seed}
          text={code}
          padTo={8}
          loop={false}
          flipDuration={0.14}
          stagger={0.04}
          flipsPerChar={5}
          fontSize={20}
          gap={2}
          tileRadius={3}
          tileColor="#101014"
          textColor="#f7f8fa"
          depth={0.2}
          glare={0.08}
          charset="0123456789"
        />
      </div>
      <div className="app-id__row">
        <button type="button" className={`app-copy ${copied ? 'is-copied' : ''}`} onClick={() => onCopy(code)}>
          {copied ? '已复制 ✓' : '复制身份码'}
        </button>
        <button type="button" className="app-id__refresh" onClick={onRefresh}>
          <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" strokeWidth="2"><path d="M20 11a8 8 0 1 0-2.3 6.3M20 5v6h-6" /></svg>
          刷新
        </button>
      </div>
      <span className="app-id__note">{remain}s 后自动刷新 · 仅本机生成 · 演示数据</span>
    </div>
  )
}

function MapView({ selected, onSelect, onCopy, copied }) {
  const point = GROUPS.find((g) => g.id === selected)
  const you = { x: 150, y: 122 }
  return (
    <div className="app-map">
      <svg className="app-map__canvas" viewBox="0 0 320 150" role="img" aria-label="取件点示意地图">
        <rect width="320" height="150" fill="#eef1f6" />
        <g fill="#e2e6ee">
          <rect x="14" y="14" width="70" height="40" rx="4" />
          <rect x="120" y="10" width="80" height="34" rx="4" />
          <rect x="232" y="16" width="70" height="42" rx="4" />
          <rect x="16" y="86" width="60" height="46" rx="4" />
          <rect x="196" y="112" width="60" height="30" rx="4" />
        </g>
        <rect x="120" y="60" width="60" height="40" rx="6" fill="#dcefe2" />
        <g stroke="#f8fafc" strokeWidth="7" strokeLinecap="round">
          <path d="M0 70h320M104 0v150M212 0v150M0 104h320" />
        </g>
        <path
          className="app-map__route"
          d={`M${you.x} ${you.y} L${you.x} ${point.pin.y} L${point.pin.x} ${point.pin.y}`}
          fill="none"
          stroke="#2b62d9"
          strokeWidth="2"
          strokeDasharray="5 5"
        />
        {GROUPS.map((g) => (
          <g
            key={g.id}
            className={`app-map__pin ${selected === g.id ? 'is-active' : ''}`}
            transform={`translate(${g.pin.x} ${g.pin.y})`}
            onClick={() => onSelect(g.id)}
            role="button"
            tabIndex={0}
            aria-label={g.name}
            onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onSelect(g.id) } }}
          >
            <circle className="app-map__pulse" r="10" />
            <path d="M0 6C0 6-7-1.5-7-6a7 7 0 1 1 14 0C7-1.5 0 6 0 6z" />
            <circle cy="-6" r="2.6" fill="#fff" />
          </g>
        ))}
        <g transform={`translate(${you.x} ${you.y})`}>
          <circle r="5" fill="#2b62d9" />
          <circle r="5" fill="none" stroke="#fff" strokeWidth="2" />
        </g>
      </svg>
      <div className="app-map__list">
        {GROUPS.map((g) => (
          <button
            key={g.id}
            type="button"
            className={`app-map__point ${selected === g.id ? 'is-active' : ''}`}
            onClick={() => onSelect(g.id)}
          >
            <span className={`app-map__dot app-map__dot--${g.tone}`} aria-hidden="true" />
            <span className="app-map__meta">
              <strong>{g.id === 'locker' ? '丰巢柜 · 3号楼下' : '菜鸟驿站 · 东门'}</strong>
              <em>{g.dist} · {g.walk} · {g.hours}</em>
            </span>
          </button>
        ))}
      </div>
      <button type="button" className={`app-copy app-map__copy ${copied ? 'is-copied' : ''}`} onClick={() => onCopy(point.address)}>
        {copied ? '地址已复制 ✓' : `复制地址 · ${point.id === 'locker' ? '丰巢柜' : '菜鸟驿站'}`}
      </button>
    </div>
  )
}

const LockerIcon = (
  <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="1.6">
    <rect x="3.5" y="3.5" width="17" height="17" rx="2" />
    <path d="M9 3.5v17M15 3.5v17M3.5 9h17M3.5 15h17" />
  </svg>
)

const HomeIcon = (
  <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="1.6">
    <path d="M4 11 12 4l8 7M6.5 9.5V20h11V9.5" />
  </svg>
)

export default function HeroConsole() {
  const t = useTicker()
  const [tab, setTab] = useState('list')
  const [collapsed, setCollapsed] = useState({})
  const [openId, setOpenId] = useState(null)
  const [showAll, setShowAll] = useState(false)
  const [copiedId, setCopiedId] = useState(null)
  const [activeCode, setActiveCode] = useState('8-3-1024')
  const [selected, setSelected] = useState('locker')
  const [refresh, setRefresh] = useState(0)
  const [syncing, setSyncing] = useState(false)
  const [toast, setToast] = useState(null)
  const toastTimer = useRef(0)
  const copyTimer = useRef(0)
  const syncTimer = useRef(0)

  useEffect(() => () => {
    window.clearTimeout(toastTimer.current)
    window.clearTimeout(copyTimer.current)
    window.clearTimeout(syncTimer.current)
  }, [])

  const flash = (msg) => {
    setToast(msg)
    window.clearTimeout(toastTimer.current)
    toastTimer.current = window.setTimeout(() => setToast(null), 1700)
  }

  const copy = async (value, label) => {
    try {
      await navigator.clipboard.writeText(value)
      setCopiedId(value)
      setActiveCode(value)
      flash(`已复制 ${label || value}`)
      window.clearTimeout(copyTimer.current)
      copyTimer.current = window.setTimeout(() => setCopiedId(null), 1600)
    } catch {
      flash('复制失败 · 请长按手动复制')
    }
  }

  const sync = () => {
    if (syncing) return
    setSyncing(true)
    window.clearTimeout(syncTimer.current)
    syncTimer.current = window.setTimeout(() => {
      setSyncing(false)
      flash('已同步 5 件 · 0 云端')
    }, 900)
  }

  const toggleRow = (p) => {
    setOpenId((cur) => (cur === p.id ? null : p.id))
    setActiveCode(p.code)
  }

  const head = HEAD[tab]

  return (
    <div className="hero-console">
      <TiltedCard
        className="hero-console-tilt"
        containerWidth="100%"
        containerHeight="100%"
        imageWidth="100%"
        imageHeight="100%"
        showTooltip={false}
        flipOnClick={false}
        rotateAmplitude={5}
        scaleOnHover={1.015}
        glare={0.16}
        shadow={0.3}
        radius={26}
        floating
        intro={false}
      >
        <div className="app-panel" aria-label="应用首页实时交互演示（演示数据）">
          <span className="app-panel__sweep" aria-hidden="true" />
          <div className="app-head">
            <div className="app-head__title">
              <strong>{head.title}</strong>
              <span>{head.sub}</span>
            </div>
            <button type="button" className={`app-head__live ${syncing ? 'is-syncing' : ''}`} onClick={sync}>
              <i aria-hidden="true" />
              {syncing ? '同步中…' : '本地解析 · 刚刚同步'}
            </button>
          </div>

          <div className="app-body" key={tab} role="tabpanel" id={`app-tabpanel-${tab}`} aria-labelledby={`app-tab-${tab}`}>
            {tab === 'list' && GROUPS.map((g) => (
              <Group
                key={g.id}
                group={g}
                t={t}
                collapsed={Boolean(collapsed[g.id])}
                onCollapse={() => setCollapsed((c) => ({ ...c, [g.id]: !c[g.id] }))}
                openId={openId}
                onToggleRow={toggleRow}
                onCopy={(code) => copy(code)}
                copiedId={copiedId}
                showAll={showAll}
                onShowAll={() => setShowAll((v) => !v)}
              />
            ))}
            {tab === 'id' && (
              <IdView
                t={t}
                refresh={refresh}
                onRefresh={() => { setRefresh((v) => v + 1); flash('身份码已刷新') }}
                onCopy={(code) => copy(code, '身份码')}
                copied={copiedId === '88421024'}
              />
            )}
            {tab === 'map' && (
              <MapView
                selected={selected}
                onSelect={setSelected}
                onCopy={(addr) => copy(addr, '地址')}
                copied={copiedId === GROUPS.find((g) => g.id === selected).address}
              />
            )}
          </div>

          <div className="app-tabbar" role="tablist" aria-label="应用底部导航">
            {TABS.map((item) => (
              <button
                key={item.id}
                type="button"
                role="tab"
                id={`app-tab-${item.id}`}
                aria-selected={tab === item.id}
                aria-controls={`app-tabpanel-${item.id}`}
                className={`app-tab ${tab === item.id ? 'is-active' : ''}`}
                onClick={() => setTab(item.id)}
              >
                <TabIcon id={item.id} />
                {item.label}
              </button>
            ))}
          </div>

          {toast && <span className="app-toast" role="status">{toast}</span>}
        </div>
      </TiltedCard>

      <div className="hero-chip hero-chip--code" aria-hidden="true">
        <span className="hero-chip__label">PICKUP CODE / 取件码</span>
        <SplitFlapText
          key={activeCode}
          text={activeCode}
          padTo={8}
          loop={false}
          flipDuration={0.16}
          stagger={0.04}
          flipsPerChar={5}
          fontSize={13}
          gap={2}
          tileRadius={2}
          tileColor="#101014"
          textColor="#f7f8fa"
          depth={0}
          glare={0.08}
          charset="0123456789-"
        />
      </div>
      <div className="hero-chip hero-chip--local" aria-hidden="true">
        <StatusMark status="done" size={13} doneColor="#00b578" strike={false} />
        <span>
          0 云端
          <em>LOCAL SQLITE ONLY</em>
        </span>
      </div>
    </div>
  )
}