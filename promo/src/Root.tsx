import React from 'react';
import { Composition } from 'remotion';
import { Video } from './Video';
import { TOTAL_FRAMES, WIDTH, HEIGHT, FPS } from './theme';

// 主合成。需要多版本（如同时出 9:16 和 16:9）时，复制 <Composition> 并改 width/height 即可。
export const RemotionRoot: React.FC = () => {
  return (
    <>
      <Composition
        id="Main"
        component={Video}
        durationInFrames={TOTAL_FRAMES}
        fps={FPS}
        width={WIDTH}
        height={HEIGHT}
      />
    </>
  );
};
