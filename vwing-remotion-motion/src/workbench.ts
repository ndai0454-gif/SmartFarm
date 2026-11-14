import React, {createElement, type FC} from 'react';
import {
  CAPTIONS,
  FLASH_CUTS,
  SCENES,
  SFX,
  SceneView,
  VWING_FPS,
  VWING_SHOTS,
  VWING_TOTAL,
  VWingMotionPromo,
  sfxDuration,
} from './VWingMotionPromo';
import {FlashCut} from './shotcraft/ShotcraftKit';

type Field = Record<string, unknown> & {
  type: string;
  key: string;
  label: string;
  default: unknown;
};

const text = (key: string, label: string, def: string): Field => ({
  type: 'text',
  key,
  label,
  default: def,
});

const textarea = (key: string, label: string, def: string): Field => ({
  type: 'textarea',
  key,
  label,
  default: def,
});

// Prop-driven scene unit wrapper for Motion Workbench Inspector
const makeSceneUnit = (sceneIndex: number): FC<Record<string, unknown>> => {
  const sc = SCENES[sceneIndex];
  const Comp: FC<Record<string, unknown>> = (props) =>
    createElement(SceneView, {
      scene: sc,
      sceneIndex,
      totalScenes: SCENES.length,
      hideCaption: true,
      overrides: {
        title: (props.title as string) || sc.title,
        subtitle: (props.subtitle as string) || sc.subtitle,
        badge: (props.badge as string) || sc.badge,
      },
    });
  Comp.displayName = `VWingScene_${sc.id}`;
  return Comp;
};

export const FlashUnit: FC<{duration?: number}> = ({duration = 10}) =>
  createElement(FlashCut, {duration});
FlashUnit.displayName = 'VWingFlashCut';

export const CaptionUnit: FC<{text?: string; bottom?: number}> = ({
  text: capText = '',
  bottom = 28,
}) =>
  createElement(
    'div',
    {
      style: {
        position: 'absolute',
        left: 60,
        bottom,
        width: 1200,
        background: 'rgba(6, 18, 14, 0.92)',
        border: '1.5px solid rgba(82, 214, 148, 0.7)',
        borderRadius: 16,
        padding: '14px 24px',
        fontSize: 22.5,
        fontWeight: 700,
        lineHeight: 1.4,
        color: '#FFFFFF',
        fontFamily: '"Segoe UI", Inter, Roboto, sans-serif',
        boxShadow: '0 12px 32px rgba(0,0,0,0.45)',
        pointerEvents: 'none',
      },
    },
    capText,
  );
CaptionUnit.displayName = 'VWingCaption';

export const WORKBENCH = {
  name: 'VWing SmartFarm · Shotcraft Promo',
  fps: VWING_FPS,
  width: 1920,
  height: 1080,
  total: VWING_TOTAL,
  background: '#04100C',
  revision: 'vwing-shotcraft-2026-v1',
  shots: SCENES.map((sc, idx) => {
    const info = VWING_SHOTS[sc.id];
    const UnitComp = makeSceneUnit(idx);
    return {
      id: sc.id,
      label: `S${idx + 1} · ${sc.title.slice(0, 28)}`,
      from: info.from,
      duration: info.duration,
      component: UnitComp,
      props: {
        title: sc.title,
        subtitle: sc.subtitle,
        badge: sc.badge,
      },
      schema: [
        text('title', 'Tiêu đề phân cảnh (Title)', sc.title),
        text('subtitle', 'Phụ đề kỹ thuật (Subtitle)', sc.subtitle),
        text('badge', 'Nhãn nổi bật (Highlight Badge)', sc.badge),
      ],
      cardId: sc.id,
      cardName: `S${idx + 1} · ${sc.shotcraftRecipe}`,
      accent: '#10b981',
    };
  }),
  transitions: FLASH_CUTS.map((cut, i) => ({
    id: `flash-${i + 1}`,
    label: `FlashCut @${cut}f`,
    from: Math.max(0, cut - 5),
    duration: 10,
    component: FlashUnit as FC<Record<string, unknown>>,
    durationProp: 'duration',
    cardId: 'vwing-flash-cut',
    cardName: 'Chuyển cảnh FlashCut Ngọc Lục Bảo',
  })),
  captions: CAPTIONS.map((c, i) => ({
    id: `caption-${i + 1}`,
    label: `Phụ đề S${i + 1}`,
    from: c.from,
    duration: c.duration,
    component: CaptionUnit as FC<Record<string, unknown>>,
    props: {text: c.text, bottom: 28},
    schema: [textarea('text', 'Nội dung thuyết minh tiếng Việt', c.text)],
    cardId: 'vwing-caption',
    cardName: 'Thanh Phụ Đề Thuyết Minh',
  })),
  sfx: [
    {
      from: 0,
      duration: VWING_TOTAL,
      src: 'audio/narration_vi.mp3',
      volume: 0.96,
      label: '🎙️ Thuyết minh Nam Minh G2P',
    },
    ...SFX.map((s) => ({
      from: s.from,
      duration: sfxDuration(s),
      src: `audio/${s.src}`,
      volume: s.volume,
      label: s.note,
    })),
  ],
  bgm: [
    {
      from: 0,
      duration: VWING_TOTAL,
      src: 'audio/bgm-tech-house.mp3',
      volume: 0.12,
      label: '🎵 BGM Tech House (124 BPM)',
    },
  ],
  order: ['transitions', 'captions', 'overlays'] as const,
  original: VWingMotionPromo as FC<Record<string, unknown>>,
};
