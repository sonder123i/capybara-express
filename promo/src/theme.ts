export const WIDTH = 1080;
export const HEIGHT = 1920;
export const FPS = 30;

/** 95 BPM：一拍 = 18.9474 帧；一小节（4 拍）= 75.79 帧 */
export const BEAT = (60 / 95) * FPS;
export const beatF = (beats: number) => Math.round(beats * BEAT);

/** 米白段（B1–B6）与黑段（B7–B8） */
export const C = {
  lightBg: '#EFEDE8',
  lightText: '#12100E',
  lightSub: '#5C5852',
  lightFaint: '#8C877E',
  darkBg: '#0A0A0B',
  darkText: '#F5F5F4',
  darkSub: '#A8A29E',
} as const;

export const FONT =
  '"Microsoft YaHei", "PingFang SC", "Noto Sans SC", "Source Han Sans SC", sans-serif';

/** 时间轴以「拍」计，渲染时换算成帧 */
export const S = {
  total: beatF(60), // 60 拍 = 1137 帧 = 37.9s
  b1: [0, 8],
  b2: [8, 11],
  b3: [11, 18],
  b4a: [18, 21],
  b4b: [21, 25],
  b5: [25, 34],
  b55: [34, 37],
  b6: [37, 44],
  b7: [44, 53],
  b8: [53, 60],
} as const;

export const at = (range: readonly [number, number]) => ({
  from: beatF(range[0]),
  dur: beatF(range[1]) - beatF(range[0]),
});

/** Root.tsx 用的总帧数 */
export const TOTAL_FRAMES = S.total;

/** 卡片版式：宽 720，屏比 720:1500 */
export const CARD_W = 720;
export const CARD_H = 1500;
export const CARD_TOP = 320;
export const TEXT_LEFT = 100;
