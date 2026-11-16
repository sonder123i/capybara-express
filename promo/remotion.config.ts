import { Config } from '@remotion/cli/config';

Config.setVideoImageFormat('jpeg');
Config.setOverwriteOutput(true);
Config.setConcurrency(4);
Config.setChromiumOpenGlRenderer('angle');

// Windows：用系统已装的 Edge（Chromium 内核）替代需联网下载的 Chrome Headless Shell。
// Mac / Linux 用户直接删掉下面这两行即可（Remotion 会自动用系统 Chrome）。
Config.setBrowserExecutable(
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
);
