import React from 'react';
import { interpolate } from 'remotion';
import { Lightbulb } from 'lucide-react';
import { C, FONT } from '../theme';
import {
  HazardTag,
  SceneTitle,
  Point,
  BottomBar,
  SceneWrap,
  usePop,
  useRise,
} from '../Shared';
import type { LucideIcon } from 'lucide-react';

export interface SceneProps {
  dur: number; // 该场景时长（帧），从 theme.T.xxx.dur 取
  tag?: string; // 右上角警示标签，如 "隐患一"
  title: string; // 场景主标题
  titleHighlight?: string; // 主标题高亮行
  points?: { text: string; tone?: 'alert' | 'safe' }[]; // 要点列表模式
  grid?: { icon: LucideIcon; text: string }[]; // 2x2 图标网格模式（建议 4 格）
  tip?: { text: string; sub?: string }; // 绿色提示框（正确做法 / 疏堵并用）
  bottom?: string; // 底部金句强调条
}

/**
 * 通用场景组件：支持「要点列表」与「图标网格」两种版式，可选绿色提示框与底部金句。
 * 用法见 src/Video.tsx。复制本文件或直接在 Video.tsx 用 <SceneTemplate .../> 传 props 即可。
 */
export const SceneTemplate: React.FC<SceneProps> = ({
  dur,
  tag,
  title,
  titleHighlight,
  points,
  grid,
  tip,
  bottom,
}) => {
  const cellOps = (grid ?? []).map((_, i) => usePop(8 + i * 12));
  const tipR = useRise(150, 32);

  const cellBase: React.CSSProperties = {
    width: 226,
    height: 158,
    borderRadius: 22,
    display: 'flex',
    alignItems: 'center',
    gap: 16,
    padding: '0 22px',
    background: 'linear-gradient(160deg, rgba(255,90,54,0.18), rgba(36,20,8,0.55))',
    border: `2px solid rgba(255,90,54,0.45)`,
    boxShadow: '0 6px 18px rgba(0,0,0,0.3)',
  };
  const label: React.CSSProperties = {
    fontFamily: FONT,
    fontSize: 32,
    fontWeight: 800,
    color: C.cream,
    letterSpacing: 1,
  };

  return (
    <SceneWrap dur={dur} justify="flex-start">
      {tag ? (
        <div style={{ marginTop: 16 }}>
          <HazardTag text={tag} delay={0} />
        </div>
      ) : null}

      {grid ? (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '226px 226px',
            gridTemplateRows: '158px 158px',
            gap: 14,
            marginTop: 6,
          }}
        >
          {grid.map((g, i) => (
            <div
              key={i}
              style={{
                ...cellBase,
                opacity: cellOps[i],
                transform: `scale(${interpolate(cellOps[i], [0, 1], [0.85, 1])})`,
              }}
            >
              <g.icon size={66} color={C.alert} strokeWidth={1.8} />
              <div style={label}>{g.text}</div>
            </div>
          ))}
        </div>
      ) : null}

      <SceneTitle main={title} highlight={titleHighlight} delay={grid ? 64 : 0} />

      {points ? (
        <div style={{ width: '100%', marginTop: 4 }}>
          {points.map((p, i) => (
            <Point
              key={i}
              index={i + 1}
              text={p.text}
              delay={64 + i * 24}
              tone={p.tone ?? 'alert'}
            />
          ))}
        </div>
      ) : null}

      {tip ? (
        <div
          style={{
            opacity: tipR.opacity,
            transform: `translateY(${tipR.y}px)`,
            display: 'flex',
            alignItems: 'center',
            gap: 18,
            padding: '20px 30px',
            borderRadius: 22,
            background:
              'linear-gradient(90deg, rgba(79,168,107,0.95), rgba(127,214,154,0.95))',
            boxShadow: '0 8px 24px rgba(79,168,107,0.4)',
          }}
        >
          <Lightbulb size={48} color={C.ink} strokeWidth={2.2} fill={C.ink} />
          <div
            style={{
              fontFamily: FONT,
              fontSize: 38,
              fontWeight: 800,
              color: C.ink,
              lineHeight: 1.3,
              letterSpacing: 1,
            }}
          >
            <div>{tip.text}</div>
            {tip.sub ? (
              <div style={{ fontSize: 32, fontWeight: 700, marginTop: 4 }}>{tip.sub}</div>
            ) : null}
          </div>
        </div>
      ) : null}

      {bottom ? (
        <div style={{ marginTop: 8 }}>
          <BottomBar text={bottom} delay={64 + (points?.length ?? 0) * 24 + 40} />
        </div>
      ) : null}
    </SceneWrap>
  );
};
