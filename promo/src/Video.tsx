import React from 'react';
import {
  AbsoluteFill,
  Audio,
  Easing,
  Img,
  OffthreadVideo,
  Sequence,
  interpolate,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from 'remotion';
import { C, CARD_H, CARD_TOP, CARD_W, FONT, S, TEXT_LEFT, at, beatF } from './theme';

/* ============================================================
   通用
   ============================================================ */

const useRise = (delay: number, distance = 24) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const s = spring({
    frame: frame - delay,
    fps,
    config: { damping: 24, stiffness: 95, mass: 0.9 },
  });
  return {
    opacity: s,
    transform: `translateY(${interpolate(s, [0, 1], [distance, 0])}px)`,
  } as React.CSSProperties;
};

/** 顶部文案：米白段深字，黑段浅字 */
const Caption: React.FC<{
  main: string;
  sub?: string;
  mainSize?: number;
  dark?: boolean;
  delay?: number;
  subDelay?: number;
}> = ({ main, sub, mainSize = 88, dark = false, delay = 0, subDelay }) => {
  const m = useRise(delay);
  const s = useRise(subDelay ?? delay + 5, 16);
  return (
    <div
      style={{
        position: 'absolute',
        left: TEXT_LEFT,
        right: TEXT_LEFT,
        top: 96,
        fontFamily: FONT,
      }}
    >
      <div
        style={{
          ...m,
          fontSize: mainSize,
          fontWeight: 700,
          lineHeight: 1.15,
          color: dark ? C.darkText : C.lightText,
          letterSpacing: 1,
          whiteSpace: 'nowrap',
        }}
      >
        {main}
      </div>
      {sub ? (
        <div
          style={{
            ...s,
            marginTop: 10,
            fontSize: 40,
            fontWeight: 400,
            color: dark ? C.darkSub : C.lightSub,
            letterSpacing: 0.5,
            whiteSpace: 'nowrap',
          }}
        >
          {sub}
        </div>
      ) : null}
    </div>
  );
};

/** 手机卡片。米白段靠投影脱开，黑段靠一道极淡描边 */
const Card: React.FC<{
  src?: string;
  video?: string;
  playbackRate?: number;
  delay?: number;
  to?: number;
  width?: number;
  top?: number;
  dark?: boolean;
  driftY?: number;
  dur?: number;
}> = ({
  src,
  video,
  playbackRate = 1,
  delay = 0,
  to = 1.04,
  width = CARD_W,
  top = CARD_TOP,
  dark = false,
  driftY = 0,
  dur = 120,
}) => {
  const frame = useCurrentFrame();
  const enter = spring({
    frame: frame - delay,
    fps: useVideoConfig().fps,
    config: { damping: 26, stiffness: 80, mass: 1 },
  });
  const scale = interpolate(frame - delay, [0, dur], [1, to], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: Easing.inOut(Easing.quad),
  });
  const h = width / (720 / 1500);

  return (
    <div
      style={{
        position: 'absolute',
        left: (1080 - width) / 2,
        top,
        width,
        height: h,
        opacity: enter,
        transform: `translateY(${interpolate(enter, [0, 1], [36, 0]) + driftY}px) scale(${scale})`,
        transformOrigin: 'top center',
        borderRadius: 26,
        overflow: 'hidden',
        backgroundColor: dark ? '#111113' : '#fff',
        border: `1px solid ${dark ? 'rgba(255,255,255,0.10)' : 'rgba(20,16,12,0.07)'}`,
        boxShadow: dark
          ? '0 30px 80px rgba(0,0,0,0.55)'
          : '0 20px 56px rgba(20,16,12,0.16)',
      }}
    >
      {video ? (
        <OffthreadVideo
          src={staticFile(video)}
          playbackRate={playbackRate}
          muted
          style={{ width: '100%', height: '100%', objectFit: 'cover' }}
        />
      ) : (
        <Img src={staticFile(src!)} style={{ width: '100%', height: '100%' }} />
      )}
    </div>
  );
};

/* ============================================================
   B1 · 三个地方（8 拍，压在音乐静音段上）
   ============================================================ */

const B1: React.FC = () => {
  const frame = useCurrentFrame();
  const { dur } = at(S.b1);
  const out = interpolate(frame, [dur - 6, dur], [1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  const line = (text: string, from: number, size: number, dimAt?: number) => {
    const r = useRise(from, 26);
    const dim =
      dimAt === undefined
        ? 1
        : interpolate(frame, [dimAt, dimAt + 9], [1, 0.28], {
            extrapolateLeft: 'clamp',
            extrapolateRight: 'clamp',
          });
    return (
      <div
        style={{
          ...r,
          opacity: (r.opacity as number) * dim,
          fontFamily: FONT,
          fontSize: size,
          fontWeight: 700,
          lineHeight: 1.42,
          color: C.lightText,
          letterSpacing: 1,
          whiteSpace: 'nowrap',
        }}
      >
        {text}
      </div>
    );
  };

  return (
    <AbsoluteFill
      style={{
        backgroundColor: C.lightBg,
        justifyContent: 'center',
        alignItems: 'flex-start',
        paddingLeft: TEXT_LEFT,
        opacity: out,
      }}
    >
      {line('取件码在三个 App 里。', 0, 68, 30)}
      {line('快递在三个柜子里。', 30, 68, 60)}
      {line('你站在中间。', 60, 104)}
    </AbsoluteFill>
  );
};

/* ============================================================
   B2 · 登场（3 拍，落在 drop 上）
   ============================================================ */

const B2: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const icon = spring({ frame, fps, config: { damping: 15, stiffness: 130, mass: 0.7 } });
  const name = useRise(10);
  const tag = useRise(24, 14);

  return (
    <AbsoluteFill style={{ backgroundColor: C.lightBg, justifyContent: 'center', alignItems: 'center' }}>
      <div
        style={{
          width: 280,
          height: 280,
          borderRadius: 64,
          overflow: 'hidden',
          opacity: icon,
          transform: `scale(${interpolate(icon, [0, 1], [0.88, 1])})`,
          border: '1px solid rgba(20,16,12,0.10)',
          boxShadow: '0 18px 44px rgba(20,16,12,0.18)',
        }}
      >
        <Img src={staticFile('icon.png')} style={{ width: '100%', height: '100%' }} />
      </div>
      <div
        style={{
          ...name,
          marginTop: 46,
          fontFamily: FONT,
          fontSize: 90,
          fontWeight: 700,
          color: C.lightText,
          letterSpacing: 2,
          whiteSpace: 'nowrap',
        }}
      >
        卡皮巴拉快递助手
      </div>
      <div
        style={{
          ...tag,
          marginTop: 16,
          fontFamily: FONT,
          fontSize: 38,
          color: C.lightSub,
          letterSpacing: 2,
          whiteSpace: 'nowrap',
        }}
      >
        取快递，看一眼就行
      </div>
    </AbsoluteFill>
  );
};

/* ============================================================
   B3 · 按取件点分好（7 拍）
   ============================================================ */

const B3: React.FC = () => (
  <AbsoluteFill style={{ backgroundColor: C.lightBg }}>
    <Caption main="按取件点分好" sub="最急的那件，排在最上面" mainSize={86} subDelay={8} />
    <Card src="home.png" to={1.03} driftY={-10} />
  </AbsoluteFill>
);

/* ============================================================
   B4 · 现场模式：整卡 → 硬切特写
   ============================================================ */

const B4a: React.FC = () => (
  <AbsoluteFill style={{ backgroundColor: C.lightBg }}>
    <Caption main="站到柜前" sub="超时了，先替你算钱" mainSize={84} subDelay={7} />
    <Card src="onsite.png" to={1.05} />
  </AbsoluteFill>
);

const B4b: React.FC = () => {
  const frame = useCurrentFrame();
  const { dur } = at(S.b4b);
  const w = interpolate(frame, [0, dur], [1210, 1262], {
    extrapolateRight: 'clamp',
    easing: Easing.out(Easing.quad),
  });
  const h = w / (720 / 1500);
  const shift = 800 - h * 0.4;

  return (
    <AbsoluteFill style={{ backgroundColor: C.lightBg, overflow: 'hidden' }}>
      <Img
        src={staticFile('onsite.png')}
        style={{ position: 'absolute', left: (1080 - w) / 2, top: shift, width: w, height: h }}
      />
    </AbsoluteFill>
  );
};

/* ============================================================
   B5 · 三种码写法（9 拍，3 镜各 3 拍）
   ============================================================ */

const B5Shot: React.FC<{ src: string }> = ({ src }) => {
  const frame = useCurrentFrame();
  const scale = interpolate(frame, [0, beatF(3)], [1.01, 1.04], {
    extrapolateRight: 'clamp',
    easing: Easing.inOut(Easing.quad),
  });
  return (
    <AbsoluteFill style={{ backgroundColor: C.lightBg }}>
      <div
        style={{
          position: 'absolute',
          left: (1080 - CARD_W) / 2,
          top: 320,
          width: CARD_W,
          height: CARD_H,
          transform: `scale(${scale})`,
          transformOrigin: 'top center',
          borderRadius: 26,
          overflow: 'hidden',
          backgroundColor: '#fff',
          border: '1px solid rgba(20,16,12,0.07)',
          boxShadow: '0 20px 56px rgba(20,16,12,0.16)',
        }}
      >
        <Img src={staticFile(src)} style={{ width: '100%', height: '100%' }} />
      </div>
    </AbsoluteFill>
  );
};

const B5: React.FC = () => {
  const b = beatF(3);
  const sub = useRise(beatF(4.5), 14);
  return (
    <AbsoluteFill style={{ backgroundColor: C.lightBg }}>
      <Sequence from={0} durationInFrames={b}>
        <B5Shot src="cn3391.png" />
      </Sequence>
      <Sequence from={b} durationInFrames={b}>
        <B5Shot src="suffix.png" />
      </Sequence>
      <Sequence from={b * 2} durationInFrames={b}>
        <B5Shot src="nocode.png" />
      </Sequence>

      <div
        style={{
          position: 'absolute',
          left: TEXT_LEFT,
          top: 104,
          fontFamily: FONT,
          fontSize: 72,
          fontWeight: 700,
          color: C.lightText,
          letterSpacing: 1,
          whiteSpace: 'nowrap',
        }}
      >
        格子号 · 货架号 · 后五位
      </div>
      <div
        style={{
          ...sub,
          position: 'absolute',
          left: TEXT_LEFT,
          top: 198,
          fontFamily: FONT,
          fontSize: 38,
          color: C.lightSub,
          whiteSpace: 'nowrap',
        }}
      >
        认不出来，就说认不出来
      </div>
    </AbsoluteFill>
  );
};

/* ============================================================
   B55 · 门禁码：二维码现算（3 拍）
   ============================================================ */

const B55: React.FC = () => {
  const frame = useCurrentFrame();
  const blur = interpolate(frame, [0, 11], [8, 0], {
    extrapolateRight: 'clamp',
    easing: Easing.out(Easing.cubic),
  });
  const { dur } = at(S.b55);
  const scale = interpolate(frame, [0, dur], [1.0, 1.04], {
    extrapolateRight: 'clamp',
    easing: Easing.inOut(Easing.quad),
  });

  return (
    <AbsoluteFill style={{ backgroundColor: C.lightBg }}>
      <Caption main="这码是手机上现算的" sub="不联网 · 不截图 · 不上传" mainSize={82} subDelay={7} />
      <div
        style={{
          position: 'absolute',
          left: (1080 - CARD_W) / 2,
          top: 320,
          width: CARD_W,
          height: CARD_H,
          transform: `scale(${scale})`,
          transformOrigin: 'top center',
          borderRadius: 26,
          overflow: 'hidden',
          backgroundColor: '#fff',
          border: '1px solid rgba(20,16,12,0.07)',
          boxShadow: '0 20px 56px rgba(20,16,12,0.16)',
        }}
      >
        <Img
          src={staticFile('kaimen.png')}
          style={{
            width: '100%',
            height: '100%',
            filter: `blur(${blur}px)`,
            transform: `scale(${1 + blur / 320})`,
          }}
        />
      </div>
    </AbsoluteFill>
  );
};

/* ============================================================
   B6 · 取完就走（7 拍：3 拍真实点击 + 4 拍全取完）
   ============================================================ */

/* B6 前半：界面铺满画幅，一个字都不给——让手势自己说话 */
const B6Hero: React.FC = () => {
  const frame = useCurrentFrame();
  const b = beatF(3);
  const scale = interpolate(frame, [0, b], [1.0, 1.035], {
    extrapolateRight: 'clamp',
    easing: Easing.inOut(Easing.quad),
  });
  return (
    <AbsoluteFill style={{ backgroundColor: '#fff', overflow: 'hidden' }}>
      <OffthreadVideo
        src={staticFile('pick.mp4')}
        playbackRate={45 / b}
        muted
        style={{
          position: 'absolute',
          left: 0,
          top: -165,
          width: 1080,
          height: 2250,
          transform: `scale(${scale})`,
          transformOrigin: 'center center',
        }}
      />
    </AbsoluteFill>
  );
};

const B6: React.FC = () => {
  const frame = useCurrentFrame();
  const b = beatF(3);
  const l1 = useRise(b, 22);
  const l2 = useRise(b + 30, 22);
  const { dur } = at(S.b6);
  const out = interpolate(frame, [dur - 6, dur], [1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  return (
    <AbsoluteFill style={{ backgroundColor: C.lightBg, opacity: out }}>
      <Sequence from={0} durationInFrames={b}>
        <B6Hero />
      </Sequence>
      <Sequence from={b} durationInFrames={dur - b}>
        <AbsoluteFill style={{ backgroundColor: C.lightBg }}>
          <Card src="alldone.png" top={430} width={700} to={1.03} dur={dur - b} />
        </AbsoluteFill>
      </Sequence>

      <div
        style={{
          position: 'absolute',
          left: TEXT_LEFT,
          top: 140,
          fontFamily: FONT,
          fontSize: 84,
          fontWeight: 700,
          lineHeight: 1.32,
          color: C.lightText,
          letterSpacing: 1,
        }}
      >
        <div style={{ ...l1, whiteSpace: 'nowrap' }}>取完这件，</div>
        <div style={{ ...l2, whiteSpace: 'nowrap' }}>下一件已经在等你</div>
      </div>
    </AbsoluteFill>
  );
};

/* ============================================================
   B7 · 关灯（9 拍：3 拍切深色 + 6 拍三行）
   ============================================================ */

const B7: React.FC = () => {
  const frame = useCurrentFrame();
  const switchDur = beatF(3);
  // 界面在 0.2 秒左右转深色，整片的底色跟着翻
  const flip = interpolate(frame, [4, 10], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const bg = frame < switchDur ? mix(C.lightBg, C.darkBg, flip) : C.darkBg;

  const rows: Array<[string, number, number]> = [
    ['离线解析', switchDur, 54],
    ['不注册，不联网', switchDur + 30, 62],
    ['数据只在这台手机上', switchDur + 60, 74],
  ];

  return (
    <AbsoluteFill style={{ backgroundColor: bg }}>
      <Sequence from={0} durationInFrames={switchDur}>
        <Card
          video="switch.mp4"
          playbackRate={63 / switchDur}
          top={360}
          width={640}
          to={1.02}
          dark
          dur={switchDur}
        />
      </Sequence>

      <div
        style={{
          position: 'absolute',
          left: TEXT_LEFT,
          right: TEXT_LEFT,
          top: 0,
          bottom: 0,
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
        }}
      >
        {rows.map(([text, from, size]) => {
          const r = useRise(from, 18);
          return (
            <div
              key={text}
              style={{
                ...r,
                fontFamily: FONT,
                fontSize: size,
                fontWeight: 700,
                lineHeight: 1.9,
                color: size === 74 ? C.darkText : C.darkSub,
                letterSpacing: 1,
                whiteSpace: 'nowrap',
              }}
            >
              {text}
            </div>
          );
        })}
      </div>
    </AbsoluteFill>
  );
};

/** 两色线性混合 */
function mix(a: string, b: string, t: number) {
  const pa = hex(a);
  const pb = hex(b);
  const c = pa.map((v, i) => Math.round(v + (pb[i] - v) * t));
  return `rgb(${c[0]},${c[1]},${c[2]})`;
}
function hex(h: string) {
  return [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
}

/* ============================================================
   B8 · 品牌（7 拍，黑底）
   ============================================================ */

const B8: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const icon = spring({ frame, fps, config: { damping: 17, stiffness: 110, mass: 0.8 } });
  const name = useRise(12);
  const tag = useRise(36, 14);
  const cta = useRise(60, 10);
  const { dur } = at(S.b8);
  const out = interpolate(frame, [dur - 8, dur], [1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  return (
    <AbsoluteFill
      style={{
        backgroundColor: C.darkBg,
        justifyContent: 'center',
        alignItems: 'center',
        opacity: out,
      }}
    >
      <div
        style={{
          width: 210,
          height: 210,
          borderRadius: 48,
          overflow: 'hidden',
          opacity: icon,
          transform: `scale(${interpolate(icon, [0, 1], [0.9, 1])})`,
        }}
      >
        <Img src={staticFile('icon.png')} style={{ width: '100%', height: '100%' }} />
      </div>
      <div
        style={{
          ...name,
          marginTop: 40,
          fontFamily: FONT,
          fontSize: 78,
          fontWeight: 700,
          color: C.darkText,
          letterSpacing: 2,
          whiteSpace: 'nowrap',
        }}
      >
        卡皮巴拉快递助手
      </div>
      <div
        style={{
          ...tag,
          marginTop: 16,
          fontFamily: FONT,
          fontSize: 38,
          color: C.darkSub,
          letterSpacing: 2,
          whiteSpace: 'nowrap',
        }}
      >
        取快递，看一眼就行
      </div>
      <div
        style={{
          ...cta,
          marginTop: 34,
          fontFamily: FONT,
          fontSize: 25,
          color: '#57534E',
          letterSpacing: 1,
          whiteSpace: 'nowrap',
        }}
      >
        GitHub · GPL-3.0 · 无广告
      </div>
    </AbsoluteFill>
  );
};

/* ============================================================
   主合成
   ============================================================ */

const Seq: React.FC<{ range: readonly [number, number]; children: React.ReactNode }> = ({
  range,
  children,
}) => {
  const { from, dur } = at(range);
  return (
    <Sequence from={from} durationInFrames={dur}>
      {children}
    </Sequence>
  );
};

export const Video: React.FC = () => (
  <AbsoluteFill style={{ backgroundColor: C.darkBg }}>
    <Audio src={staticFile('bgm.mp3')} />

    <Seq range={S.b1}><B1 /></Seq>
    <Seq range={S.b2}><B2 /></Seq>
    <Seq range={S.b3}><B3 /></Seq>
    <Seq range={S.b4a}><B4a /></Seq>
    <Seq range={S.b4b}><B4b /></Seq>
    <Seq range={S.b5}><B5 /></Seq>
    <Seq range={S.b55}><B55 /></Seq>
    <Seq range={S.b6}><B6 /></Seq>
    <Seq range={S.b7}><B7 /></Seq>
    <Seq range={S.b8}><B8 /></Seq>
  </AbsoluteFill>
);
