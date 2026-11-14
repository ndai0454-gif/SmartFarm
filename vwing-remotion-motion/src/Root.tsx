import React from 'react';
import {Composition} from 'remotion';
import {VWingMotionPromo, SingleSceneShowcase, VWING_FPS, VWING_TOTAL} from './VWingMotionPromo';

export const RemotionRoot: React.FC = () => {
  // 90.85s @ 24 fps = 2181 frames (7 scenes including Dual AI 70a-70f + Multi-platform Phone + Tablet TB321FU)
  return (
    <>
      <Composition
        id="VWingMotionPromo"
        component={VWingMotionPromo}
        durationInFrames={VWING_TOTAL}
        fps={VWING_FPS}
        width={1920}
        height={1080}
        defaultProps={{
          bgm: true,
          sfx: true,
          voiceover: true,
          plate: false,
        }}
      />
      <Composition
        id="VWingShotcraftNoBGM"
        component={VWingMotionPromo}
        durationInFrames={VWING_TOTAL}
        fps={VWING_FPS}
        width={1920}
        height={1080}
        defaultProps={{
          bgm: false,
          sfx: true,
          voiceover: true,
          plate: false,
        }}
      />
      <Composition
        id="Screen02-PricingBannerMotion"
        component={SingleSceneShowcase}
        durationInFrames={360}
        fps={VWING_FPS}
        width={1920}
        height={1080}
        defaultProps={{sceneIndex: 1}}
      />
      <Composition
        id="Screen03-QrLaserMotion"
        component={SingleSceneShowcase}
        durationInFrames={300}
        fps={VWING_FPS}
        width={1920}
        height={1080}
        defaultProps={{sceneIndex: 2}}
      />
      <Composition
        id="Screen04-GeeRadarMotion"
        component={SingleSceneShowcase}
        durationInFrames={300}
        fps={VWING_FPS}
        width={1920}
        height={1080}
        defaultProps={{sceneIndex: 3}}
      />
      <Composition
        id="Screen05-DualAi70a70fMotion"
        component={SingleSceneShowcase}
        durationInFrames={258}
        fps={VWING_FPS}
        width={1920}
        height={1080}
        defaultProps={{sceneIndex: 4}}
      />
      <Composition
        id="Screen07-MultiPlatformPhoneTablet"
        component={SingleSceneShowcase}
        durationInFrames={360}
        fps={VWING_FPS}
        width={1920}
        height={1080}
        defaultProps={{sceneIndex: 6}}
      />
    </>
  );
};
