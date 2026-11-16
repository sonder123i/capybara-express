import React from 'react';
import { AbsoluteFill, interpolate, spring } from 'remotion';
import { Wheat } from 'lucide-react';
import { C, FONT } from '../theme';
import { useCurrentFrame, useVideoConfig } from 'remotion';

export interface IntroProps {
  title: string; // 主标题第一行
  titleHighlight?: string; // 主标题第二行（高亮色）
  subtitle?: string; // 副标题
  sub?: string; // 副标题下方小字
  icon?: React.ComponentType<{ size?: number; color?: string; strokeWidth?: number }>; // 顶部图标
}

/**
 * 开场：标题 + 图标 + 副标题
 * 改文案只需在 Video.tsx 里给 <Intro .../> 传 props。
 */
export const Intro: React.FC<IntroProps> = ({
  title,
  titleHighlight,
  subtitle,
  sub,
  icon: Icon = Wheat,
}) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const fadeIn = interpolate(frame, [0, 10], [0, 1], { extrapolateRight: 'clamp' });
  const fadeOut = interpolate(frame, [110, 120], [1, 0], { extrapolateLeft: 'clamp' });

  const iconPop = spring({ frame: frame - 4, fps, config: { damping: 10, stiffness: 130, mass: 0.5 } });
  const lineSpring = spring({ frame: frame - 52, fps, config: { damping: 20, stiffness: 80, mass: 0.7 } });
  const titleA = spring({ frame: frame - 16, fps, config: { damping: 16, stiffness: 100, mass: 0.7 } });
  const titleB = spring({ frame: frame - 32, fps, config: { damping: 16, stiffness: 100, mass: 0.7 } });
  const subR = spring({ frame: frame - 68, fps, config: { damping: 20, stiffness: 80, mass: 0.8 } });

  const sway = Math.sin(frame / 14) * 5;

  return (
    <AbsoluteFill
      style={{ opacity: fadeIn * fadeOut, alignItems: 'center', justifyContent: 'center', gap: 40 }}
    >
      <div
        style={{
          opacity: iconPop,
          transform: `scale(${interpolate(iconPop, [0, 1], [0.4, 1])}) rotate(${sway}deg)`,
        }}
      >
        <Icon size={150} color={C.gold} strokeWidth={2} />
      </div>

      <div
        style={{
          fontFamily: FONT,
          fontSize: 132,
          fontWeight: 900,
          textAlign: 'center',
          lineHeight: 1.18,
          letterSpacing: 8,
        }}
      >
        <div
          style={{
            opacity: titleA,
            transform: `translateY(${interpolate(titleA, [0, 1], [40, 0])}px)`,
            color: C.cream,
            textShadow: '0 8px 30px rgba(0,0,0,0.55)',
          }}
        >
          {title}
        </div>
        {titleHighlight ? (
          <div
            style={{
              opacity: titleB,
              transform: `translateY(${interpolate(titleB, [0, 1], [40, 0])}px)`,
              color: C.gold,
              textShadow: '0 8px 34px rgba(245,179,46,0.5)',
            }}
          >
            {titleHighlight}
          </div>
        ) : null}
      </div>

      <div
        style={{
          width: interpolate(lineSpring, [0, 1], [0, 340]),
          height: 5,
          borderRadius: 3,
          background: `linear-gradient(90deg, transparent, ${C.gold}, transparent)`,
        }}
      />

      {subtitle ? (
        <div
          style={{
            opacity: subR,
            transform: `translateY(${interpolate(subR, [0, 1], [22, 0])}px)`,
            fontFamily: FONT,
            fontSize: 46,
            fontWeight: 600,
            color: C.creamDim,
            letterSpacing: 6,
            textAlign: 'center',
            lineHeight: 1.5,
          }}
        >
          {subtitle}
          {sub ? <div style={{ fontSize: 36, marginTop: 18, opacity: 0.9 }}>{sub}</div> : null}
        </div>
      ) : null}
    </AbsoluteFill>
  );
};
