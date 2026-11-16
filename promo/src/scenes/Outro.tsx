import React from 'react';
import { AbsoluteFill, interpolate, spring } from 'remotion';
import { Wheat, Heart, Home } from 'lucide-react';
import { C, FONT } from '../theme';
import { useCurrentFrame, useVideoConfig } from 'remotion';

export interface OutroProps {
  title: string; // 主标题第一行
  titleHighlight?: string; // 主标题第二行（高亮）
  subtitle?: string; // 收尾语
  tag?: string; // 底部小标签（如 "— 标语 —"）
  signOff?: string; // 署名落款（如 "XX单位 提醒您"），不传则不显示
}

/**
 * 收尾：两行主标题 + 收尾语 + 可选署名。
 */
export const Outro: React.FC<OutroProps> = ({
  title,
  titleHighlight,
  subtitle,
  tag,
  signOff,
}) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const fadeIn = interpolate(frame, [0, 8], [0, 1], { extrapolateRight: 'clamp' });
  const fadeOut = interpolate(frame, [168, 180], [1, 0], { extrapolateLeft: 'clamp' });

  const glow = interpolate(Math.sin(frame / 14), [-1, 1], [0.55, 0.85]);

  const titleA = spring({ frame: frame - 12, fps, config: { damping: 14, stiffness: 90, mass: 0.7 } });
  const titleB = spring({ frame: frame - 32, fps, config: { damping: 14, stiffness: 90, mass: 0.7 } });
  const decoP = spring({ frame: frame - 56, fps, config: { damping: 14, stiffness: 100, mass: 0.7 } });
  const subR = spring({ frame: frame - 86, fps, config: { damping: 18, stiffness: 80, mass: 0.8 } });
  const tagR = spring({ frame: frame - 110, fps, config: { damping: 20, stiffness: 80, mass: 0.8 } });

  return (
    <AbsoluteFill
      style={{
        opacity: fadeIn * fadeOut,
        alignItems: 'center',
        justifyContent: 'center',
        gap: 28,
      }}
    >
      <div
        style={{
          position: 'absolute',
          top: '30%',
          left: '50%',
          transform: 'translate(-50%, -50%)',
          width: 900,
          height: 900,
          borderRadius: '50%',
          background: `radial-gradient(circle, rgba(245,179,46,${glow}) 0%, rgba(245,179,46,0) 70%)`,
        }}
      />

      <div
        style={{
          opacity: decoP,
          transform: `scale(${interpolate(decoP, [0, 1], [0.7, 1])})`,
          display: 'flex',
          alignItems: 'center',
          gap: 30,
        }}
      >
        <Wheat size={120} color={C.goldLight} strokeWidth={2} />
        <div
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 14,
            padding: '12px 28px',
            borderRadius: 999,
            background: 'rgba(255,90,54,0.85)',
            boxShadow: '0 6px 20px rgba(255,90,54,0.4)',
          }}
        >
          <Heart size={60} color={C.cream} fill={C.cream} strokeWidth={2} />
          <Home size={56} color={C.cream} strokeWidth={2.2} />
        </div>
        <Wheat size={120} color={C.goldLight} strokeWidth={2} style={{ transform: 'scaleX(-1)' }} />
      </div>

      <div style={{ fontFamily: FONT, textAlign: 'center', lineHeight: 1.18, letterSpacing: 8 }}>
        <div
          style={{
            fontSize: 116,
            fontWeight: 900,
            color: C.cream,
            opacity: titleA,
            transform: `translateY(${interpolate(titleA, [0, 1], [36, 0])}px)`,
            textShadow: '0 8px 30px rgba(0,0,0,0.55)',
          }}
        >
          {title}
        </div>
        {titleHighlight ? (
          <div
            style={{
              fontSize: 116,
              fontWeight: 900,
              color: C.gold,
              marginTop: 18,
              opacity: titleB,
              transform: `translateY(${interpolate(titleB, [0, 1], [36, 0])}px)`,
              textShadow: '0 8px 34px rgba(245,179,46,0.5)',
            }}
          >
            {titleHighlight}
          </div>
        ) : null}
      </div>

      <div
        style={{
          width: interpolate(subR, [0, 1], [0, 320]),
          height: 4,
          borderRadius: 2,
          background: `linear-gradient(90deg, transparent, ${C.gold}, transparent)`,
        }}
      />

      {subtitle ? (
        <div
          style={{
            opacity: subR,
            transform: `translateY(${interpolate(subR, [0, 1], [20, 0])}px)`,
            fontFamily: FONT,
            fontSize: 44,
            fontWeight: 700,
            color: C.creamDim,
            letterSpacing: 4,
          }}
        >
          {subtitle}
        </div>
      ) : null}

      {signOff ? (
        <div
          style={{
            opacity: tagR,
            transform: `translateY(${interpolate(tagR, [0, 1], [16, 0])}px) scale(${interpolate(tagR, [0, 1], [0.92, 1])})`,
            marginTop: 14,
            padding: '14px 40px',
            borderRadius: 999,
            background: 'rgba(255,246,227,0.12)',
            border: '2px solid rgba(255,246,227,0.35)',
            fontFamily: FONT,
            fontSize: 32,
            fontWeight: 700,
            color: C.cream,
            letterSpacing: 4,
          }}
        >
          {signOff}
        </div>
      ) : tag ? (
        <div
          style={{
            opacity: tagR,
            transform: `translateY(${interpolate(tagR, [0, 1], [16, 0])}px) scale(${interpolate(tagR, [0, 1], [0.92, 1])})`,
            marginTop: 14,
            padding: '14px 40px',
            borderRadius: 999,
            background: 'rgba(255,246,227,0.12)',
            border: '2px solid rgba(255,246,227,0.35)',
            fontFamily: FONT,
            fontSize: 32,
            fontWeight: 700,
            color: C.cream,
            letterSpacing: 4,
          }}
        >
          {tag}
        </div>
      ) : null}
    </AbsoluteFill>
  );
};
