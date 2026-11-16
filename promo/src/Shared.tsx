import React from 'react';
import {
  interpolate,
  spring,
  useCurrentFrame,
  useVideoConfig,
  AbsoluteFill,
} from 'remotion';
import { Wheat, TriangleAlert } from 'lucide-react';
import { C, FONT } from './theme';

/* ============ 动画 hooks ============ */

/** 弹跳出现（缩放+淡入） */
export const usePop = (delay = 0) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  return spring({
    frame: frame - delay,
    fps,
    config: { damping: 12, stiffness: 140, mass: 0.6 },
  });
};

/** 上浮出现（位移+淡入） */
export const useRise = (delay = 0, distance = 44) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const s = spring({
    frame: frame - delay,
    fps,
    config: { damping: 18, stiffness: 90, mass: 0.8 },
  });
  return { opacity: s, y: interpolate(s, [0, 1], [distance, 0]) };
};

/** 场景整体淡入淡出（转场） */
export const useSceneFade = (dur: number, fadeFrames = 10) => {
  const frame = useCurrentFrame();
  const inOp = interpolate(frame, [0, fadeFrames], [0, 1], {
    extrapolateRight: 'clamp',
  });
  const outOp = interpolate(frame, [dur - fadeFrames, dur], [1, 0], {
    extrapolateLeft: 'clamp',
  });
  return Math.min(inOp, outOp);
};

/* ============ 全局背景 ============ */

export const GlobalBg: React.FC = () => {
  const frame = useCurrentFrame();
  // 缓慢呼吸的光晕位移，让静态背景不死板
  const drift = Math.sin(frame / 90) * 30;
  const glow = interpolate(Math.sin(frame / 60), [-1, 1], [0.25, 0.45]);

  return (
    <AbsoluteFill
      style={{
        background: `linear-gradient(170deg, ${C.bgDeep} 0%, ${C.bgMid} 42%, ${C.bgWarm} 78%, ${C.goldDeep} 100%)`,
      }}
    >
      {/* 右上角暖光晕（太阳） */}
      <div
        style={{
          position: 'absolute',
          top: -180 + drift,
          right: -220,
          width: 900,
          height: 900,
          borderRadius: '50%',
          background: `radial-gradient(circle, rgba(245,179,46,${glow}) 0%, rgba(245,179,46,0) 68%)`,
        }}
      />
      {/* 左下角暖光晕 */}
      <div
        style={{
          position: 'absolute',
          bottom: -260 - drift,
          left: -240,
          width: 820,
          height: 820,
          borderRadius: '50%',
          background: `radial-gradient(circle, rgba(255,90,54,0.22) 0%, rgba(255,90,54,0) 70%)`,
        }}
      />
      {/* 装饰暗纹（示例用麦穗；换成你的主题图标即可） */}
      <WheatPattern />
    </AbsoluteFill>
  );
};

const WheatPattern: React.FC = () => {
  const items = Array.from({ length: 12 });
  return (
    <AbsoluteFill style={{ opacity: 0.07 }}>
      {items.map((_, i) => {
        const col = i % 4;
        const row = Math.floor(i / 4);
        return (
          <Wheat
            key={i}
            size={150}
            color={C.accent}
            strokeWidth={1.2}
            style={{
              position: 'absolute',
              left: col * 300 - 40,
              top: row * 640 + 120,
              transform: `rotate(${(i % 2 === 0 ? 1 : -1) * 14}deg)`,
            }}
          />
        );
      })}
    </AbsoluteFill>
  );
};

/* ============ 通用元件 ============ */

/** 顶部"隐患/要点"标签（警示胶囊） */
export const HazardTag: React.FC<{ text: string; delay?: number }> = ({
  text,
  delay = 0,
}) => {
  const p = usePop(delay);
  return (
    <div
      style={{
        opacity: p,
        transform: `scale(${interpolate(p, [0, 1], [0.8, 1])})`,
        display: 'inline-flex',
        alignItems: 'center',
        gap: 16,
        padding: '14px 40px',
        borderRadius: 999,
        background: `linear-gradient(90deg, ${C.alertDeep}, ${C.alert})`,
        boxShadow: `0 6px 26px rgba(255,90,54,0.42)`,
        fontFamily: FONT,
        fontSize: 40,
        fontWeight: 800,
        color: C.cream,
        letterSpacing: 2,
      }}
    >
      <TriangleAlert size={38} color={C.cream} strokeWidth={2.6} />
      {text}
    </div>
  );
};

/** 场景主标题（main + 可选高亮行） */
export const SceneTitle: React.FC<{
  main: string;
  highlight?: string;
  delay?: number;
}> = ({ main, highlight, delay = 0 }) => {
  const r = useRise(delay, 34);
  return (
    <div
      style={{
        opacity: r.opacity,
        transform: `translateY(${r.y}px)`,
        fontFamily: FONT,
        fontSize: 92,
        fontWeight: 900,
        color: C.cream,
        textAlign: 'center',
        lineHeight: 1.16,
        letterSpacing: 3,
        textShadow: '0 6px 26px rgba(0,0,0,0.5)',
      }}
    >
      {main}
      {highlight ? (
        <span style={{ color: C.gold, display: 'block' }}>{highlight}</span>
      ) : null}
    </div>
  );
};

/** 要点条目（编号 + 文案，tone 控制红/绿色） */
export const Point: React.FC<{
  index: number;
  text: string;
  delay: number;
  tone?: 'alert' | 'safe';
}> = ({ index, text, delay, tone = 'alert' }) => {
  const r = useRise(delay, 40);
  const badge = tone === 'alert' ? C.alert : C.safe;
  return (
    <div
      style={{
        opacity: r.opacity,
        transform: `translateY(${r.y}px)`,
        display: 'flex',
        alignItems: 'center',
        gap: 26,
        padding: '24px 34px',
        marginBottom: 22,
        borderRadius: 22,
        background: C.card,
        border: `2px solid ${C.line}`,
        backdropFilter: 'blur(3px)',
      }}
    >
      <div
        style={{
          flexShrink: 0,
          width: 62,
          height: 62,
          borderRadius: '50%',
          background: `linear-gradient(135deg, ${badge}, ${tone === 'alert' ? C.alertDeep : C.safe})`,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontFamily: FONT,
          fontSize: 36,
          fontWeight: 900,
          color: C.cream,
          boxShadow: `0 4px 16px rgba(0,0,0,0.35)`,
        }}
      >
        {index}
      </div>
      <div
        style={{
          fontFamily: FONT,
          fontSize: 46,
          fontWeight: 700,
          color: C.cream,
          lineHeight: 1.32,
          letterSpacing: 1,
        }}
      >
        {text}
      </div>
    </div>
  );
};

/** 底部强调条（金句） */
export const BottomBar: React.FC<{ text: string; delay: number }> = ({
  text,
  delay,
}) => {
  const frame = useCurrentFrame();
  const p = usePop(delay);
  const pulse = interpolate(
    Math.sin((frame - delay) / 11),
    [-1, 1],
    [1, 1.018],
  );
  return (
    <div
      style={{
        opacity: p,
        transform: `scale(${interpolate(p, [0, 1], [0.9, 1]) * pulse})`,
        padding: '26px 46px',
        borderRadius: 24,
        background: `linear-gradient(90deg, ${C.goldDeep}, ${C.gold}, ${C.goldDeep})`,
        boxShadow: '0 10px 34px rgba(245,179,46,0.4)',
        fontFamily: FONT,
        fontSize: 50,
        fontWeight: 900,
        color: C.ink,
        textAlign: 'center',
        letterSpacing: 3,
      }}
    >
      {text}
    </div>
  );
};

/** 场景内容容器（统一内边距 + 淡入淡出） */
export const SceneWrap: React.FC<{
  dur: number;
  children: React.ReactNode;
  justify?: 'flex-start' | 'center';
}> = ({ dur, children, justify = 'center' }) => {
  const op = useSceneFade(dur);
  return (
    <AbsoluteFill
      style={{
        opacity: op,
        padding: '110px 68px 90px',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: justify,
        gap: 44,
      }}
    >
      {children}
    </AbsoluteFill>
  );
};
