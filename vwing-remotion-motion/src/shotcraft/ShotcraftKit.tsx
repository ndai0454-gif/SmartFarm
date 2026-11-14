import React from 'react';
import {AbsoluteFill, Easing, Img, interpolate, staticFile, useCurrentFrame} from 'remotion';

// ─────────────────────────────────────────────────────────────────────────────
// 1. DETERMINISTIC HELPERS (video-shotcraft/assets/lib/helpers)
// ─────────────────────────────────────────────────────────────────────────────
export const mulberry32 = (seed: number) => {
  let a = seed | 0;
  return () => {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
};

export const handheld = (_frame: number, _amp = 0): [number, number, number] => [
  0,
  0,
  0,
];

export const velocityAt = (
  posAt: (f: number) => {x: number; y: number},
  frame: number,
  dt = 0.5,
): {vx: number; vy: number; speed: number; direction: number} => {
  const before = posAt(frame - dt);
  const after = posAt(frame + dt);
  const vx = (after.x - before.x) / (2 * dt);
  const vy = (after.y - before.y) / (2 * dt);
  return {vx, vy, speed: Math.hypot(vx, vy), direction: Math.atan2(vy, vx)};
};

export const lagged = <T,>(stateAt: (f: number) => T, frame: number, delayFrames: number): T =>
  stateAt(frame - delayFrames);

export const dampedSettle = (_t: number, _freq: number, _damping: number): number => 0;

// ─────────────────────────────────────────────────────────────────────────────
// 2. DIGIT ROLL ODOMETER (video-shotcraft/assets/lib/DigitRoll.tsx)
// ─────────────────────────────────────────────────────────────────────────────
const DIGITS = '0123456789';

export const DigitRoll: React.FC<{
  value: string;
  delay?: number;
  fontSize?: number;
  color?: string;
}> = ({value, delay = 0, fontSize = 23, color = '#FDE047'}) => {
  const frame = useCurrentFrame();
  const lineH = Math.round(fontSize * 1.18);
  return (
    <span style={{display: 'inline-flex', overflow: 'hidden', height: lineH, verticalAlign: 'bottom'}}>
      {value.split('').map((ch, i) => {
        const target = DIGITS.indexOf(ch);
        if (target < 0) {
          return (
            <span key={i} style={{fontSize, lineHeight: `${lineH}px`, color, fontWeight: 800}}>
              {ch}
            </span>
          );
        }
        const t = interpolate(frame, [delay + i * 3, delay + i * 3 + 18], [0, 1], {
          extrapolateLeft: 'clamp',
          extrapolateRight: 'clamp',
          easing: Easing.bezier(0.25, 0.8, 0.25, 1),
        });
        const offset = (10 + target) * t * lineH;
        return (
          <span key={i} style={{display: 'inline-block', height: lineH}}>
            <span style={{display: 'block', transform: `translateY(${-offset}px)`}}>
              {(DIGITS + DIGITS).split('').map((d, j) => (
                <span
                  key={j}
                  style={{
                    display: 'block',
                    fontSize,
                    lineHeight: `${lineH}px`,
                    color,
                    fontWeight: 800,
                    fontVariantNumeric: 'tabular-nums',
                  }}
                >
                  {d}
                </span>
              ))}
            </span>
          </span>
        );
      })}
    </span>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// 3. FLASH CUT TRANSITION (video-shotcraft/assets/lib/FlashCut.tsx)
// ─────────────────────────────────────────────────────────────────────────────
export const FlashCut: React.FC<{duration?: number; tint?: string}> = ({
  duration = 10,
  tint = 'rgba(167, 243, 208, 0.92)',
}) => {
  const frame = useCurrentFrame();
  const o = interpolate(frame, [0, duration * 0.4, duration], [0, 0.82, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  return (
    <AbsoluteFill
      style={{
        pointerEvents: 'none',
        opacity: o,
        background: `radial-gradient(ellipse at 50% 45%, ${tint}, rgba(254, 240, 138, 0.45) 55%, transparent 82%)`,
      }}
    />
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// 4. 2.5D PAGE CAMERA (video-shotcraft/assets/lib/PageCam.tsx)
// ─────────────────────────────────────────────────────────────────────────────
export type CamKey = {
  frame: number;
  cx: number;
  cy: number;
  zoom: number;
  rotX?: number;
  rotY?: number;
  rotZ?: number;
  persp?: number;
};

const lerp = (a: number, b: number, t: number) => a + (b - a) * t;

export const PageCam: React.FC<{
  src: string;
  pageW?: number;
  pageH: number;
  keys: CamKey[];
  children?: React.ReactNode;
  blur?: number;
  frame?: number;
}> = ({src, pageW = 1080, pageH, keys, children, blur = 0, frame: frameProp}) => {
  const ownFrame = useCurrentFrame();
  const frame = frameProp ?? ownFrame;
  let a = keys[0];
  let b = keys[keys.length - 1];
  for (let i = 0; i < keys.length - 1; i++) {
    if (frame >= keys[i].frame && frame <= keys[i + 1].frame) {
      a = keys[i];
      b = keys[i + 1];
      break;
    }
  }
  const t =
    a.frame === b.frame
      ? 1
      : interpolate(frame, [a.frame, b.frame], [0, 1], {
          extrapolateLeft: 'clamp',
          extrapolateRight: 'clamp',
          easing: Easing.bezier(0.33, 0, 0.15, 1),
        });
  const cx = lerp(a.cx, b.cx, t);
  const cy = lerp(a.cy, b.cy, t);
  const zoom = lerp(a.zoom, b.zoom, t);
  const rotX = lerp(a.rotX ?? 0, b.rotX ?? 0, t);
  const rotY = lerp(a.rotY ?? 0, b.rotY ?? 0, t);
  const rotZ = lerp(a.rotZ ?? 0, b.rotZ ?? 0, t);
  const persp = lerp(a.persp ?? 1400, b.persp ?? 1400, t);

  return (
    <AbsoluteFill style={{overflow: 'hidden'}}>
      <div
        style={{
          position: 'absolute',
          inset: 0,
          perspective: `${persp * zoom}px`,
          perspectiveOrigin: '50% 50%',
        }}
      >
        <div
          style={{
            position: 'absolute',
            width: pageW,
            height: pageH,
            zoom,
            transform: `translate(${540 / zoom - cx}px, ${446 / zoom - cy}px) rotateY(${rotY}deg) rotateX(${rotX}deg) rotateZ(${rotZ}deg)`,
            transformOrigin: `${cx}px ${cy}px`,
            transformStyle: 'preserve-3d',
            filter: blur > 0 ? `blur(${blur}px)` : undefined,
          }}
        >
          <Img src={staticFile(src)} style={{position: 'absolute', width: pageW, height: pageH}} />
          {children}
        </div>
      </div>
    </AbsoluteFill>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// 5. DETERMINISTIC STAGE PARTICLES & SPOTLIGHT CONE
//    (spotlight-hero-card + outro-group-photo-launch)
// ─────────────────────────────────────────────────────────────────────────────
const DUST = Array.from({length: 20}, (_, i) => ({
  x: (i * 439 + 137) % 1920,
  y0: (i * 613 + 271) % 1080,
  rise: 0.34 + (i % 5) * 0.12,
  swayAmp: 9 + (i % 4) * 5,
  swayFreq: 0.022 + (i % 3) * 0.008,
  phase: (i * 0.83) % (Math.PI * 2),
  size: 2.6 + (i % 3) * 1.0,
  opacity: 0.32 + ((i * 7) % 5) * 0.08,
}));

const BOKEH = Array.from({length: 6}, (_, i) => ({
  x: (i * 701 + 260) % 1920,
  y0: (i * 389 + 640) % 1080,
  rise: 0.55 + (i % 3) * 0.2,
  size: 16 + (i % 3) * 8,
  opacity: 0.09 + (i % 2) * 0.05,
}));

export const ShotcraftStageAtmosphere: React.FC<{
  frame: number;
  spotTargetX?: number;
  spotTargetY?: number;
  accentRgb?: string;
}> = ({frame, spotTargetX = 1480, spotTargetY = 480, accentRgb = '110, 231, 183'}) => {
  const lamp = interpolate(frame, [0, 3, 5, 9], [0, 1, 0.72, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const sx = spotTargetX + 360;
  const sy = -620;
  const coneW = 360;
  const cone = `${sx - 28}px ${sy}px, ${sx + 28}px ${sy}px, ${spotTargetX + coneW}px ${spotTargetY + 420}px, ${spotTargetX - coneW}px ${spotTargetY + 420}px`;

  return (
    <AbsoluteFill style={{pointerEvents: 'none'}}>
      {/* Volumetric Spotlight Cone */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          opacity: 0.22 * lamp,
          clipPath: `polygon(${cone})`,
          background: `linear-gradient(170deg, rgba(${accentRgb}, 0.45) 0%, rgba(253, 224, 71, 0.14) 55%, transparent 95%)`,
        }}
      />

      {/* Deterministic Rising Dust Motes */}
      {DUST.map((d, i) => {
        const y = (((d.y0 - frame * d.rise) % 1080) + 1080) % 1080;
        const x = d.x + Math.sin(frame * d.swayFreq + d.phase) * d.swayAmp;
        return (
          <div
            key={`dust-${i}`}
            style={{
              position: 'absolute',
              left: x,
              top: y,
              width: d.size,
              height: d.size,
              borderRadius: '50%',
              background: i % 2 === 0 ? '#FEF08A' : '#A7F3D0',
              opacity: d.opacity * lamp,
              boxShadow: `0 0 ${d.size * 3}px rgba(${accentRgb}, 0.75)`,
            }}
          />
        );
      })}

      {/* Foreground Soft Bokeh Orbs */}
      {BOKEH.map((b, i) => {
        const y = (((b.y0 - frame * b.rise) % 1080) + 1080) % 1080;
        return (
          <div
            key={`bokeh-${i}`}
            style={{
              position: 'absolute',
              left: b.x,
              top: y,
              width: b.size,
              height: b.size,
              borderRadius: '50%',
              background: `radial-gradient(circle, rgba(${accentRgb}, 0.55) 0%, transparent 72%)`,
              opacity: b.opacity * lamp,
              filter: 'blur(2px)',
            }}
          />
        );
      })}
    </AbsoluteFill>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// 6. PERIMETER SPECULAR BEAM (spotlight-hero-card double-lap contour scan)
// ─────────────────────────────────────────────────────────────────────────────
export const ShotcraftPerimeterBeam: React.FC<{
  width: number;
  height: number;
  radius?: number;
  frame: number;
}> = ({width, height, radius = 20, frame}) => {
  const perim = 2 * (width + height);
  const lapFrame = frame % 72;
  const prog = interpolate(lapFrame, [4, 48], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: Easing.bezier(0.35, 0, 0.2, 1),
  });
  const op = interpolate(lapFrame, [4, 10, 42, 52], [0, 1, 1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  if (op <= 0.01) return null;
  const segLen = perim * 0.24;
  const offset = -prog * perim;

  return (
    <svg
      width={width}
      height={height}
      style={{position: 'absolute', inset: 0, pointerEvents: 'none', opacity: op}}
    >
      <rect
        x={1.5}
        y={1.5}
        width={width - 3}
        height={height - 3}
        rx={radius}
        fill="none"
        stroke="#FEF08A"
        strokeWidth={3.2}
        strokeDasharray={`${segLen} ${perim}`}
        strokeDashoffset={offset}
        strokeLinecap="round"
      />
    </svg>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// 7. SCAN BRACKET SWEEP (demos/effects/scan-bracket-sweep/ScanBracketSweep.tsx)
// ─────────────────────────────────────────────────────────────────────────────
export const ShotcraftScanBracketOverlay: React.FC<{
  x: number;
  y: number;
  w: number;
  h: number;
  frame: number;
  durationInFrames: number;
  scanRgb?: string;
}> = ({x, y, w, h, frame, durationInFrames, scanRgb = '74, 222, 128'}) => {
  const t = Math.min(1, Math.max(0, frame / Math.max(1, durationInFrames)));
  const PASSES = 4;
  const raw = t * PASSES;
  const pi = Math.min(PASSES - 1, Math.floor(raw));
  const local = Math.min(1, Math.max(0, (raw - pi) / 0.88));
  const dir = pi % 2 === 0 ? 1 : -1;
  const prog = 0.5 - Math.cos(Math.PI * local) / 2;
  const scanY = dir > 0 ? prog * h : h - prog * h;
  const speed = Math.sin(Math.PI * local);
  const tailH = Math.max(4, 38 * Math.pow(speed, 0.75));

  const bracketIn = interpolate(frame, [2, 14], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: Easing.out(Easing.cubic),
  });
  const pad = (1 - bracketIn) * 14;
  const cs = 22;

  return (
    <g transform={`translate(${x}, ${y})`}>
      {/* 4 L-shaped Corner Viewfinder Brackets with Overshoot */}
      <path
        d={`M ${-pad} ${cs - pad} V ${-pad} H ${cs - pad}`}
        fill="none"
        stroke="#FEF08A"
        strokeWidth={3.2}
        strokeLinecap="round"
      />
      <path
        d={`M ${w - cs + pad} ${-pad} H ${w + pad} V ${cs - pad}`}
        fill="none"
        stroke="#FEF08A"
        strokeWidth={3.2}
        strokeLinecap="round"
      />
      <path
        d={`M ${w + pad} ${h - cs + pad} V ${h + pad} H ${w - cs + pad}`}
        fill="none"
        stroke="#FEF08A"
        strokeWidth={3.2}
        strokeLinecap="round"
      />
      <path
        d={`M ${cs - pad} ${h + pad} H ${-pad} V ${h - cs + pad}`}
        fill="none"
        stroke="#FEF08A"
        strokeWidth={3.2}
        strokeLinecap="round"
      />

      {/* Velocity-scaled Trailing Scan Beam */}
      <defs>
        <linearGradient id="shotcraftScanGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={`rgba(${scanRgb}, ${dir > 0 ? '0' : '0.45'})`} />
          <stop offset="100%" stopColor={`rgba(${scanRgb}, ${dir > 0 ? '0.45' : '0'})`} />
        </linearGradient>
      </defs>
      <rect
        x={4}
        y={dir > 0 ? Math.max(0, scanY - tailH) : scanY}
        width={w - 8}
        height={tailH}
        fill="url(#shotcraftScanGrad)"
      />
      <line
        x1={2}
        y1={scanY}
        x2={w - 2}
        y2={scanY}
        stroke={`rgb(${scanRgb})`}
        strokeWidth={3}
        strokeLinecap="round"
      />
    </g>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// 8. LIVE VOICE WAVEFORM CAPSULE (demos/interaction/voice-waveform-live)
// ─────────────────────────────────────────────────────────────────────────────
const noiseAt = (x: number) => {
  const i = Math.floor(x);
  const fr = x - i;
  const a = mulberry32(i * 7919 + 13)();
  const b = mulberry32((i + 1) * 7919 + 13)();
  const s = fr * fr * (3 - 2 * fr);
  return a + (b - a) * s;
};

export const ShotcraftVoiceWaveformPill: React.FC<{
  frame: number;
  channelLabel: string;
}> = ({frame, channelLabel}) => {
  const N_BARS = 28;
  const bars = Array.from({length: N_BARS}, (_, i) => {
    const sampleT = frame - (N_BARS - 1 - i) * 1.4;
    const syllable = 0.45 + 0.55 * noiseAt(sampleT / 4.2 + 200);
    const center = Math.pow(Math.sin((i / (N_BARS - 1)) * Math.PI), 0.8);
    const jitter = 0.35 + 0.65 * noiseAt(sampleT * 1.5 + i * 0.17);
    return Math.max(4, syllable * center * jitter * 26);
  });

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        background: 'rgba(8, 30, 24, 0.94)',
        border: '1.5px solid #4ADE80',
        borderRadius: 999,
        padding: '6px 16px',
        boxShadow: '0 8px 24px rgba(16, 185, 129, 0.28)',
      }}
    >
      <div
        style={{
          width: 10,
          height: 10,
          borderRadius: '50%',
          background: '#4ADE80',
          boxShadow: '0 0 10px #4ADE80',
        }}
      />
      <span style={{fontSize: 13.5, fontWeight: 800, color: '#A7F3D0', whiteSpace: 'nowrap'}}>
        {channelLabel}
      </span>
      <div style={{display: 'flex', alignItems: 'center', gap: 2.5, height: 26}}>
        {bars.map((bh, idx) => (
          <div
            key={idx}
            style={{
              width: 3,
              height: bh,
              borderRadius: 2,
              background: idx > N_BARS - 8 ? '#FDE047' : '#34D399',
            }}
          />
        ))}
      </div>
    </div>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// 9. SHOTCRAFT RECIPE HUD BADGE (Top-Right Telemetry Tag)
// ─────────────────────────────────────────────────────────────────────────────
export const ShotcraftRecipeHud: React.FC<{
  recipeName: string;
  beatNumber: number;
  bpm?: number;
}> = ({recipeName, beatNumber, bpm = 124}) => {
  return (
    <div
      style={{
        position: 'absolute',
        right: 36,
        bottom: 28,
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        background: 'rgba(7, 24, 20, 0.92)',
        border: '1.5px solid rgba(253, 224, 71, 0.65)',
        borderRadius: 999,
        padding: '8px 18px',
        boxShadow: '0 8px 24px rgba(0,0,0,0.45)',
      }}
    >
      <span
        style={{
          width: 8,
          height: 8,
          borderRadius: '50%',
          background: '#FDE047',
          display: 'inline-block',
        }}
      />
      <span
        style={{
          fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
          fontSize: 13.5,
          fontWeight: 800,
          color: '#FEF08A',
          letterSpacing: 0.6,
        }}
      >
        SHOTCRAFT · {recipeName}
      </span>
      <span
        style={{
          fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
          fontSize: 12.5,
          fontWeight: 700,
          color: '#6EE7B7',
          background: 'rgba(16, 185, 129, 0.18)',
          padding: '2px 8px',
          borderRadius: 999,
        }}
      >
        {bpm} BPM · BEAT #{beatNumber}
      </span>
    </div>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// 10. VWING OFFICIAL BRAND LOGO (Exact Vector Match of Uploaded App Icon)
// ─────────────────────────────────────────────────────────────────────────────
export const VWingBrandLogo: React.FC<{
  size?: number;
  showLabelBelow?: boolean;
  showLabelRight?: boolean;
  drawProgress?: number;
}> = ({size = 64, showLabelBelow = false, showLabelRight = false, drawProgress = 1}) => {
  const radius = Math.round(size * 0.24);
  const dashLen = 220;
  const dashOffset = (1 - Math.min(1, Math.max(0, drawProgress))) * dashLen;

  return (
    <div
      style={{
        display: 'inline-flex',
        flexDirection: showLabelBelow ? 'column' : 'row',
        alignItems: 'center',
        gap: showLabelBelow ? Math.max(6, Math.round(size * 0.1)) : 12,
      }}
    >
      <div
        style={{
          width: size,
          height: size,
          borderRadius: radius,
          background: 'linear-gradient(145deg, #11422E 0%, #0B2D1F 100%)',
          border: `${Math.max(1.5, size * 0.028)}px solid rgba(168, 223, 101, 0.55)`,
          boxShadow: '0 10px 28px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255, 255, 255, 0.12)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          position: 'relative',
          overflow: 'hidden',
          flexShrink: 0,
        }}
      >
        <svg width={size} height={size} viewBox="0 0 108 108">
          <path
            d="M72,36C72,36 70.5,58.5 55,68.4C43.6,75.6 32.4,74.1 32.4,74.1C32.4,74.1 31,61.3 39.5,51.5C49.4,40.2 72,36 72,36Z"
            fill="rgba(168, 223, 101, 0.08)"
            stroke="#A8DF65"
            strokeWidth="5.2"
            strokeLinejoin="round"
            strokeLinecap="round"
            strokeDasharray={dashLen}
            strokeDashoffset={dashOffset}
          />
          <path
            d="M30,78L56.5,51.5"
            fill="none"
            stroke="#A8DF65"
            strokeWidth="5.2"
            strokeLinecap="round"
            strokeDasharray={dashLen}
            strokeDashoffset={dashOffset}
          />
        </svg>
      </div>
      {showLabelBelow && (
        <span
          style={{
            fontSize: Math.max(16, Math.round(size * 0.25)),
            fontWeight: 800,
            color: '#FFFFFF',
            letterSpacing: 0.4,
            textShadow: '0 2px 10px rgba(0,0,0,0.75)',
          }}
        >
          VWing
        </span>
      )}
      {showLabelRight && (
        <div style={{display: 'flex', flexDirection: 'column'}}>
          <span
            style={{
              fontSize: Math.max(18, Math.round(size * 0.42)),
              fontWeight: 900,
              color: '#FFFFFF',
              letterSpacing: 0.3,
              lineHeight: 1.05,
            }}
          >
            VWing
          </span>
          <span
            style={{
              fontSize: Math.max(11, Math.round(size * 0.24)),
              fontWeight: 800,
              color: '#A8DF65',
              letterSpacing: 0.8,
              marginTop: 2,
            }}
          >
            SMARTFARM
          </span>
        </div>
      )}
    </div>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// 11. CINEMATIC LOGO INTRO & OUTER-SPACE SATELLITE ZOOM TO DEMARCATED FARM
//     (4-Stage Orbital Dive: Z5 Vietnam -> Z10 Lam Dong -> Z15 Di Linh -> Z18 Demarcated Orchard)
// ─────────────────────────────────────────────────────────────────────────────
export const INTRO_DURATION_FRAMES = 144; // 6.0 seconds @ 24fps

const FARM_POLYGON_POINTS: [number, number][] = [
  [845, 315],
  [1260, 165],
  [1500, 365],
  [1245, 795],
  [880, 760],
  [740, 535],
];

const ORCHARD_TREES: {id: string; x: number; y: number; alert?: boolean}[] = [
  {id: 'A-01', x: 925, y: 390},
  {id: 'A-02', x: 1030, y: 325},
  {id: 'A-03', x: 1140, y: 265, alert: true},
  {id: 'A-04', x: 1250, y: 225},
  {id: 'B-01', x: 895, y: 505},
  {id: 'B-02', x: 1010, y: 440, alert: true},
  {id: 'B-03', x: 1125, y: 375},
  {id: 'B-04', x: 1240, y: 315},
  {id: 'B-05', x: 1350, y: 380},
  {id: 'C-01', x: 920, y: 635},
  {id: 'C-02', x: 1040, y: 565},
  {id: 'C-03', x: 1160, y: 495, alert: true},
  {id: 'C-04', x: 1275, y: 445},
  {id: 'D-01', x: 1005, y: 695},
  {id: 'D-02', x: 1145, y: 625},
];

export const VWingSatelliteLogoIntro: React.FC<{hideCaption?: boolean}> = ({hideCaption = false}) => {
  const frame = useCurrentFrame();

  // Stage opacities & zooms across 144 frames (0.0s -> 6.0s)
  // Layer 1: Z5 Vietnam & Southeast Asia (f0 -> f42)
  const z5Scale = interpolate(frame, [0, 42], [1.0, 2.25], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: Easing.inOut(Easing.cubic),
  });
  const z5Opacity = interpolate(frame, [0, 30, 42], [1, 1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  // Layer 2: Z10 Lam Dong Highlands (f28 -> f68)
  const z10Scale = interpolate(frame, [28, 68], [1.0, 2.2], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: Easing.inOut(Easing.cubic),
  });
  const z10Opacity = interpolate(frame, [28, 38, 56, 68], [0, 1, 1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  // Layer 3: Z15 Di Linh Agricultural Valley (f54 -> f92)
  const z15Scale = interpolate(frame, [54, 92], [1.0, 2.1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: Easing.inOut(Easing.cubic),
  });
  const z15Opacity = interpolate(frame, [54, 65, 80, 92], [0, 1, 1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  // Layer 4: Z18 Demarcated Durian Orchard in Hoa Ninh, Di Linh (f78 -> f144)
  const z18Scale = interpolate(frame, [78, 112, 144], [1.0, 1.12, 1.16], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: Easing.out(Easing.cubic),
  });
  const z18Opacity = interpolate(frame, [78, 90, 144], [0, 1, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  // Altitude counter (850 km -> 0.16 km)
  const altitudeKm = interpolate(
    frame,
    [0, 35, 62, 90, 115, 144],
    [850, 48, 2.4, 0.22, 0.16, 0.16],
    {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'},
  );
  const altitudeLabel =
    altitudeKm >= 1.0 ? `${altitudeKm.toFixed(1)} km` : `${Math.round(altitudeKm * 1000)} m`;

  const stageTitle =
    frame < 34
      ? 'QUỸ ĐẠO VỆ TINH ĐÔNG NAM Á · VIỆT NAM'
      : frame < 62
        ? 'CAO NGUYÊN LÂM ĐỒNG · TÂY NGUYÊN'
        : frame < 86
          ? 'XÃ HÒA NINH · HUYỆN DI LINH'
          : 'ĐÃ KHOANH VÙNG: VƯỜN SẦU RIÊNG DI LINH (1,54 HA)';

  // Logo leaf draw & card transitions
  const logoDraw = interpolate(frame, [2, 26], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: Easing.out(Easing.cubic),
  });

  // Polygon perimeter draw (starts at frame 86, finishes at frame 114)
  const polyProgress = interpolate(frame, [86, 114], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: Easing.out(Easing.cubic),
  });
  const polyFillOpacity = interpolate(frame, [96, 118], [0, 0.28], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  const polyPointsAttr = FARM_POLYGON_POINTS.map(([x, y]) => `${x},${y}`).join(' ');
  const perimLen = 2400;

  return (
    <AbsoluteFill
      style={{
        backgroundColor: '#030E0A',
        fontFamily: '"Segoe UI", Inter, Roboto, sans-serif',
        color: '#FFFFFF',
        overflow: 'hidden',
      }}
    >
      {/* 1. 4-STAGE SATELLITE IMAGERY STACK */}
      {z5Opacity > 0.01 && (
        <AbsoluteFill
          style={{
            opacity: z5Opacity,
            transform: `scale(${z5Scale.toFixed(4)})`,
            transformOrigin: '50% 50%',
          }}
        >
          <Img
            src={staticFile('satellite/sat_z5_vietnam.jpg')}
            style={{width: 1920, height: 1080, objectFit: 'cover'}}
          />
        </AbsoluteFill>
      )}

      {z10Opacity > 0.01 && (
        <AbsoluteFill
          style={{
            opacity: z10Opacity,
            transform: `scale(${z10Scale.toFixed(4)})`,
            transformOrigin: '50% 50%',
          }}
        >
          <Img
            src={staticFile('satellite/sat_z10_lamdong.jpg')}
            style={{width: 1920, height: 1080, objectFit: 'cover'}}
          />
        </AbsoluteFill>
      )}

      {z15Opacity > 0.01 && (
        <AbsoluteFill
          style={{
            opacity: z15Opacity,
            transform: `scale(${z15Scale.toFixed(4)})`,
            transformOrigin: '50% 50%',
          }}
        >
          <Img
            src={staticFile('satellite/sat_z15_dilinh.jpg')}
            style={{width: 1920, height: 1080, objectFit: 'cover'}}
          />
        </AbsoluteFill>
      )}

      {z18Opacity > 0.01 && (
        <AbsoluteFill
          style={{
            opacity: z18Opacity,
            transform: `scale(${z18Scale.toFixed(4)})`,
            transformOrigin: '54% 48%',
          }}
        >
          <Img
            src={staticFile('satellite/sat_z18_orchard.jpg')}
            style={{width: 1920, height: 1080, objectFit: 'cover'}}
          />

          {/* SVG DEMARCATED FARM POLYGON & GPS TREE PINS OVERLAY */}
          <svg
            width={1920}
            height={1080}
            style={{position: 'absolute', inset: 0, pointerEvents: 'none'}}
          >
            {/* Glowing Demarcated Farm Polygon Fill & Animated Laser Boundary */}
            <polygon
              points={polyPointsAttr}
              fill={`rgba(52, 211, 153, ${polyFillOpacity.toFixed(3)})`}
              stroke="#A8DF65"
              strokeWidth={4.5}
              strokeLinejoin="round"
              strokeDasharray={perimLen}
              strokeDashoffset={(1 - polyProgress) * perimLen}
            />
            <polygon
              points={polyPointsAttr}
              fill="none"
              stroke="#FEF08A"
              strokeWidth={2}
              strokeDasharray="12 8"
              opacity={polyProgress}
            />

            {/* Internal 8m x 8m Grid Lines inside Demarcated Plot */}
            {polyProgress > 0.5 && (
              <g opacity={(polyProgress - 0.5) * 1.1}>
                <line x1={875} y1={430} x2={1305} y2={250} stroke="rgba(168,223,101,0.45)" strokeWidth={1.5} strokeDasharray="6 6" />
                <line x1={825} y1={540} x2={1405} y2={340} stroke="rgba(168,223,101,0.45)" strokeWidth={1.5} strokeDasharray="6 6" />
                <line x1={865} y1={660} x2={1345} y2={460} stroke="rgba(168,223,101,0.45)" strokeWidth={1.5} strokeDasharray="6 6" />
              </g>
            )}

            {/* 6 Polygon Boundary Vertex Beacons */}
            {FARM_POLYGON_POINTS.map(([vx, vy], idx) => {
              const vShow = interpolate(frame, [88 + idx * 3, 96 + idx * 3], [0, 1], {
                extrapolateLeft: 'clamp',
                extrapolateRight: 'clamp',
              });
              if (vShow <= 0.01) return null;
              return (
                <g key={`v-${idx}`} opacity={vShow}>
                  <circle cx={vx} cy={vy} r={11} fill="rgba(15, 59, 41, 0.9)" stroke="#FEF08A" strokeWidth={2.5} />
                  <circle cx={vx} cy={vy} r={4.5} fill="#A8DF65" />
                </g>
              );
            })}

            {/* Individual Durian Tree GPS Pins inside the Demarcated Orchard */}
            {ORCHARD_TREES.map((tree, idx) => {
              const tShow = interpolate(frame, [98 + idx * 1.5, 108 + idx * 1.5], [0, 1], {
                extrapolateLeft: 'clamp',
                extrapolateRight: 'clamp',
              });
              if (tShow <= 0.01) return null;
              const pinColor = tree.alert ? '#FBBF24' : '#4ADE80';
              return (
                <g key={tree.id} opacity={tShow} transform={`translate(${tree.x}, ${tree.y})`}>
                  <circle cx={0} cy={0} r={14} fill="rgba(6, 24, 18, 0.88)" stroke={pinColor} strokeWidth={2.5} />
                  <circle cx={0} cy={0} r={5} fill={pinColor} />
                  <rect
                    x={-26}
                    y={-34}
                    width={52}
                    height={18}
                    rx={6}
                    fill="rgba(6, 24, 18, 0.92)"
                    stroke={pinColor}
                    strokeWidth={1.2}
                  />
                  <text
                    x={0}
                    y={-21}
                    textAnchor="middle"
                    fill={tree.alert ? '#FEF08A' : '#D1FAE5'}
                    fontSize={11}
                    fontWeight={800}
                  >
                    {tree.id}
                  </text>
                </g>
              );
            })}

            {/* Floating Plot Callout Banner directly on the Demarcated Orchard */}
            {polyProgress > 0.7 && (
              <g opacity={Math.min(1, (polyProgress - 0.7) * 3.3)} transform="translate(895, 92)">
                <rect
                  x={0}
                  y={0}
                  width={490}
                  height={58}
                  rx={14}
                  fill="rgba(9, 34, 25, 0.95)"
                  stroke="#A8DF65"
                  strokeWidth={2.5}
                />
                <text x={245} y={25} textAnchor="middle" fill="#A8DF65" fontSize={14.5} fontWeight={900}>
                  ✓ KHU VƯỜN ĐÃ KHOANH VÙNG · 1,54 HA (24 GỐC SẦU RIÊNG)
                </text>
                <text x={245} y={45} textAnchor="middle" fill="#FEF08A" fontSize={13} fontWeight={700}>
                  Xã Hòa Ninh, Huyện Di Linh, Lâm Đồng · GPS 11.5834°N, 108.0628°E
                </text>
              </g>
            )}
          </svg>
        </AbsoluteFill>
      )}

      {/* 2. CINEMATIC VIGNETTE & ORBITAL TELEMETRY RETICLE OVERLAY */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          background:
            'radial-gradient(circle at 58% 48%, rgba(4, 18, 12, 0.08) 0%, rgba(4, 18, 12, 0.52) 68%, rgba(3, 12, 9, 0.86) 100%)',
          pointerEvents: 'none',
        }}
      />

      {/* Orbital Target Reticle during Zoom (f0 -> f94) */}
      {frame < 96 && (
        <svg
          width={1920}
          height={1080}
          style={{
            position: 'absolute',
            inset: 0,
            pointerEvents: 'none',
            opacity: interpolate(frame, [0, 80, 96], [0.85, 0.85, 0], {
              extrapolateLeft: 'clamp',
              extrapolateRight: 'clamp',
            }),
          }}
        >
          <circle
            cx={1040}
            cy={500}
            r={170}
            fill="none"
            stroke="rgba(168, 223, 101, 0.45)"
            strokeWidth={2}
            strokeDasharray="10 8"
          />
          <circle
            cx={1040}
            cy={500}
            r={78}
            fill="rgba(74, 222, 128, 0.08)"
            stroke="#FEF08A"
            strokeWidth={2.2}
          />
          <line x1={820} y1={500} x2={945} y2={500} stroke="#A8DF65" strokeWidth={2} />
          <line x1={1135} y1={500} x2={1260} y2={500} stroke="#A8DF65" strokeWidth={2} />
          <line x1={1040} y1={280} x2={1040} y2={405} stroke="#A8DF65" strokeWidth={2} />
          <line x1={1040} y1={595} x2={1040} y2={720} stroke="#A8DF65" strokeWidth={2} />
          <circle cx={1040} cy={500} r={6} fill="#FEF08A" />
        </svg>
      )}

      {/* 3. LEFT BRAND INTRO CARD: VWING OFFICIAL LOGO + SATELLITE TELEMETRY */}
      <div
        style={{
          position: 'absolute',
          left: 60,
          top: 54,
          width: 560,
          height: 468,
          boxSizing: 'border-box',
          background: 'rgba(8, 28, 20, 0.94)',
          border: '2.5px solid #A8DF65',
          borderRadius: 28,
          padding: '22px 26px',
          boxShadow: '0 24px 60px rgba(0,0,0,0.72), 0 0 40px rgba(168, 223, 101, 0.22)',
          overflow: 'hidden',
        }}
      >
        {/* Top Tag Pill */}
        <div style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 18}}>
          <span
            style={{
              background: 'rgba(22, 78, 54, 0.95)',
              border: '1.5px solid #A8DF65',
              borderRadius: 999,
              padding: '5px 14px',
              fontSize: 13,
              fontWeight: 800,
              color: '#D9F99D',
              letterSpacing: 0.4,
              whiteSpace: 'nowrap',
            }}
          >
            🛰️ INTRO · VỆ TINH GEE THU PHÓNG KHU VƯỜN
          </span>
          <span
            style={{
              fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
              fontSize: 14,
              fontWeight: 800,
              color: '#FEF08A',
              whiteSpace: 'nowrap',
            }}
          >
            ALT: {altitudeLabel}
          </span>
        </div>

        {/* VWing Official App Icon + Brand Title */}
        <div style={{display: 'flex', alignItems: 'center', gap: 22}}>
          <VWingBrandLogo size={116} showLabelBelow={true} drawProgress={logoDraw} />
          <div style={{flex: 1}}>
            <div
              style={{
                fontSize: 34,
                fontWeight: 900,
                color: '#FFFFFF',
                letterSpacing: -0.5,
                lineHeight: 1.12,
              }}
            >
              VWing SmartFarm
            </div>
            <div
              style={{
                fontSize: 18,
                fontWeight: 700,
                color: '#A8DF65',
                marginTop: 6,
              }}
            >
              Quản Trị Nông Trại Vệ Tinh & AI 24/7
            </div>
            <div
              style={{
                fontSize: 15,
                color: '#D1FAE5',
                marginTop: 8,
                lineHeight: 1.4,
              }}
            >
              Định vị vệ tinh đa tầng từ quỹ đạo xuống từng lô vườn và từng gốc cây đã khoanh vùng.
            </div>
          </div>
        </div>

        {/* 4-Step Satellite Zoom Progress Tracker */}
        <div
          style={{
            marginTop: 20,
            background: 'rgba(5, 18, 13, 0.85)',
            border: '1.5px solid rgba(168, 223, 101, 0.4)',
            borderRadius: 16,
            padding: '14px 16px',
          }}
        >
          <div style={{fontSize: 14, fontWeight: 800, color: '#FEF08A', marginBottom: 10}}>
            🎯 {stageTitle}
          </div>
          <div style={{display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8}}>
            {[
              {lbl: '1. Vệ tinh VN', active: frame >= 0},
              {lbl: '2. Lâm Đồng', active: frame >= 30},
              {lbl: '3. Di Linh', active: frame >= 58},
              {lbl: '4. Khoanh vùng', active: frame >= 84},
            ].map((st, i) => (
              <div
                key={i}
                style={{
                  background: st.active ? 'rgba(34, 110, 74, 0.95)' : 'rgba(18, 40, 30, 0.7)',
                  border: st.active ? '1.5px solid #A8DF65' : '1px solid rgba(255,255,255,0.15)',
                  borderRadius: 10,
                  padding: '7px 6px',
                  textAlign: 'center',
                  fontSize: 12.5,
                  fontWeight: 800,
                  color: st.active ? '#FFFFFF' : '#94A3B8',
                }}
              >
                {st.lbl}
              </div>
            ))}
          </div>
        </div>

        {/* Live Telemetry Metrics of the Demarcated Plot */}
        <div
          style={{
            marginTop: 14,
            display: 'grid',
            gridTemplateColumns: 'repeat(3, 1fr)',
            gap: 10,
          }}
        >
          <div
            style={{
              background: 'rgba(16, 48, 36, 0.9)',
              border: '1px solid rgba(110, 231, 183, 0.45)',
              borderRadius: 12,
              padding: '10px 12px',
              textAlign: 'center',
            }}
          >
            <div style={{fontSize: 12, color: '#A7F3D0', fontWeight: 700}}>DIỆN TÍCH LÔ</div>
            <div style={{fontSize: 20, color: '#FEF08A', fontWeight: 900, marginTop: 2}}>1,54 ha</div>
          </div>
          <div
            style={{
              background: 'rgba(16, 48, 36, 0.9)',
              border: '1px solid rgba(110, 231, 183, 0.45)',
              borderRadius: 12,
              padding: '10px 12px',
              textAlign: 'center',
            }}
          >
            <div style={{fontSize: 12, color: '#A7F3D0', fontWeight: 700}}>CÂY ĐỊNH VỊ GPS</div>
            <div style={{fontSize: 20, color: '#4ADE80', fontWeight: 900, marginTop: 2}}>24 / 24</div>
          </div>
          <div
            style={{
              background: 'rgba(16, 48, 36, 0.9)',
              border: '1px solid rgba(110, 231, 183, 0.45)',
              borderRadius: 12,
              padding: '10px 12px',
              textAlign: 'center',
            }}
          >
            <div style={{fontSize: 12, color: '#A7F3D0', fontWeight: 700}}>CHỈ SỐ GEE NDVI</div>
            <div style={{fontSize: 20, color: '#A8DF65', fontWeight: 900, marginTop: 2}}>0.78 Tốt</div>
          </div>
        </div>

        <ShotcraftPerimeterBeam width={560} height={468} radius={28} frame={frame} />
      </div>

      {/* Bottom Subtitle for Intro */}
      {!hideCaption && (
        <div
          style={{
            position: 'absolute',
            left: 60,
            bottom: 28,
            width: 1200,
            background: 'rgba(6, 18, 14, 0.94)',
            border: '1.5px solid #A8DF65',
            borderRadius: 16,
            padding: '14px 24px',
            fontSize: 22,
            fontWeight: 700,
            lineHeight: 1.4,
            color: '#FFFFFF',
            boxShadow: '0 12px 32px rgba(0,0,0,0.55)',
          }}
        >
          VWing SmartFarm — Định vị vệ tinh từ ngoài không gian thu phóng trực tiếp đến khu vườn sầu riêng đã khoanh vùng tại Di Linh, Lâm Đồng.
        </div>
      )}

      <ShotcraftRecipeHud
        recipeName="intro/vwing-logo + satellite-zoom-demarcated-plot"
        beatNumber={Math.floor((frame / 24) * (124 / 60)) + 1}
        bpm={124}
      />
    </AbsoluteFill>
  );
};
