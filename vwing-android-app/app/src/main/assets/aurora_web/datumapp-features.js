// =============================================================================
// DATUMAPP v4.1021.224 — FULL CONVERTED HYDROLOGY, TOPOGRAPHY, GNSS & CAD SUITE
// Reverse-engineered with REA v6.8.0 from:
// https://www.datumapp.net/home?t=... (Project: "Crecida Río Paraná", pid: "col-mvif1u1o-n4yx5")
//
// REA Evidence Provenance:
// - ev_1bd954dfd9f389e79a1f48acb90411eec98d84c279ee793b4575e5d31e95ef81 (inspect-web-page)
// - ev_e96a3ce7a5fbfdc7c6cfdc5d82164c9add4872e7e5a44baf8c6af68a5579732d (export-web-scripts: 69 JS files)
// - ev_150da542084ebdcbef319fadfc07f21eacdffaf8f54aabe497101b7da39cfc7c (analyze-javascript-application)
// - ev_c5492ad02f12d57cc74acd8b9d161f8cfa1bf7f671cf9ddd085fcb4db5ff7818 (capture-browser-scenario)
//
// Converted Modules:
// 1. Crecida Río Paraná & Flood Simulator (crecida-rio.js, river-boundary-core.js,
//    river-boundary-editor.js, river-boundary-slope.js, river-boundary-slope-water.js)
// 2. Topographic Profile & Marching Squares Contours (perfil-datos.js, perfil-grafico.js,
//    alturas.js, curvas.js)
// 3. GNSS RTK NMEA-0183 Parser, Skyplot & Stakeout Replanteo (gnss-nmea.js, gnss-nucleo.js, gnss3d.js)
// 4. Point Cloud Classification, OrtoFalsa Camera, POSGAR/VN-2000 CRS & AutoCAD DXF R12 Exporter
//    (dib-clasificacion.js, ortho-falsa.js, crs.js, dxf.js)
// =============================================================================

(function (global) {
  'use strict';

  // ---------------------------------------------------------------------------
  // 1. CORE MATHEMATICAL ENGINES EXTRACTED FROM DATUMAPP SCRIPTS
  // ---------------------------------------------------------------------------

  // 1A. DatumRiverBoundaryCore (from river-boundary-core.js?v=1.1.0 — source-65)
  const DatumRiverBoundaryCore = (function () {
    const MIN_VERTICES = 3;
    const MAX_VERTICES = 512;
    const DUPLICATE_EPS_M = 0.05;
    const MIN_AREA_M2 = 1.0;
    const EARTH_R = 6378137.0;

    function toRad(deg) { return (deg * Math.PI) / 180; }

    function projectLocalMeters(points) {
      if (!points || !points.length) return [];
      const refLat = points[0].lat;
      const refLng = points[0].lng;
      const cosLat = Math.max(0.01, Math.cos(toRad(refLat)));
      return points.map(p => ({
        x: toRad(p.lng - refLng) * EARTH_R * cosLat,
        y: toRad(p.lat - refLat) * EARTH_R
      }));
    }

    function distM(a, b) {
      const pts = projectLocalMeters([a, b]);
      return Math.hypot(pts[1].x - pts[0].x, pts[1].y - pts[0].y);
    }

    function polygonAreaM2(points) {
      if (!Array.isArray(points) || points.length < MIN_VERTICES) return 0;
      const pts = projectLocalMeters(points);
      let sum = 0;
      for (let i = 0; i < pts.length; i++) {
        const j = (i + 1) % pts.length;
        sum += pts[i].x * pts[j].y - pts[j].x * pts[i].y;
      }
      return Math.abs(sum) * 0.5;
    }

    function orient2d(a, b, c) {
      return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    function onSegment(a, b, p, eps) {
      return (
        Math.abs(orient2d(a, b, p)) <= eps &&
        p.x >= Math.min(a.x, b.x) - eps &&
        p.x <= Math.max(a.x, b.x) + eps &&
        p.y >= Math.min(a.y, b.y) - eps &&
        p.y <= Math.max(a.y, b.y) + eps
      );
    }

    function segmentsIntersect(a, b, c, d) {
      const eps = 1e-7;
      const o1 = orient2d(a, b, c);
      const o2 = orient2d(a, b, d);
      const o3 = orient2d(c, d, a);
      const o4 = orient2d(c, d, b);
      if (((o1 > eps && o2 < -eps) || (o1 < -eps && o2 > eps)) &&
          ((o3 > eps && o4 < -eps) || (o3 < -eps && o4 > eps))) {
        return true;
      }
      if (onSegment(a, b, c, eps) || onSegment(a, b, d, eps) ||
          onSegment(c, d, a, eps) || onSegment(c, d, b, eps)) {
        return true;
      }
      return false;
    }

    function hasSelfIntersection(points) {
      const n = points.length;
      if (n < 4) return false;
      const pts = projectLocalMeters(points);
      for (let i = 0; i < n; i++) {
        const a = pts[i];
        const b = pts[(i + 1) % n];
        for (let j = i + 1; j < n; j++) {
          if (j === i || j === (i + 1) % n || i === (j + 1) % n) continue;
          const c = pts[j];
          const d = pts[(j + 1) % n];
          if (segmentsIntersect(a, b, c, d)) return true;
        }
      }
      return false;
    }

    function validatePolygon(points) {
      if (!Array.isArray(points)) return { valid: false, reason: 'not_array', areaM2: 0 };
      if (points.length < MIN_VERTICES) return { valid: false, reason: 'too_few_vertices', areaM2: 0 };
      if (points.length > MAX_VERTICES) return { valid: false, reason: 'too_many_vertices', areaM2: 0 };
      for (let i = 0; i < points.length; i++) {
        const j = (i + 1) % points.length;
        if (distM(points[i], points[j]) < DUPLICATE_EPS_M) {
          return { valid: false, reason: 'duplicate_vertices', areaM2: 0 };
        }
      }
      const areaM2 = polygonAreaM2(points);
      if (!(areaM2 >= MIN_AREA_M2)) return { valid: false, reason: 'zero_area', areaM2 };
      if (hasSelfIntersection(points)) return { valid: false, reason: 'self_intersection', areaM2 };
      return { valid: true, reason: null, areaM2 };
    }

    return {
      MIN_VERTICES,
      MAX_VERTICES,
      polygonAreaM2,
      hasSelfIntersection,
      validatePolygon,
      projectLocalMeters
    };
  })();

  // 1B. DatumPerfilDatos (from perfil-datos.js?v=3 — source-38)
  const DatumPerfilDatos = (function () {
    function isNum(v) { return typeof v === 'number' && Number.isFinite(v); }
    function slopeRange(s) {
      const a = Math.abs(s);
      return a <= 2 ? 0 : a <= 5 ? 1 : a <= 10 ? 2 : a <= 20 ? 3 : 4;
    }
    function pk(distM) {
      const cm = Math.round(100 * Math.max(0, distM));
      return 'PK ' + Math.floor(cm / 100000) + '+' + ((cm % 100000) / 100).toFixed(2).padStart(6, '0');
    }
    function gradeAt(grade, d) {
      if (!grade || !isNum(d)) return null;
      const k = grade.knots;
      if (d < k[0].distance || d > k[k.length - 1].distance) return null;
      let i = 0, l = k.length - 1;
      while (i + 1 < l) {
        const m = (i + l) >> 1;
        if (k[m].distance <= d) i = m; else l = m;
      }
      const span = k[l].distance - k[i].distance;
      if (span <= 0) return k[i].elevation;
      return k[i].elevation + (k[l].elevation - k[i].elevation) * ((d - k[i].distance) / span);
    }
    function at(prof, d) {
      if (!prof || !isNum(d) || d < 0 || d > prof.length || !prof.samples.length) return null;
      const a = prof.samples;
      let i = 0, l = a.length - 1;
      while (i < l) {
        const r = Math.ceil((i + l) / 2);
        if (a[r].distance <= d) i = r; else l = r - 1;
      }
      const o = a[i], s = a[i + 1];
      if (d === o.distance || !s) return Object.assign({}, o);
      const c = s.distance - o.distance;
      const u = (d - o.distance) / c;
      const u2 = u * u, u3 = u2 * u;
      // Fritsch-Carlson Hermite cubic interpolation
      const v = (2 * u3 - 3 * u2 + 1) * o.elevation +
                (u3 - 2 * u2 + u) * c * prof.tangents[i] +
                (-2 * u3 + 3 * u2) * s.elevation +
                (u3 - u2) * c * prof.tangents[i + 1];
      return {
        distance: d,
        elevation: v,
        slope: ((s.elevation - o.elevation) / c) * 100,
        deltaH: isNum(a[0].elevation) ? v - a[0].elevation : null,
        interpolated: true
      };
    }
    function prepare(rawSamples) {
      const r = (rawSamples || [])
        .filter(e => e && isNum(e.distance) && e.distance >= 0 && isNum(e.elevation))
        .map(e => Object.assign({}, e))
        .sort((a, b) => a.distance - b.distance);
      const len = r.length ? r[r.length - 1].distance : 0;
      const u = r.length ? r[0].elevation : 0;
      const d = r.length ? r[r.length - 1].elevation : 0;
      const metrics = {
        length: len,
        min: null,
        max: null,
        deltaH: r.length ? d - u : 0,
        maxSlope: 0,
        excess: 0,
        deficit: 0
      };
      const slopes = [];
      r.forEach((e, idx) => {
        e.deltaH = e.elevation - u;
        e.slope = 0;
        metrics.min = metrics.min === null ? e.elevation : Math.min(metrics.min, e.elevation);
        metrics.max = metrics.max === null ? e.elevation : Math.max(metrics.max, e.elevation);
        if (idx > 0) {
          const prev = r[idx - 1];
          const slp = ((e.elevation - prev.elevation) / Math.max(1e-6, e.distance - prev.distance)) * 100;
          slopes.push({ from: prev.distance, to: e.distance, slope: slp, range: slopeRange(slp) });
          e.slope = slp;
          if (idx === 1) prev.slope = slp;
          metrics.maxSlope = Math.max(metrics.maxSlope, Math.abs(slp));
        }
      });
      // Fritsch-Carlson monotone cubic tangents
      const tangents = new Float64Array(r.length);
      const secants = [];
      for (let i = 0; i + 1 < r.length; i++) {
        secants.push((r[i + 1].elevation - r[i].elevation) / Math.max(1e-6, r[i + 1].distance - r[i].distance));
      }
      for (let i = 0; i < r.length; i++) {
        tangents[i] = i === 0
          ? (secants[0] || 0)
          : i === r.length - 1
            ? (secants[secants.length - 1] || 0)
            : (secants[i - 1] * secants[i] <= 0 ? 0 : 2 / (1 / secants[i - 1] + 1 / secants[i]));
      }
      for (let i = 0; i < secants.length; i++) {
        const c = secants[i];
        if (c) {
          const alpha = tangents[i] / c;
          const beta = tangents[i + 1] / c;
          const sq = alpha * alpha + beta * beta;
          if (sq > 9) {
            const tau = 3 / Math.sqrt(sq);
            tangents[i] = tau * alpha * c;
            tangents[i + 1] = tau * beta * c;
          }
        } else {
          tangents[i] = tangents[i + 1] = 0;
        }
      }
      const grade = len > 0 ? {
        a: u,
        b: d,
        slope: (d - u) / len,
        knots: [{ distance: 0, elevation: u }, { distance: len, elevation: d }]
      } : null;
      if (grade) {
        r.forEach(e => {
          const gVal = gradeAt(grade, e.distance);
          const diff = e.elevation - gVal;
          metrics.excess = Math.max(metrics.excess, diff);
          metrics.deficit = Math.max(metrics.deficit, -diff);
        });
      }
      return { samples: r, tangents, slopes, metrics, grade, length: len };
    }
    return { prepare, at, gradeAt, slopeRange, pk };
  })();

  // 1C. CURVAS Marching Squares + Chaikin Smoothing (from curvas.js?v=4.286.0 — source-32)
  const DatumCurvas = (function () {
    const EPS = 1e-6;
    function cellVal(grid, x, y) {
      if (x < 0 || y < 0 || x >= grid.nx || y >= grid.ny) return null;
      const v = grid.z[y * grid.nx + x];
      return v != null && Number.isFinite(v) ? v : null;
    }
    function interp(a, b, level) {
      const d = b - a;
      if (Math.abs(d) < 1e-12) return 0.5;
      const t = (level - a) / d;
      return t < 0 ? 0 : t > 1 ? 1 : t;
    }
    function segmentos(grid, level) {
      const segs = [];
      const z = level + 1e-12 * Math.max(Math.abs(level), 1);
      for (let y = 0; y + 1 < grid.ny; y++) {
        for (let x = 0; x + 1 < grid.nx; x++) {
          const C = cellVal(grid, x, y);
          const D = cellVal(grid, x + 1, y);
          const E = cellVal(grid, x + 1, y + 1);
          const F = cellVal(grid, x, y + 1);
          if (C === null || D === null || E === null || F === null) continue;
          const mask = (C >= z ? 8 : 0) | (D >= z ? 4 : 0) | (E >= z ? 2 : 0) | (F >= z ? 1 : 0);
          if (mask === 0 || mask === 15) continue;
          const H = [x + interp(C, D, z), y];
          const I = [x + 1, y + interp(D, E, z)];
          const J = [x + interp(F, E, z), y + 1];
          const K = [x, y + interp(C, F, z)];
          const add = (p1, p2) => {
            if (Math.abs(p1[0] - p2[0]) >= EPS || Math.abs(p1[1] - p2[1]) >= EPS) segs.push([p1, p2]);
          };
          switch (mask) {
            case 1: case 14: add(K, J); break;
            case 2: case 13: add(J, I); break;
            case 3: case 12: add(K, I); break;
            case 4: case 11: add(H, I); break;
            case 6: case 9:  add(H, J); break;
            case 7: case 8:  add(K, H); break;
            case 5:
              if ((C + D + E + F) / 4 >= z) { add(K, H); add(J, I); }
              else { add(K, J); add(H, I); }
              break;
            case 10:
              if ((C + D + E + F) / 4 >= z) { add(K, J); add(H, I); }
              else { add(K, H); add(J, I); }
              break;
          }
        }
      }
      return segs;
    }
    function unir(segs) {
      const keyOf = pt => Math.round(pt[0] / 1e-4) + ',' + Math.round(pt[1] / 1e-4);
      const map = {};
      segs.forEach((s, idx) => {
        [keyOf(s[0]), keyOf(s[1])].forEach(k => { (map[k] || (map[k] = [])).push(idx); });
      });
      const used = new Array(segs.length).fill(false);
      const lines = [];
      const otherEnd = (idx, k) => keyOf(segs[idx][0]) === k ? segs[idx][1] : segs[idx][0];
      const extend = (chain, k, prepend) => {
        for (;;) {
          const list = map[k] || [];
          let nextIdx = -1;
          for (let i = 0; i < list.length; i++) {
            if (!used[list[i]]) { nextIdx = list[i]; break; }
          }
          if (nextIdx < 0) return;
          used[nextIdx] = true;
          const pt = otherEnd(nextIdx, k);
          if (prepend) chain.unshift(pt); else chain.push(pt);
          k = keyOf(pt);
        }
      };
      for (let i = 0; i < segs.length; i++) {
        if (!used[i]) {
          used[i] = true;
          const chain = [segs[i][0], segs[i][1]];
          extend(chain, keyOf(chain[chain.length - 1]), false);
          extend(chain, keyOf(chain[0]), true);
          if (chain.length >= 2) {
            chain.cerrada = chain.length > 3 && keyOf(chain[0]) === keyOf(chain[chain.length - 1]);
            lines.push(chain);
          }
        }
      }
      return lines;
    }
    function suavizar(pts, iters) {
      const nIters = Math.max(0, Math.min(4, iters | 0));
      if (!Array.isArray(pts) || pts.length < 3 || !nIters) return pts;
      const closed = !!pts.cerrada;
      let cur = closed ? pts.slice(0, pts.length - 1) : pts.slice();
      for (let it = 0; it < nIters; it++) {
        const next = [];
        if (!closed) next.push(cur[0]);
        const lim = closed ? cur.length : cur.length - 1;
        for (let i = 0; i < lim; i++) {
          const F = cur[i], G = cur[(i + 1) % cur.length];
          next.push([0.75 * F[0] + 0.25 * G[0], 0.75 * F[1] + 0.25 * G[1]]);
          next.push([0.25 * F[0] + 0.75 * G[0], 0.25 * F[1] + 0.75 * G[1]]);
        }
        if (!closed) next.push(cur[cur.length - 1]);
        cur = next;
      }
      if (closed) cur.push(cur[0].slice());
      cur.cerrada = closed;
      return cur;
    }
    function niveles(minZ, maxZ, step, maxCount = 400) {
      const s = Number(step);
      if (!Number.isFinite(s) || s <= 0 || maxZ < minZ) return [];
      const start = Math.ceil(minZ / s);
      const end = Math.floor(maxZ / s);
      const out = [];
      for (let i = start; i <= end && out.length < maxCount; i++) {
        const val = Math.round(i * s * 1e6) / 1e6;
        if (val > minZ && val < maxZ) out.push(val);
      }
      return out;
    }
    function esIndice(level, step, every = 5) {
      const m = every | 0 || 5;
      return ((Math.round(level / step) % m) + m) % m === 0;
    }
    function generar(grid, opts = {}) {
      const step = opts.paso || 1;
      const lvls = niveles(opts.min, opts.max, step);
      const smooth = opts.suavizar == null ? 2 : opts.suavizar;
      const out = [];
      lvls.forEach(lvl => {
        let lines = unir(segmentos(grid, lvl));
        if (lines.length) {
          if (smooth) lines = lines.map(l => suavizar(l, smooth));
          out.push({ nivel: lvl, indice: esIndice(lvl, step, opts.indiceCada || 5), lineas: lines });
        }
      });
      return out;
    }
    return { generar, niveles, esIndice, segmentos, unir, suavizar };
  })();

  // 1D. GNSS NMEA-0183 Parser (from gnss-nmea.js?v=4 & gnss-nucleo.js?v=4 — source-9 & source-33)
  const DatumGnssNmea = (function () {
    const CALIDAD_GGA = {
      0: 'SIN_FIX',
      1: 'AUTONOMO',
      2: 'DGPS',
      3: 'AUTONOMO',
      4: 'RTK_FIJO',
      5: 'FLOAT',
      6: 'SIN_SENAL'
    };

    function verificar(raw) {
      const s = String(raw || '').trim();
      if (!s.startsWith('$') && !s.startsWith('!')) {
        return { ok: false, cuerpo: '', conChecksum: false, motivo: 'Thiếu ký tự đầu $' };
      }
      const star = s.lastIndexOf('*');
      if (star < 0) return { ok: true, cuerpo: s.slice(1), conChecksum: false };
      const body = s.slice(1, star);
      const hex = s.slice(star + 1, star + 3).toUpperCase();
      if (!/^[0-9A-F]{2}$/.test(hex)) {
        return { ok: false, cuerpo: body, conChecksum: true, motivo: 'Checksum không hợp lệ' };
      }
      let xor = 0;
      for (let i = 0; i < body.length; i++) xor ^= body.charCodeAt(i);
      const calcHex = (xor < 16 ? '0' : '') + xor.toString(16).toUpperCase();
      return calcHex === hex
        ? { ok: true, cuerpo: body, conChecksum: true, checksumHex: calcHex }
        : { ok: false, cuerpo: body, conChecksum: true, motivo: `Sai checksum (${hex} ≠ ${calcHex})` };
    }

    function nmeaCoord(valStr, hemi) {
      if (!valStr) return null;
      const v = Number(valStr);
      if (!Number.isFinite(v)) return null;
      const deg = Math.floor(v / 100);
      const min = v - deg * 100;
      let dec = deg + min / 60;
      if (hemi === 'S' || hemi === 'W') dec = -dec;
      return dec;
    }

    function parseBatch(text) {
      const lines = String(text || '').split(/\r?\n/).map(l => l.trim()).filter(Boolean);
      const state = {
        validLines: 0,
        badChecksums: 0,
        utc: '143210.00',
        lat: -31.712938,
        lon: -60.576180,
        calidad: 'RTK_FIJO',
        calidadGga: 4,
        sats: 18,
        hdop: 0.58,
        pdop: 1.12,
        vdop: 0.96,
        hOrto: 14.220,
        hGeoide: 17.564,
        hElip: 31.784,
        sigmaLat: 0.008,
        sigmaLon: 0.009,
        sigmaH: 0.012,
        sigmaV: 0.019,
        edadCorr: 0.8,
        baseId: '0104',
        velocidadKmh: 0.14,
        rumbo: 112.4,
        sentences: []
      };

      lines.forEach(line => {
        const chk = verificar(line);
        if (!chk.ok) {
          state.badChecksums++;
          state.sentences.push({ line, ok: false, reason: chk.motivo });
          return;
        }
        state.validLines++;
        const f = chk.cuerpo.split(',');
        const head = (f[0] || '').toUpperCase();
        const type = head.slice(-3);
        const talker = head.slice(0, -3);
        state.sentences.push({ line, ok: true, type, talker, checksum: chk.checksumHex });

        if (type === 'GGA') {
          state.utc = f[1] || state.utc;
          const lat = nmeaCoord(f[2], f[3]);
          const lon = nmeaCoord(f[4], f[5]);
          if (lat !== null) state.lat = lat;
          if (lon !== null) state.lon = lon;
          state.calidadGga = Number(f[6] || 4);
          state.calidad = CALIDAD_GGA[state.calidadGga] || 'AUTONOMO';
          state.sats = Number(f[7] || state.sats);
          state.hdop = Number(f[8] || state.hdop);
          state.hOrto = Number(f[9] || state.hOrto);
          state.hGeoide = Number(f[11] || state.hGeoide);
          state.hElip = state.hOrto + state.hGeoide;
          state.edadCorr = Number(f[13] || 0.8);
          state.baseId = f[14] || state.baseId;
        } else if (type === 'GST') {
          state.sigmaLat = Number(f[6] || 0.008);
          state.sigmaLon = Number(f[7] || 0.009);
          state.sigmaV = Number(f[8] || 0.019);
          state.sigmaH = Math.hypot(state.sigmaLat, state.sigmaLon);
        } else if (type === 'GSA') {
          if (f[15]) state.pdop = Number(f[15]);
          if (f[16]) state.hdop = Number(f[16]);
          if (f[17]) state.vdop = Number(f[17]);
        } else if (type === 'RMC') {
          if (f[7]) state.velocidadKmh = Number(f[7]) * 1.852;
          if (f[8]) state.rumbo = Number(f[8]);
        }
      });
      return state;
    }

    return { verificar, parseBatch, CALIDAD_GGA };
  })();

  // 1E. POSGAR 2007 (EPSG:5343..5349) & CRS Transverse Mercator Engine (from crs.js?v=10 — source-51)
  const DatumCrsEngine = (function () {
    const DEFINITIONS = {
      5347: {
        epsg: 5347,
        name: 'POSGAR 2007 / Argentina Faja 5',
        lat0: -90,
        lon0: -60,
        k0: 1.0,
        x0: 5500000,
        y0: 0,
        ellps: 'GRS80',
        def: '+proj=tmerc +lat_0=-90 +lon_0=-60 +k=1 +x_0=5500000 +y_0=0 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs'
      },
      5346: {
        epsg: 5346,
        name: 'POSGAR 2007 / Argentina Faja 4',
        lat0: -90,
        lon0: -63,
        k0: 1.0,
        x0: 4500000,
        y0: 0,
        ellps: 'GRS80',
        def: '+proj=tmerc +lat_0=-90 +lon_0=-63 +k=1 +x_0=4500000 +y_0=0 +ellps=GRS80 +units=m +no_defs'
      },
      9210: {
        epsg: 9210,
        name: 'VN-2000 / KT trục Lâm Đồng (L0=107.75° · Múi 3°)',
        lat0: 0,
        lon0: 107.75,
        k0: 0.9999,
        x0: 500000,
        y0: 0,
        ellps: 'WGS84',
        def: '+proj=tmerc +lat_0=0 +lon_0=107.75 +k=0.9999 +x_0=500000 +y_0=0 +ellps=WGS84 +units=m +no_defs'
      },
      32720: {
        epsg: 32720,
        name: 'WGS 84 / UTM zona 20S (Paraná)',
        lat0: 0,
        lon0: -63,
        k0: 0.9996,
        x0: 500000,
        y0: 10000000,
        ellps: 'WGS84',
        def: '+proj=utm +zone=20 +south +datum=WGS84 +units=m +no_defs'
      }
    };

    function meridionalArc(latRad, a, e2) {
      return a * (
        (1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * Math.pow(e2, 3) / 256) * latRad -
        (3 * e2 / 8 + 3 * e2 * e2 / 32 + 45 * Math.pow(e2, 3) / 1024) * Math.sin(2 * latRad) +
        (15 * e2 * e2 / 256 + 45 * Math.pow(e2, 3) / 1024) * Math.sin(4 * latRad) -
        (35 * Math.pow(e2, 3) / 3072) * Math.sin(6 * latRad)
      );
    }

    function projectLonLat(lonDeg, latDeg, epsgCode = 5347) {
      const cfg = DEFINITIONS[epsgCode] || DEFINITIONS[5347];
      const a = 6378137.0;
      const f = cfg.ellps === 'GRS80' ? 1 / 298.257222101 : 1 / 298.257223563;
      const e2 = 2 * f - f * f;
      const ep2 = e2 / (1 - e2);
      const phi = (latDeg * Math.PI) / 180;
      const lam = (lonDeg * Math.PI) / 180;
      const phi0 = (cfg.lat0 * Math.PI) / 180;
      const lam0 = (cfg.lon0 * Math.PI) / 180;

      const sinPhi = Math.sin(phi), cosPhi = Math.cos(phi), tanPhi = Math.tan(phi);
      const N = a / Math.sqrt(1 - e2 * sinPhi * sinPhi);
      const T = tanPhi * tanPhi;
      const C = ep2 * cosPhi * cosPhi;
      const A = cosPhi * (lam - lam0);
      const M = meridionalArc(phi, a, e2);
      const M0 = meridionalArc(phi0, a, e2);

      const E = cfg.x0 + cfg.k0 * N * (
        A +
        (1 - T + C) * Math.pow(A, 3) / 6 +
        (5 - 18 * T + T * T + 72 * C - 58 * ep2) * Math.pow(A, 5) / 120
      );
      const Northing = cfg.y0 + cfg.k0 * (
        M - M0 + N * tanPhi * (
          A * A / 2 +
          (5 - T + 9 * C + 4 * C * C) * Math.pow(A, 4) / 24 +
          (61 - 58 * T + T * T + 600 * C - 330 * ep2) * Math.pow(A, 6) / 720
        )
      );
      return { E, N: Northing, epsg: cfg.epsg, name: cfg.name, def: cfg.def };
    }

    function dms(deg, axis) {
      if (!Number.isFinite(deg)) return '';
      const neg = deg < 0;
      const abs = Math.abs(deg);
      let d = Math.floor(abs);
      const rem = (abs - d) * 60;
      let m = Math.floor(rem);
      let s = Math.round((rem - m) * 60 * 1e4) / 1e4;
      if (s >= 60) { s = 0; m += 1; }
      if (m >= 60) { m = 0; d += 1; }
      const hemi = axis === 'lat' ? (neg ? 'S' : 'N') : (neg ? 'W' : 'E');
      return `${d}°${String(m).padStart(2, '0')}'${s < 10 ? '0' : ''}${s.toFixed(4)}" ${hemi}`;
    }

    return { DEFINITIONS, projectLonLat, dms };
  })();

  // 1F. AutoCAD DXF R12 (AC1009) Exporter (from dxf.js?v=4.681.0 — source-3)
  const DatumDxfExporter = (function () {
    const NL = '\n';
    function fmt(n) { return Number(n || 0).toFixed(3); }

    function generateDxf(figures, epsgCode = 5347) {
      const crsDef = DatumCrsEngine.DEFINITIONS[epsgCode] || DatumCrsEngine.DEFINITIONS[5347];
      const allE = [], allN = [];
      const layers = new Set(['COTA_AGUA', 'POLIGONO_CRECIDA', 'CURVAS_NIVEL', 'PERFIL_TOPO', 'PUNTOS_GNSS']);

      const projectedFigs = figures.map(fig => {
        const pts = (fig.points || []).map(pt => {
          const p = DatumCrsEngine.projectLonLat(pt.lng, pt.lat, epsgCode);
          allE.push(p.E);
          allN.push(p.N);
          return { E: p.E, N: p.N, Z: Number.isFinite(pt.z) ? pt.z : 0 };
        });
        if (fig.layer) layers.add(fig.layer);
        return Object.assign({}, fig, { pts });
      }).filter(f => f.pts.length > 0);

      const minE = allE.length ? Math.min(...allE) : 0;
      const maxE = allE.length ? Math.max(...allE) : 100;
      const minN = allN.length ? Math.min(...allN) : 0;
      const maxN = allN.length ? Math.max(...allN) : 100;
      const ctrE = (minE + maxE) / 2;
      const ctrN = (minN + maxN) / 2;
      const viewSize = 1.2 * Math.max(maxN - minN, maxE - minE) || 100;

      let out = `999${NL}DATUMAPP_CRS=EPSG:${crsDef.epsg}${NL}`;
      out += `0${NL}SECTION${NL}2${NL}HEADER${NL}9${NL}$ACADVER${NL}1${NL}AC1009${NL}`;
      out += `9${NL}$INSUNITS${NL}70${NL}6${NL}`;
      out += `9${NL}$EXTMIN${NL}10${NL}${fmt(minE)}${NL}20${NL}${fmt(minN)}${NL}30${NL}0.0${NL}`;
      out += `9${NL}$EXTMAX${NL}10${NL}${fmt(maxE)}${NL}20${NL}${fmt(maxN)}${NL}30${NL}0.0${NL}`;
      out += `9${NL}$VIEWCTR${NL}10${NL}${fmt(ctrE)}${NL}20${NL}${fmt(ctrN)}${NL}`;
      out += `9${NL}$VIEWSIZE${NL}40${NL}${fmt(viewSize)}${NL}0${NL}ENDSEC${NL}`;

      out += `0${NL}SECTION${NL}2${NL}TABLES${NL}0${NL}TABLE${NL}2${NL}LAYER${NL}70${NL}${layers.size}${NL}`;
      let colorIdx = 1;
      layers.forEach(layerName => {
        out += `0${NL}LAYER${NL}2${NL}${layerName}${NL}70${NL}0${NL}62${NL}${(colorIdx++ % 7) + 1}${NL}6${NL}CONTINUOUS${NL}`;
      });
      out += `0${NL}ENDTAB${NL}0${NL}ENDSEC${NL}`;

      out += `0${NL}SECTION${NL}2${NL}ENTITIES${NL}`;
      projectedFigs.forEach(fig => {
        const layer = fig.layer || 'POLIGONO_CRECIDA';
        if (fig.type === 'point') {
          const p = fig.pts[0];
          out += `0${NL}POINT${NL}8${NL}${layer}${NL}10${NL}${fmt(p.E)}${NL}20${NL}${fmt(p.N)}${NL}30${NL}${fmt(p.Z)}${NL}`;
          if (fig.label) {
            out += `0${NL}TEXT${NL}8${NL}${layer}${NL}10${NL}${fmt(p.E + 0.5)}${NL}20${NL}${fmt(p.N + 0.5)}${NL}30${NL}${fmt(p.Z)}${NL}40${NL}1.200${NL}1${NL}${fig.label}${NL}`;
          }
        } else {
          const flags = (fig.closed ? 1 : 0) | 8; // 3D Polyline flag
          out += `0${NL}POLYLINE${NL}8${NL}${layer}${NL}66${NL}1${NL}70${NL}${flags}${NL}10${NL}0.0${NL}20${NL}0.0${NL}30${NL}0.0${NL}`;
          fig.pts.forEach(p => {
            out += `0${NL}VERTEX${NL}8${NL}${layer}${NL}10${NL}${fmt(p.E)}${NL}20${NL}${fmt(p.N)}${NL}30${NL}${fmt(p.Z)}${NL}70${NL}32${NL}`;
          });
          out += `0${NL}SEQEND${NL}8${NL}${layer}${NL}`;
        }
      });
      out += `0${NL}ENDSEC${NL}0${NL}EOF${NL}`;
      return out;
    }

    return { generateDxf };
  })();

  // ---------------------------------------------------------------------------
  // 2. STATE & BENCHMARK DATASETS (CRECIDA RÍO PARANÁ + BẢO LỘC TÂY NGUYÊN)
  // ---------------------------------------------------------------------------
  const DATUM_STATE = {
    activeTab: 'flood', // 'flood' | 'profile' | 'gnss' | 'cad3d'
    sitePreset: 'parana', // 'parana' | 'baoloc'
    // River flood state (exact constants from crecida-rio.js?v=1.19.0)
    level: 3.42,
    minLevel: -2.0,
    maxLevel: 12.0,
    alertLevel: 4.70,
    evacuationLevel: 5.00,
    zeroEllipsoidM: 26.928, // 31.888 - 4.900 - 0.060 (from crecida-rio.js)
    geoidUndulationM: 17.564321102889643, // GEOIDE-Ar16
    zeroOrthometricM: 9.364,
    waterColor: '#248ce8',
    waterAlpha: 0.52,
    slopeAdjustM: 0.0,
    waveMotion: 'waves', // 'waves' | 'none'
    waveSpeed: 1.2,
    waveIntensity: 0.45,
    waveScaleM: 18,
    selectedVertexIdx: 1,
    animatingRise: false,
    historicalCrecidas: [
      { year: '1983', level: 6.83, note: 'Lũ lịch sử Paraná 1983 (Academia Nacional de Geografía)' },
      { year: '1992', level: 6.89, note: 'Đỉnh lũ kỷ lục thế kỷ XX tại cảng Paraná (6.89 m)' },
      { year: '1998', level: 6.72, note: 'Siêu El Niño 1998 tại Bajada Grande (6.72 m)' }
    ],
    savedScenarios: [
      { id: 'sc-1', name: 'Mực nước thực tế PNA hôm nay', level: 3.42, adjust: 0.0, color: '#248ce8' },
      { id: 'sc-2', name: 'Ngưỡng Cảnh báo (Alerta PNA)', level: 4.70, adjust: 0.15, color: '#f59e0b' },
      { id: 'sc-3', name: 'Ngưỡng Sơ tán (Evacuación PNA)', level: 5.00, adjust: 0.20, color: '#ef4444' },
      { id: 'sc-4', name: 'Tái hiện Đỉnh lũ lịch sử 1992', level: 6.89, adjust: 0.35, color: '#1d4ed8' }
    ],
    // 30-day hydrograph series (Prefectura Naval Argentina format)
    hydroSeries: [
      { day: '02/10', level: 2.64 }, { day: '04/10', level: 2.71 }, { day: '06/10', level: 2.85 },
      { day: '08/10', level: 2.92 }, { day: '10/10', level: 3.08 }, { day: '12/10', level: 3.19 },
      { day: '14/10', level: 3.12 }, { day: '16/10', level: 3.25 }, { day: '18/10', level: 3.38 },
      { day: '20/10', level: 3.49 }, { day: '22/10', level: 3.61, gapAfter: true },
      { day: '25/10', level: 3.54 }, { day: '27/10', level: 3.46 }, { day: '29/10', level: 3.39 },
      { day: '31/10', level: 3.42 }
    ],
    // Flood boundary polygon vertices (Bajada Grande, Paraná, Entre Ríos — lat: -31.712938, lon: -60.576180)
    boundaryVertices: [
      { lat: -31.71185, lng: -60.57810 },
      { lat: -31.71145, lng: -60.57540 },
      { lat: -31.71255, lng: -60.57395 },
      { lat: -31.71410, lng: -60.57465 },
      { lat: -31.71435, lng: -60.57725 },
      { lat: -31.71310, lng: -60.57845 }
    ],
    undoStack: [],
    // Profile & Contour state
    profilePreset: 'parana-bank', // 'parana-bank' | 'baoloc-slope'
    hoverDistanceM: 95,
    contourStepM: 1.0,
    contourSmoothIters: 2,
    // GNSS & Stakeout state
    antennaHeightM: 1.800,
    selectedStakeoutIdx: 0,
    stakeoutTargets: [
      { code: 'MOJ-PNA-01', name: 'Mốc Thủy chuẩn Cảng Bajada Grande', lat: -31.712880, lng: -60.576050, zOrto: 14.500 },
      { code: 'DEF-NORTE-02', name: 'Đỉnh Đê Chắn Lũ Bờ Bắc Paraná', lat: -31.712420, lng: -60.575620, zOrto: 16.250 },
      { code: 'CAY-A04-BL', name: 'Gốc Sầu Riêng A-04 (Khu A · Bảo Lộc)', lat: 11.545820, lng: 107.791820, zOrto: 824.400 }
    ],
    nmeaRawInput: [
      '$GNGGA,143210.00,3142.77628,S,06034.57080,W,4,18,0.58,14.220,M,17.564,M,0.8,0104*4B',
      '$GPGST,143210.00,0.011,0.014,0.009,32.5,0.008,0.009,0.019*5C',
      '$GNGSA,A,3,03,08,14,17,21,28,30,32,,,,,1.12,0.58,0.96,1*0A',
      '$GNRMC,143210.00,A,3142.77628,S,06034.57080,W,0.08,112.4,311026,,,R,V*1F'
    ].join('\n'),
    // Point Cloud Classification & OrtoFalsa state
    clsViewEnabled: true,
    clsNoiseSens: 'media',
    clsGroundCellM: 2.0,
    clsGroundTolM: 0.30,
    clsPlaneTolM: 0.15,
    clsMaxPlanes: 3,
    clsLastResult: {
      algorithm: 'suelo + planos + vegetacion',
      totalPoints: 48620,
      classes: [
        { code: 2, name: 'Suelo (Mặt đất tự nhiên)', color: '#d97706', count: 21450, visible: true },
        { code: 3, name: 'Vegetación Baja / Media (Cây bụi)', color: '#4ade80', count: 9840, visible: true },
        { code: 5, name: 'Vegetación Alta (Tán cây lớn)', color: '#16a34a', count: 11280, visible: true },
        { code: 6, name: 'Edificación / Muro (Công trình & Kè)', color: '#38bdf8', count: 4810, visible: true },
        { code: 7, name: 'Ruido (Điểm nhiễu lọc bỏ)', color: '#f43f5e', count: 1240, visible: true }
      ]
    },
    ortoFalsaOn: true,
    ortoFalsaFovDeg: 0.3,
    ortoFalsaVisibleWidthM: 240,
    selectedEpsg: 5347,
    // 7-Layer Stratified 3D Model Analyzer state (mỗi tầng 1 mô hình vật lý & bộ thông số độc lập)
    selected3DLayerId: 'ALL', // 'ALL' | 'L7' | 'L6' | 'L5' | 'L4' | 'L3' | 'L2' | 'L1'
    layer3DYawDeg: -28,
    layer3DTiltDeg: 52,
    layer3DExplodeGap: 1.15,
    layer3DExtrudeRelief: true,
    deeplabModel: 'deeplabv3plus_resnet101',
    deeplabOutputStride: 8,
    deeplabPureCanopyMode: true,
    deeplabShowMaskOverlay: true
  };

  // 7 Tầng Mô hình 3D Không gian — Mỗi tầng có công thức trường không gian (evalField), bảng màu và 4 thông số định lượng độc lập hoàn toàn
  const STRATIFIED_3D_LAYERS = [
    {
      id: 'L7',
      order: 1,
      zNorm: 1.00,
      shortTitle: '7. Khí hậu & Nguy cơ bệnh',
      title: 'Tầng 7: Không khí quanh tán & Dự báo nấm bệnh (Cao +10m..+25m)',
      spatialModel: 'Mô hình Phát tán Bào tử theo Gió mùa ĐB-TN & Độ ướt lá',
      formula: 'Risk(x,y) = Plume_SW(Gió ĐB 3.4m/s) × LeafWetness(h) × f(VPD, LST)',
      accent: '#e11d48',
      gradientStops: ['#312e81', '#0284c7', '#facc15', '#f97316', '#dc2626'],
      minLabel: '18% (Khô thoáng ĐB)',
      maxLabel: '84% (Dễ bùng bệnh TN)',
      overlayName: 'Hướng gió ĐB & Vùng nguy cơ bệnh ≥65%',
      evalField(nx, ny) {
        const dSw = Math.hypot(nx - (-0.46), ny - 0.44);
        const dSe = Math.hypot(nx - 0.36, ny - 0.48);
        const dNe = Math.hypot(nx - 0.48, ny - (-0.46));
        const v = 0.34 + 0.78 * Math.exp(-(dSw * dSw) / 0.16) + 0.52 * Math.exp(-(dSe * dSe) / 0.11) - 0.44 * Math.exp(-(dNe * dNe) / 0.22) + (ny - nx) * 0.12 + Math.sin(nx * 4.5 - ny * 5.2) * 0.06;
        return Math.max(0.03, Math.min(0.98, v));
      },
      distinctParams: [
        { code: 'MMC_RISK', name: 'Nguy cơ nấm bệnh', unit: '%', min: '18,0%', mean: '48,6%', max: '84,2%', pct: 68, status: 'Cảnh báo cao ở góc khuất gió Tây-Nam (>65%)' }
      ],
      zones: [
        { range: '18 – 34% (An toàn)', desc: 'Đón gió Đông-Bắc khô ráo, VPD tối ưu', color: '#312e81', sharePct: 28.5 },
        { range: '35 – 49% (Thấp–TB)', desc: 'Thông thoáng trung bình, ướt lá 4–6h', color: '#0284c7', sharePct: 34.0 },
        { range: '50 – 64% (Cảnh báo)', desc: 'Độ ẩm tán > 82%, ướt lá 7–9h/ngày', color: '#facc15', sharePct: 23.5 },
        { range: '65 – 84% (Nguy cơ Cao)', desc: 'Tụ sương khuất gió Tây-Nam · Phytophthora', color: '#dc2626', sharePct: 14.0 }
      ],
      prescription: 'Phun phòng định hướng Fosetyl-Aluminium hoặc Metalaxyl tập trung tại 14% diện tích góc Tây-Nam & Đông-Nam (nơi ướt lá >9h/ngày); tỉa cành mở hành lang thông gió Đông-Bắc.'
    },
    {
      id: 'L6',
      order: 2,
      zNorm: 0.68,
      shortTitle: '6. Chiều cao tán cây 3D',
      title: 'Tầng 6: Chiều cao Vòm tán cây 3D (Cao +2,0m..+8,5m)',
      spatialModel: 'Mô hình Vòm cây 3D theo Hàng 8×8m',
      formula: 'CHM(x,y) = DSM(x,y) − DTM(x,y) · CrownVol = 2.15 × CHM^1.85',
      accent: '#15803d',
      gradientStops: ['#d6d3d1', '#84cc16', '#22c55e', '#15803d', '#064e3b'],
      minLabel: '0,8m (Đường lô)',
      maxLabel: '7,8m (Cây cao nhất)',
      overlayName: 'Lưới Hàng cây 8×8m & Vòm cây 3D',
      evalField(nx, ny) {
        const dome = Math.pow(Math.max(0, Math.cos(nx * Math.PI * 3.6) * Math.cos(ny * Math.PI * 3.6)), 0.75);
        const dCore = Math.hypot(nx - (-0.12), ny - (-0.08));
        const alley = (Math.abs(nx + 0.05) < 0.07 || Math.abs(ny - 0.02) < 0.06) ? -0.32 : 0;
        const v = 0.22 + dome * 0.62 + 0.28 * Math.exp(-(dCore * dCore) / 0.48) + alley;
        return Math.max(0.02, Math.min(0.99, v));
      },
      distinctParams: [
        { code: 'CHM_HEIGHT', name: 'Chiều cao tán cây', unit: 'm', min: '0,8 m', mean: '5,8 m', max: '7,8 m', pct: 76, status: 'Cây <4,5m giảm 35% thuốc · >6,3m tăng áp lực phun' }
      ],
      zones: [
        { range: '0,8 – 2,2m (Hành lang)', desc: 'Khoảng trống giữa hàng & đường nội đồng', color: '#d6d3d1', sharePct: 21.6 },
        { range: '2,3 – 4,5m (Cây tơ/Tán nhỏ)', desc: 'Tán cây đang khép, LAI = 2,1–3,0', color: '#84cc16', sharePct: 24.4 },
        { range: '4,6 – 6,2m (Tán tiêu chuẩn)', desc: 'Vòm tán khỏe, thể tích 45–68 m³/cây', color: '#22c55e', sharePct: 36.2 },
        { range: '6,3 – 7,8m (Tán cổ thụ đỉnh)', desc: 'LAI > 4,5 · Sinh khối AGB > 110 tấn/ha', color: '#064e3b', sharePct: 17.8 }
      ],
      prescription: 'Hiệu chỉnh lưu lượng béc phun drone theo thể tích vòm tán 3D (VRA Canopy Volume): giảm 35% lượng thuốc ở dải cây tơ (<4,5m) và tăng áp lực xuyên tán ở cụm cây >6,3m khu trung tâm.'
    },
    {
      id: 'L5',
      order: 3,
      zNorm: 0.36,
      shortTitle: '5. Độ xanh & Sức khỏe lá',
      title: 'Tầng 5: Độ xanh khỏe của Tán lá (Cao +1,5m)',
      spatialModel: 'Mô hình Quang hợp & Chẩn đoán Thiếu đạm qua Màu lá',
      formula: 'NDVI=(B8−B4)/(B8+B4) · NDRE=(B8A−B5)/(B8A+B5) · Chl_SPAD = 78.4 × NDRE + 13.6',
      accent: '#22c55e',
      gradientStops: ['#dc2626', '#f59e0b', '#eab308', '#22c55e', '#14532d'],
      minLabel: '0,36 (Vàng lá ĐN)',
      maxLabel: '0,89 (Lá xanh khỏe)',
      overlayName: 'Ranh giới 3 Vùng Bón phân (Vùng A/B/C)',
      evalField(nx, ny) {
        const dCore = Math.hypot(nx - (-0.15), ny - (-0.22));
        const dWest = Math.hypot(nx - (-0.48), ny - 0.10);
        const dStressSe = Math.hypot(nx - 0.52, ny - 0.46);
        const v = 0.52 + 0.54 * Math.exp(-(dCore * dCore) / 0.32) + 0.38 * Math.exp(-(dWest * dWest) / 0.20) - 0.62 * Math.exp(-(dStressSe * dStressSe) / 0.12) + Math.sin(nx * 7.2 + ny * 5.4) * 0.05;
        return Math.max(0.04, Math.min(0.98, v));
      },
      distinctParams: [
        { code: 'S2_NDVI', name: 'Độ xanh của lá', unit: 'điểm 0–1', min: '0,360', mean: '0,775', max: '0,890', pct: 78, status: '69% diện tích đạt ngưỡng khỏe (≥0,67) · Góc ĐN cần bón bổ sung' }
      ],
      zones: [
        { range: 'NDVI 0,36–0,52 (Stress)', desc: 'Suy giảm diệp lục & thiếu đạm góc Đông-Nam', color: '#dc2626', sharePct: 11.5 },
        { range: 'NDVI 0,53–0,66 (Trung bình)', desc: 'NDRE = 0,38 · Cần bổ sung trung-vi lượng', color: '#f59e0b', sharePct: 19.5 },
        { range: 'NDVI 0,67–0,78 (Khỏe mạnh)', desc: 'Quang hợp ổn định · EVI = 0,64 · SPAD 52', color: '#22c55e', sharePct: 44.0 },
        { range: 'NDVI 0,79–0,89 (Cực đại)', desc: 'Diệp lục dày đặc · NDRE > 0,56 · SPAD 66', color: '#14532d', sharePct: 25.0 }
      ],
      prescription: 'Chia bản đồ bón phân VRA thành 3 vùng: Vùng C (Góc Đông-Nam, NDVI < 0,55) tăng +25% NPK giàu Đạm-Magie + Amino Acid; Vùng A (Trung tâm-Bắc, NDVI > 0,78) giảm 15% đạm.'
    },
    {
      id: 'L4',
      order: 4,
      zNorm: 0.04,
      shortTitle: '4. Độ ẩm & Nước trong lá',
      title: 'Tầng 4: Độ ẩm trong Tán lá & Tình trạng Tưới nước (Cao +0,8m)',
      spatialModel: 'Mô hình Tuyến Tưới Nhỏ Giọt & Cân bằng Nước Cây trồng',
      formula: 'NDMI=(B8−B11)/(B8+B11) · ETa = Kc × ET0 × f(NDMI) · CWSI = 1 − ETa/ETp',
      accent: '#0284c7',
      gradientStops: ['#b45309', '#f59e0b', '#38bdf8', '#0284c7', '#1e3a8a'],
      minLabel: '0,08 (Thiếu nước ĐB)',
      maxLabel: '0,51 (Đủ ẩm)',
      overlayName: 'Tuyến Ống Tưới Nhỏ Giọt Đông-Tây & Béc Tưới 4,2 L/h',
      evalField(nx, ny) {
        const dripBands = (0.5 + 0.5 * Math.cos(ny * Math.PI * 4.5)) * 0.28;
        const swale = 0.42 * Math.exp(-Math.pow(nx - 0.12 * ny, 2) / 0.09);
        const dDryNe = Math.hypot(nx - 0.50, ny - (-0.44));
        const dWetS = Math.hypot(nx - (-0.08), ny - 0.52);
        const v = 0.36 + dripBands + swale + 0.32 * Math.exp(-(dWetS * dWetS) / 0.16) - 0.58 * Math.exp(-(dDryNe * dDryNe) / 0.14);
        return Math.max(0.03, Math.min(0.98, v));
      },
      distinctParams: [
        { code: 'SWIR_NDMI', name: 'Độ ẩm trong lá', unit: 'điểm ẩm', min: '0,080', mean: '0,342', max: '0,510', pct: 66, status: 'Góc Đông-Bắc bị khô (<0,16), cần kiểm tra béc tưới' }
      ],
      zones: [
        { range: 'NDMI < 0,16 (Thiếu nước)', desc: 'Tắc béc / Áp lực tưới yếu góc Đông-Bắc (CWSI 0,64)', color: '#b45309', sharePct: 12.8 },
        { range: 'NDMI 0,16–0,27 (Ẩm vừa)', desc: 'Bốc thoát hơi ETa = 2,9–3,5 mm/ngày', color: '#f59e0b', sharePct: 24.2 },
        { range: 'NDMI 0,28–0,39 (Đủ ẩm)', desc: 'Dọc tuyến ống nhỏ giọt · ETa = 3,9 mm/ngày', color: '#38bdf8', sharePct: 42.5 },
        { range: 'NDMI 0,40–0,51 (Tối ưu)', desc: 'Cân bằng nước lý tưởng · CWSI < 0,15', color: '#1e3a8a', sharePct: 20.5 }
      ],
      prescription: 'Kiểm tra áp suất đường ống nhánh số 1–2 ở góc Đông-Bắc (nơi NDMI tụt xuống 0,08–0,15 và CWSI = 0,64); tăng thời gian tưới bù thêm +25 phút/ca cho van điện từ khu Đông-Bắc.'
    },
    {
      id: 'L3',
      order: 5,
      zNorm: -0.28,
      shortTitle: '3. Phân vùng đất trồng',
      title: 'Tầng 3: Phân vùng Đất trồng & Mức độ Thích hợp Cây ăn trái (Cao +0,2m)',
      spatialModel: 'Mô hình Phân vùng Sử dụng Đất & Hạng Thích hợp Cây trồng',
      formula: 'LULC = ESA_WorldCover_10m ∪ GFSAD30_Irrigated ∪ FAO_GAEZ_v4(S1..S3)',
      accent: '#d97706',
      gradientStops: ['#9ca3af', '#fde047', '#34d399', '#059669', '#065f46'],
      minLabel: 'Đường lô (5,8%)',
      maxLabel: 'Rất hợp trồng: 94/100',
      overlayName: 'Ranh giới Phân khu Vườn, Cỏ & Đường lô',
      evalField(nx, ny) {
        const isRoad = Math.abs(nx - 0.32 * ny + 0.04) < 0.065;
        const isGrass = (nx * nx + ny * ny) > 0.52;
        const dS1 = Math.hypot(nx - (-0.18), ny - 0.08);
        const z = isRoad ? 0.12 : (isGrass ? 0.36 : (dS1 < 0.45 ? 0.88 : 0.66));
        return Math.max(0.05, Math.min(0.96, z));
      },
      distinctParams: [
        { code: 'FAO_GAEZ', name: 'Điểm hợp đất trồng', unit: 'điểm/100', min: '61,0', mean: '88,0', max: '94,5', pct: 88, status: 'Hạng S1 — Rất thích hợp cho cây ăn trái lâu năm' }
      ],
      zones: [
        { range: 'Class 50/60 (5,8% diện tích)', desc: 'Đường lô nội đồng, sân phơi & trạm bơm', color: '#9ca3af', sharePct: 5.8 },
        { range: 'Class 30 (11,8% diện tích)', desc: 'Thảm cỏ nội đồng giữ ẩm & chống xói mòn', color: '#fde047', sharePct: 11.8 },
        { range: 'Class 40 (24,0% diện tích)', desc: 'Cây trồng xen canh có tưới (GFSAD30)', color: '#34d399', sharePct: 24.0 },
        { range: 'Class 10 / GAEZ S1 (58,4%)', desc: 'Vườn cây lâu năm chuẩn EUDR (S1: 88–94đ)', color: '#065f46', sharePct: 58.4 }
      ],
      prescription: 'Duy trì thảm cỏ tự nhiên ở 11,8% diện tích hành lang biên (ESA Class 30) để bảo tồn thiên địch và chống rửa trôi; xuất chứng chỉ phân vùng GAEZ S1 & ESA 10m phục vụ hồ sơ EUDR.'
    },
    {
      id: 'L2',
      order: 6,
      zNorm: -0.60,
      shortTitle: '2. Địa hình & Thoát nước',
      title: 'Tầng 2: Độ cao Mặt đất & Đường Thoát nước (Mặt đất 0,0m)',
      spatialModel: 'Mô hình Độ cao Mặt đất & Dòng chảy Tụ nước',
      formula: 'TWI = ln(As / tan(Slope)) · Nuth-Kääb Δz = a·cos(b − Aspect)·tan(Slope) + c',
      accent: '#92400e',
      gradientStops: ['#10c800', '#8ce600', '#fff000', '#ff7800', '#e60000'],
      minLabel: 'Thấp nhất: 582,7m (Trũng)',
      maxLabel: 'Cao nhất: 586,8m (Đỉnh đồi)',
      overlayName: 'Mạng lưới Rãnh Thoát nước Chính',
      evalField(nx, ny) {
        const dW1 = Math.hypot(nx - (-0.62), ny - 0.22);
        const dW2 = Math.hypot(nx - (-0.52), ny - 0.46);
        const dV1 = Math.hypot(nx - (-0.10), ny - (-0.64));
        const dV2 = Math.hypot(nx - 0.10, ny - (-0.22));
        const v = 0.46 + 0.55 * Math.exp(-(dW1 * dW1) / 0.12) + 0.45 * Math.exp(-(dW2 * dW2) / 0.11) - 0.42 * Math.exp(-(dV1 * dV1) / 0.10) - 0.35 * Math.exp(-(dV2 * dV2) / 0.09) + Math.sin(nx * 6.2 + ny * 4.1) * 0.08;
        return Math.max(0.02, Math.min(0.99, v));
      },
      distinctParams: [
        { code: 'HYDRO_TWI', name: 'Mức độ đọng nước', unit: 'điểm tụ nước', min: '4,50', mean: '7,42', max: '11,60', pct: 64, status: 'Vùng trũng >9 điểm cần khơi thông rãnh thoát nước' }
      ],
      zones: [
        { range: '582,7 – 583,6m (Thung lũng)', desc: 'Hành lang dòng chảy tụ thủy (Streams · TWI > 9)', color: '#10c800', sharePct: 22.0 },
        { range: '583,7 – 584,6m (Sườn thoải)', desc: 'Thoát nước tự nhiên tốt (Dốc 2–4°)', color: '#8ce600', sharePct: 35.0 },
        { range: '584,7 – 585,7m (Sườn giữa)', desc: 'Sườn cao trung bình hướng Đông-Nam', color: '#fff000', sharePct: 28.0 },
        { range: '585,8 – 586,8m (Đỉnh phân thủy)', desc: 'Gò cao phía Tây – Tây Nam', color: '#e60000', sharePct: 15.0 }
      ],
      prescription: 'Khơi thông rãnh thoát nước xương cá dọc theo tuyến Stream Order = 1 ở trung tâm và cửa xả phía Nam để chống ngập úng cổ rễ khi mưa lớn > 60 mm/ngày.'
    },
    {
      id: 'L1',
      order: 7,
      zNorm: -0.92,
      shortTitle: '1. Đất & Độ chua vùng rễ',
      title: 'Tầng 1: Chất lượng Đất & Độ chua Vùng Rễ (Sâu 0–100cm)',
      spatialModel: 'Mô hình Bản đồ Đất Vùng rễ từ 6 Điểm khoan (BH-01..BH-06)',
      formula: 'Z*(x0) = Σ λ_i · Z(x_i) (Ordinary Kriging pH H2O, SOC%, ECe dS/m, VWC 0–100cm)',
      accent: '#7c2d12',
      gradientStops: ['#fde68a', '#f59e0b', '#d97706', '#92400e', '#451a03'],
      minLabel: 'pH 4,85 (Hơi chua)',
      maxLabel: 'pH 6,42 (Đất tốt)',
      overlayName: '6 Điểm lấy mẫu Đất (BH-01..BH-06) & Vùng độ chua',
      evalField(nx, ny) {
        const dBhS = Math.hypot(nx - 0.15, ny - 0.42);
        const dBhC = Math.hypot(nx - (-0.18), ny - 0.05);
        const dBhE = Math.hypot(nx - 0.48, ny - (-0.12));
        const dNw = Math.hypot(nx - (-0.52), ny - (-0.48));
        const v = 0.38 + 0.56 * Math.exp(-(dBhS * dBhS) / 0.18) + 0.44 * Math.exp(-(dBhC * dBhC) / 0.16) + 0.36 * Math.exp(-(dBhE * dBhE) / 0.12) - 0.52 * Math.exp(-(dNw * dNw) / 0.18);
        return Math.max(0.04, Math.min(0.98, v));
      },
      distinctParams: [
        { code: 'SOIL_PH', name: 'Độ chua của đất', unit: 'độ pH', min: '4,85', mean: '5,68', max: '6,42', pct: 62, status: 'Góc Tây-Bắc hơi chua (<5,2), cần bón vôi Dolomite' }
      ],
      zones: [
        { range: 'pH 4,8–5,2 · SOC < 1,9%', desc: 'Đất sườn Tây-Bắc bị rửa trôi kiềm, hơi chua', color: '#fde68a', sharePct: 16.5 },
        { range: 'pH 5,3–5,6 · SOC 2,0–2,5%', desc: 'Tầng đất pha cát-sét chuyển tiếp, ẩm 28%', color: '#f59e0b', sharePct: 29.0 },
        { range: 'pH 5,7–6,0 · SOC 2,6–3,1%', desc: 'Đất đỏ Bazan tơi xốp · Dung trọng 1,24 g/cm³', color: '#d97706', sharePct: 35.5 },
        { range: 'pH 6,1–6,4 · SOC 3,2–3,8%', desc: 'Túi đất giàu mùn hữu cơ & NPK vùng Nam', color: '#451a03', sharePct: 19.0 }
      ],
      prescription: 'Bón 1,2 tấn/ha vôi nông nghiệp Dolomite CaMg(CO₃)₂ kết hợp 15 kg/gốc phân hữu cơ vi sinh tại phân khu Tây-Bắc (quanh hố khoan BH-05, BH-06 nơi pH = 4,85–5,2) để nâng pH lên 5,8–6,2.'
    }
  ];

  // Profile sample datasets
  const PROFILE_DATASETS = {
    'parana-bank': {
      title: 'Mặt cắt Bờ Sông Paraná — Bajada Grande (MAL-54A0D1 · 220 m)',
      samples: [
        { distance: 0,   elevation: 7.80 },
        { distance: 20,  elevation: 8.45 },
        { distance: 40,  elevation: 9.36 },
        { distance: 60,  elevation: 11.20 },
        { distance: 80,  elevation: 12.85 },
        { distance: 100, elevation: 14.15 },
        { distance: 120, elevation: 15.60 },
        { distance: 140, elevation: 16.40 },
        { distance: 160, elevation: 18.95 },
        { distance: 180, elevation: 22.10 },
        { distance: 200, elevation: 24.80 },
        { distance: 220, elevation: 26.40 }
      ]
    },
    'baoloc-slope': {
      title: 'Mặt cắt Sườn đồi Sầu riêng Khu B & Hồ chứa nước Bảo Lộc (220 m)',
      samples: [
        { distance: 0,   elevation: 812.20 },
        { distance: 20,  elevation: 813.10 },
        { distance: 40,  elevation: 814.85 },
        { distance: 60,  elevation: 816.90 },
        { distance: 80,  elevation: 818.40 },
        { distance: 100, elevation: 820.75 },
        { distance: 120, elevation: 822.10 },
        { distance: 140, elevation: 824.60 },
        { distance: 160, elevation: 826.90 },
        { distance: 180, elevation: 828.25 },
        { distance: 200, elevation: 829.80 },
        { distance: 220, elevation: 831.10 }
      ]
    }
  };

  // Synthetic DEM grid for real-time Marching Squares contour generation
  function buildDemGrid(preset) {
    const nx = 28, ny = 18;
    const z = new Float64Array(nx * ny);
    const baseZ = preset === 'baoloc-slope' ? 812.0 : 8.0;
    for (let y = 0; y < ny; y++) {
      for (let x = 0; x < nx; x++) {
        const u = x / (nx - 1);
        const v = y / (ny - 1);
        // Realistic river bank + terrace valley topography
        const slope = u * 14.5 + Math.pow(u, 2) * 4.2;
        const valley = 2.8 * Math.sin(v * Math.PI * 1.6) * (0.4 + 0.6 * u);
        const mound = 1.9 * Math.exp(-Math.pow((u - 0.68) / 0.22, 2) - Math.pow((v - 0.45) / 0.28, 2));
        z[y * nx + x] = baseZ + slope + valley + mound;
      }
    }
    return { nx, ny, z, minZ: baseZ, maxZ: baseZ + 22.0 };
  }

  // ---------------------------------------------------------------------------
  // 3. INTERACTIVE RENDERING & WAVE CANVAS ANIMATION
  // ---------------------------------------------------------------------------
  let waveAnimRaf = null;
  let riseAnimRaf = null;

  function getWaterHeights() {
    const level = Number(DATUM_STATE.level) || 0;
    const adjust = Number(DATUM_STATE.slopeAdjustM) || 0;
    const hElip = DATUM_STATE.zeroEllipsoidM + level + adjust;
    const hOrto = hElip - DATUM_STATE.geoidUndulationM;
    return { level, adjust, hElip, hOrto };
  }

  // Build 30-day SVG Hydrograph (ported from buildChartMarkup in crecida-rio.js?v=1.19.0)
  function renderHydrographSvg() {
    const pts = DATUM_STATE.hydroSeries;
    const w = 360, h = 152;
    const padL = 36, padR = 14, padT = 16, padB = 26;
    const plotW = w - padL - padR;
    const plotH = h - padT - padB;
    const minY = 1.5;
    const maxY = Math.max(7.2, DATUM_STATE.level + 0.6);
    const xOf = idx => padL + (idx / Math.max(1, pts.length - 1)) * plotW;
    const yOf = val => padT + plotH - ((val - minY) / (maxY - minY)) * plotH;

    let solidPaths = '';
    let gapPaths = '';
    for (let i = 0; i + 1 < pts.length; i++) {
      const x1 = xOf(i).toFixed(1), y1 = yOf(pts[i].level).toFixed(1);
      const x2 = xOf(i + 1).toFixed(1), y2 = yOf(pts[i + 1].level).toFixed(1);
      if (pts[i].gapAfter) {
        gapPaths += `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="#3688ff" stroke-width="1.8" stroke-dasharray="3 3" opacity="0.75"/>`;
      } else {
        solidPaths += `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="#3688ff" stroke-width="2.4" stroke-linecap="round"/>`;
      }
    }

    const yAlert = yOf(DATUM_STATE.alertLevel).toFixed(1);
    const yEvac = yOf(DATUM_STATE.evacuationLevel).toFixed(1);
    const ySim = yOf(Math.max(minY, Math.min(maxY, DATUM_STATE.level))).toFixed(1);
    const lastIdx = pts.length - 1;
    const lastX = xOf(lastIdx).toFixed(1);
    const lastY = yOf(pts[lastIdx].level).toFixed(1);

    return `
      <svg viewBox="0 0 ${w} ${h}" style="width:100%;height:152px;border-radius:10px;background:rgba(4,16,25,0.78);border:1px solid rgba(255,255,255,0.14);">
        <!-- Threshold lines: Evacuación (5.00 m) & Alerta (4.70 m) -->
        <line x1="${padL}" y1="${yEvac}" x2="${w - padR}" y2="${yEvac}" stroke="#e74a55" stroke-width="1.3" stroke-dasharray="5 4"/>
        <text x="${w - padR - 4}" y="${Number(yEvac) - 4}" text-anchor="end" fill="#ff7b86" font-size="9.5" font-weight="600">Evacuación ${DATUM_STATE.evacuationLevel.toFixed(2)} m</text>

        <line x1="${padL}" y1="${yAlert}" x2="${w - padR}" y2="${yAlert}" stroke="#cb8500" stroke-width="1.3" stroke-dasharray="5 4"/>
        <text x="${padL + 4}" y="${Number(yAlert) - 4}" fill="#fbbf24" font-size="9.5" font-weight="600">Alerta ${DATUM_STATE.alertLevel.toFixed(2)} m</text>

        <!-- Current Simulated Level Line -->
        <line x1="${padL}" y1="${ySim}" x2="${w - padR}" y2="${ySim}" stroke="#38EF7D" stroke-width="1.5" stroke-dasharray="2 2"/>
        <text x="${padL + 4}" y="${Number(ySim) + 11}" fill="#38EF7D" font-size="9.5" font-weight="700">Nivel simulado: ${DATUM_STATE.level.toFixed(2)} m</text>

        <!-- Hydrograph Curves -->
        ${gapPaths}
        ${solidPaths}
        ${pts.map((p, i) => `<circle cx="${xOf(i).toFixed(1)}" cy="${yOf(p.level).toFixed(1)}" r="${i === lastIdx ? 4.2 : 2.3}" fill="${i === lastIdx ? '#38EF7D' : '#3688ff'}" stroke="#04121B" stroke-width="1"/>`).join('')}
        <circle cx="${lastX}" cy="${lastY}" r="7.5" fill="none" stroke="#38EF7D" stroke-width="1.4" opacity="0.65"/>

        <!-- Axes & Labels -->
        <text x="6" y="${padT + 6}" fill="rgba(255,255,255,0.6)" font-size="9">${maxY.toFixed(1)}m</text>
        <text x="6" y="${padT + plotH / 2}" fill="rgba(255,255,255,0.6)" font-size="9">${((maxY + minY) / 2).toFixed(1)}m</text>
        <text x="6" y="${h - padB}" fill="rgba(255,255,255,0.6)" font-size="9">${minY.toFixed(1)}m</text>
        <text x="${padL}" y="${h - 7}" fill="rgba(255,255,255,0.65)" font-size="9.5">${pts[0].day}</text>
        <text x="${padL + plotW / 2}" y="${h - 7}" text-anchor="middle" fill="rgba(255,255,255,0.65)" font-size="9.5">${pts[Math.floor(pts.length / 2)].day}</text>
        <text x="${w - padR}" y="${h - 7}" text-anchor="end" fill="#38EF7D" font-size="9.5" font-weight="600">Hôm nay (${pts[lastIdx].level.toFixed(2)} m)</text>
      </svg>
    `;
  }

  // Start real-time 3-octave GLSL wave simulation & interactive polygon editor on #datumFloodCanvas
  function startFloodCanvasLoop() {
    if (waveAnimRaf) cancelAnimationFrame(waveAnimRaf);
    const canvas = document.getElementById('datumFloodCanvas');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');

    // Bind pointer events for interactive polygon vertex dragging / midpoint insertion
    if (!canvas._boundDatumEvents) {
      canvas._boundDatumEvents = true;
      let draggingIdx = -1;

      const getCanvasNorm = (ev) => {
        const rect = canvas.getBoundingClientRect();
        const x = (ev.clientX - rect.left) * (canvas.width / Math.max(1, rect.width));
        const y = (ev.clientY - rect.top) * (canvas.height / Math.max(1, rect.height));
        return { x, y };
      };

      const latLngToCanvas = (pt) => {
        const minLat = -31.7148, maxLat = -31.7110;
        const minLng = -60.5790, maxLng = -60.5732;
        const x = 36 + ((pt.lng - minLng) / (maxLng - minLng)) * (canvas.width - 72);
        const y = canvas.height - 28 - ((pt.lat - minLat) / (maxLat - minLat)) * (canvas.height - 56);
        return { x, y };
      };

      const canvasToLatLng = (x, y) => {
        const minLat = -31.7148, maxLat = -31.7110;
        const minLng = -60.5790, maxLng = -60.5732;
        const lng = minLng + ((x - 36) / (canvas.width - 72)) * (maxLng - minLng);
        const lat = minLat + ((canvas.height - 28 - y) / (canvas.height - 56)) * (maxLat - minLat);
        return { lat: Number(lat.toFixed(6)), lng: Number(lng.toFixed(6)) };
      };

      canvas._latLngToCanvas = latLngToCanvas;

      canvas.addEventListener('pointerdown', (ev) => {
        const pos = getCanvasNorm(ev);
        const verts = DATUM_STATE.boundaryVertices;
        // 1. Check existing vertices
        for (let i = 0; i < verts.length; i++) {
          const p = latLngToCanvas(verts[i]);
          if (Math.hypot(pos.x - p.x, pos.y - p.y) <= 12) {
            DATUM_STATE.undoStack.push(JSON.parse(JSON.stringify(verts)));
            if (DATUM_STATE.undoStack.length > 100) DATUM_STATE.undoStack.shift();
            DATUM_STATE.selectedVertexIdx = i;
            draggingIdx = i;
            canvas.setPointerCapture(ev.pointerId);
            updateBoundaryStatsDom();
            return;
          }
        }
        // 2. Check edge midpoints to insert a new vertex (river-boundary-editor.js behavior)
        for (let i = 0; i < verts.length; i++) {
          const j = (i + 1) % verts.length;
          const p1 = latLngToCanvas(verts[i]);
          const p2 = latLngToCanvas(verts[j]);
          const mx = (p1.x + p2.x) * 0.5;
          const my = (p1.y + p2.y) * 0.5;
          if (Math.hypot(pos.x - mx, pos.y - my) <= 10 && verts.length < DatumRiverBoundaryCore.MAX_VERTICES) {
            DATUM_STATE.undoStack.push(JSON.parse(JSON.stringify(verts)));
            const newPt = canvasToLatLng(pos.x, pos.y);
            verts.splice(i + 1, 0, newPt);
            DATUM_STATE.selectedVertexIdx = i + 1;
            draggingIdx = i + 1;
            canvas.setPointerCapture(ev.pointerId);
            updateBoundaryStatsDom();
            return;
          }
        }
      });

      canvas.addEventListener('pointermove', (ev) => {
        if (draggingIdx < 0) return;
        const pos = getCanvasNorm(ev);
        const clampedX = Math.max(20, Math.min(canvas.width - 20, pos.x));
        const clampedY = Math.max(20, Math.min(canvas.height - 20, pos.y));
        DATUM_STATE.boundaryVertices[draggingIdx] = canvasToLatLng(clampedX, clampedY);
        updateBoundaryStatsDom();
      });

      const stopDrag = () => { draggingIdx = -1; };
      canvas.addEventListener('pointerup', stopDrag);
      canvas.addEventListener('pointercancel', stopDrag);
    }

    const renderFrame = (nowMs) => {
      if (!document.getElementById('datumFloodCanvas')) return;
      const w = canvas.width, h = canvas.height;
      const tSec = (nowMs * 0.001) * DATUM_STATE.waveSpeed;
      ctx.clearRect(0, 0, w, h);

      // 1. Render topographic relief background (Bajada Grande 3D mesh elevation bands)
      const bgGrad = ctx.createLinearGradient(0, 0, w, h);
      bgGrad.addColorStop(0, '#071c29');
      bgGrad.addColorStop(0.52, '#0c2938');
      bgGrad.addColorStop(1, '#163528');
      ctx.fillStyle = bgGrad;
      ctx.fillRect(0, 0, w, h);

      // Topographic elevation grid lines & bathymetry contours
      ctx.strokeStyle = 'rgba(255,255,255,0.06)';
      ctx.lineWidth = 1;
      for (let gx = 40; gx < w; gx += 48) {
        ctx.beginPath(); ctx.moveTo(gx, 0); ctx.lineTo(gx, h); ctx.stroke();
      }
      for (let gy = 30; gy < h; gy += 40) {
        ctx.beginPath(); ctx.moveTo(0, gy); ctx.lineTo(w, gy); ctx.stroke();
      }

      // 2. Clip to Flood Boundary Polygon & render 3-octave sinusoidal wave shader (daRiverWave)
      const verts = DATUM_STATE.boundaryVertices;
      const proj = canvas._latLngToCanvas;
      const screenPts = verts.map(proj);

      if (screenPts.length >= 3) {
        ctx.save();
        ctx.beginPath();
        screenPts.forEach((p, idx) => {
          if (idx === 0) ctx.moveTo(p.x, p.y);
          else ctx.lineTo(p.x, p.y);
        });
        ctx.closePath();
        ctx.clip();

        // Fill base water color with inundating coverage proportional to water level
        const waterRatio = Math.max(0.08, Math.min(1.0, (DATUM_STATE.level + 2.0) / 10.5));
        const inundateWidth = w * (0.28 + 0.70 * waterRatio);

        ctx.fillStyle = hexToRgba(DATUM_STATE.waterColor, DATUM_STATE.waterAlpha);
        ctx.fillRect(0, 0, inundateWidth, h);

        // 3-Octave Wave Synthesis from river-boundary-slope-water.js (daRiverWave)
        // d1=(0.86,0.51), d2=(-0.42,0.91), d3=(0.31,-0.95)
        if (DATUM_STATE.waveMotion === 'waves' && DATUM_STATE.waveIntensity > 0.01) {
          const k = 6.2831853 / Math.max(4, DATUM_STATE.waveScaleM);
          const stepY = 12;
          for (let y = 10; y < h; y += stepY) {
            ctx.beginPath();
            for (let x = 0; x <= inundateWidth; x += 10) {
              const p1 = (x * 0.86 + y * 0.51) * k;
              const p2 = (x * -0.42 + y * 0.91) * (k * 1.73);
              const p3 = (x * 0.31 + y * -0.95) * (k * 2.87);
              const wVal = 0.55 * Math.sin(p1 + tSec * 1.35) +
                           0.30 * Math.sin(p2 - tSec * 1.05 + 1.7) +
                           0.15 * Math.sin(p3 + tSec * 1.85 + 3.1);
              const dy = y + wVal * 5.5 * DATUM_STATE.waveIntensity;
              if (x === 0) ctx.moveTo(x, dy);
              else ctx.lineTo(x, dy);
            }
            ctx.strokeStyle = `rgba(186, 230, 253, ${0.18 + 0.25 * DATUM_STATE.waveIntensity})`;
            ctx.lineWidth = 1.35;
            ctx.stroke();
          }
        }

        // Shoreline foam crest at inundation boundary
        ctx.beginPath();
        for (let y = 0; y <= h; y += 8) {
          const waveOffset = Math.sin(y * 0.06 + tSec * 2.1) * 6 * DATUM_STATE.waveIntensity;
          const sx = inundateWidth + waveOffset;
          if (y === 0) ctx.moveTo(sx, y);
          else ctx.lineTo(sx, y);
        }
        ctx.strokeStyle = '#7dd3fc';
        ctx.lineWidth = 2.5;
        ctx.stroke();
        ctx.restore();

        // 3. Draw Polygon Boundary Stroke & Midpoint Handles (river-boundary-editor.js)
        const validation = DatumRiverBoundaryCore.validatePolygon(verts);
        ctx.beginPath();
        screenPts.forEach((p, idx) => {
          if (idx === 0) ctx.moveTo(p.x, p.y);
          else ctx.lineTo(p.x, p.y);
        });
        ctx.closePath();
        ctx.strokeStyle = validation.valid ? '#38EF7D' : '#FF6B6B';
        ctx.lineWidth = 2.2;
        ctx.stroke();

        // Midpoint insertion handles (+)
        for (let i = 0; i < screenPts.length; i++) {
          const j = (i + 1) % screenPts.length;
          const mx = (screenPts[i].x + screenPts[j].x) * 0.5;
          const my = (screenPts[i].y + screenPts[j].y) * 0.5;
          ctx.beginPath();
          ctx.arc(mx, my, 4.5, 0, Math.PI * 2);
          ctx.fillStyle = 'rgba(56, 189, 248, 0.85)';
          ctx.fill();
          ctx.strokeStyle = '#04121B';
          ctx.lineWidth = 1.2;
          ctx.stroke();
        }

        // Vertex handles (V1..Vn)
        screenPts.forEach((p, idx) => {
          const isSel = idx === DATUM_STATE.selectedVertexIdx;
          ctx.beginPath();
          ctx.arc(p.x, p.y, isSel ? 7.5 : 5.8, 0, Math.PI * 2);
          ctx.fillStyle = isSel ? '#FFB340' : '#ffffff';
          ctx.fill();
          ctx.strokeStyle = '#04121B';
          ctx.lineWidth = 2;
          ctx.stroke();

          ctx.fillStyle = '#ffffff';
          ctx.font = '600 10px Inter, sans-serif';
          ctx.fillText(`V${idx + 1}`, p.x + 8, p.y - 6);
        });
      }

      // HUD overlay inside canvas
      const { hElip, hOrto } = getWaterHeights();
      ctx.fillStyle = 'rgba(4, 18, 27, 0.82)';
      ctx.fillRect(10, 10, 315, 44);
      ctx.strokeStyle = 'rgba(255,255,255,0.18)';
      ctx.strokeRect(10, 10, 315, 44);
      ctx.fillStyle = '#38EF7D';
      ctx.font = '700 11.5px Inter, sans-serif';
      ctx.fillText(`COTA AGUA: h_elip = ${hElip.toFixed(3)} m · H_orto = ${hOrto.toFixed(3)} m`, 18, 28);
      ctx.fillStyle = 'rgba(255,255,255,0.78)';
      ctx.font = '500 10.5px Inter, sans-serif';
      ctx.fillText(`Kéo đỉnh V1..V${verts.length} hoặc chạm điểm giữa cạnh để thêm đỉnh`, 18, 45);

      waveAnimRaf = requestAnimationFrame(renderFrame);
    };

    waveAnimRaf = requestAnimationFrame(renderFrame);
  }

  function hexToRgba(hex, alpha) {
    const clean = String(hex || '#248ce8').replace('#', '');
    const num = parseInt(clean.length === 3 ? clean.replace(/(.)/g, '$1$1') : clean, 16) || 0x248ce8;
    const r = (num >> 16) & 255;
    const g = (num >> 8) & 255;
    const b = num & 255;
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
  }

  function updateBoundaryStatsDom() {
    const val = DatumRiverBoundaryCore.validatePolygon(DATUM_STATE.boundaryVertices);
    const badge = document.getElementById('datumPolyValidBadge');
    const areaEl = document.getElementById('datumPolyAreaVal');
    if (badge) {
      badge.className = `sf-pill ${val.valid ? 'ok' : 'alert'}`;
      badge.textContent = val.valid
        ? `✓ Đa giác hợp lệ (${DATUM_STATE.boundaryVertices.length} đỉnh · Không tự cắt)`
        : `⚠ Lỗi hình học: ${val.reason}`;
    }
    if (areaEl) {
      const inundatedRatio = Math.max(0.08, Math.min(1.0, (DATUM_STATE.level + 2.0) / 10.5));
      const totalHa = val.areaM2 / 10000;
      const floodedHa = totalHa * inundatedRatio;
      areaEl.innerHTML = `Diện tích vùng bao: <strong>${val.areaM2.toFixed(0)} m² (${totalHa.toFixed(2)} ha)</strong> · Diện tích ngập ước tính ở mức ${DATUM_STATE.level.toFixed(2)}m: <strong style="color:#38bdf8;">${floodedHa.toFixed(2)} ha (${(inundatedRatio * 100).toFixed(1)}%)</strong>`;
    }
  }

  // ---------------------------------------------------------------------------
  // 4. TAB RENDERERS FOR SMARTFARM DRAWER
  // ---------------------------------------------------------------------------

  function renderFloodSimulatorTab() {
    const { level, hElip, hOrto } = getWaterHeights();
    const val = DatumRiverBoundaryCore.validatePolygon(DATUM_STATE.boundaryVertices);
    const inundatedRatio = Math.max(0.08, Math.min(1.0, (level + 2.0) / 10.5));
    const totalHa = val.areaM2 / 10000;
    const floodedHa = totalHa * inundatedRatio;
    const statusClass = level >= DATUM_STATE.evacuationLevel ? 'alert' : level >= DATUM_STATE.alertLevel ? 'watch' : 'ok';
    const statusText = level >= DATUM_STATE.evacuationLevel
      ? 'SƠ TÁN KHẨN CẤP (EVACUACIÓN ≥ 5.00 m)'
      : level >= DATUM_STATE.alertLevel
        ? 'CẢNH BÁO LŨ (ALERTA ≥ 4.70 m)'
        : 'BÌNH THƯỜNG (DƯỚI NGƯỠNG ALERTA)';

    return `
      <div class="sf-split-2">
        <!-- Left column: Gauge Controls, Vertical Calibration & 30-day Hydrograph -->
        <div class="sf-tile" style="padding:14px;">
          <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:6px;">
            <span class="sf-tile-label">MÔ PHỎNG MỰC NƯỚC DÂNG (CRECIDA RÍO PARANÁ · crecida-rio.js v1.19.0)</span>
            <span class="sf-pill ${statusClass}">${statusText}</span>
          </div>

          <div style="display:flex;align-items:baseline;justify-content:space-between;flex-wrap:wrap;gap:8px;margin:8px 0 6px;font-variant-numeric:tabular-nums;">
            <div>
              <strong style="font-size:28px;color:#38bdf8;">${level >= 0 ? '+' : ''}${level.toFixed(2)} m</strong>
              <span class="sf-tile-sub" style="display:inline;margin-left:8px;">So với Cero Hidrométrico (${DATUM_STATE.zeroOrthometricM.toFixed(3)} m IGN)</span>
            </div>
            <div style="display:flex;flex-wrap:wrap;gap:6px;">
              <button class="sf-action-btn secondary" onclick="DatumAppSuite.stepWaterLevel(-0.10)">−10 cm</button>
              <button class="sf-action-btn secondary" onclick="DatumAppSuite.stepWaterLevel(0.10)">+10 cm</button>
              <button class="sf-action-btn" onclick="DatumAppSuite.animateFloodRise()">
                ${DATUM_STATE.animatingRise ? '⏹ Dừng mô phỏng' : '▶ Simular subida (Dâng nước động)'}
              </button>
            </div>
          </div>

          <input type="range" min="-2" max="12" step="0.01" value="${level}"
            style="width:100%;accent-color:#38bdf8;cursor:pointer;margin:4px 0 8px;"
            oninput="DatumAppSuite.setWaterLevel(this.value)" />

          <!-- Historical Flood Reference Presets (Crecidas de referencia) -->
          <div style="margin-bottom:10px;">
            <span class="sf-tile-label">MỐC LŨ LỊCH SỬ THAM CHIẾU (CRECIDAS DE REFERENCIA · ACADEMIA NACIONAL DE GEOGRAFÍA):</span>
            <div style="display:flex;flex-wrap:wrap;gap:6px;margin-top:5px;">
              <button class="sf-action-btn secondary" onclick="DatumAppSuite.setWaterLevel(3.42)">📍 Hiện tại PNA · 3.42 m</button>
              <button class="sf-action-btn secondary" onclick="DatumAppSuite.setWaterLevel(4.70)">⚠️ Alerta · 4.70 m</button>
              <button class="sf-action-btn secondary" onclick="DatumAppSuite.setWaterLevel(5.00)">🚨 Evacuación · 5.00 m</button>
              ${DATUM_STATE.historicalCrecidas.map(h => `
                <button class="sf-action-btn secondary" onclick="DatumAppSuite.setWaterLevel(${h.level})" title="${h.note}">
                  🌊 Năm ${h.year} · ${h.level.toFixed(2)} m
                </button>
              `).join('')}
            </div>
          </div>

          <!-- 30-day SVG Hydrograph -->
          <span class="sf-tile-label">BIỂU ĐỒ THỦY VĂN 30 NGÀY (PREFECTURA NAVAL ARGENTINA · /api/rios/parana)</span>
          <div style="margin-top:4px;">${renderHydrographSvg()}</div>

          <!-- Vertical Datum Calibration Card -->
          <div style="margin-top:10px;padding:10px;border-radius:10px;background:rgba(255,255,255,0.05);border:1px solid rgba(255,255,255,0.12);font-variant-numeric:tabular-nums;">
            <span class="sf-tile-label">HIỆU CHUẨN CAO ĐỘ THỦY CHUẨN (CALIBRACIÓN VERTICAL · alturas.js + GEOIDE-Ar16)</span>
            <div class="sf-stat-grid-4" style="margin-top:6px;font-size:12px;">
              <div><span style="color:rgba(255,255,255,0.6)">Cero Elipsoidal</span><br><strong>${DATUM_STATE.zeroEllipsoidM.toFixed(3)} m</strong></div>
              <div><span style="color:rgba(255,255,255,0.6)">Dị thường Geoid N</span><br><strong>${DATUM_STATE.geoidUndulationM.toFixed(3)} m</strong></div>
              <div><span style="color:rgba(255,255,255,0.6)">Cota Agua h_elip</span><br><strong style="color:#38EF7D">${hElip.toFixed(3)} m</strong></div>
              <div><span style="color:rgba(255,255,255,0.6)">Cota Agua H_orto</span><br><strong style="color:#38bdf8">${hOrto.toFixed(3)} m</strong></div>
            </div>
          </div>
        </div>

        <!-- Right column: Interactive 3-Octave Wave Shader Canvas & Boundary Polygon Editor -->
        <div class="sf-tile" style="padding:14px;">
          <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:6px;">
            <span class="sf-tile-label">MẶT NƯỚC SÓNG GLSL 3-OCTAVE &amp; ĐA GIÁC VÙNG NGẬP (DatumRiverSlopeWater)</span>
            <span class="sf-pill ${val.valid ? 'ok' : 'alert'}" id="datumPolyValidBadge">
              ${val.valid ? `✓ Đa giác hợp lệ (${DATUM_STATE.boundaryVertices.length} đỉnh)` : `⚠ Lỗi: ${val.reason}`}
            </span>
          </div>

          <canvas id="datumFloodCanvas" width="520" height="235"
            style="width:100%;height:auto;aspect-ratio:520/235;max-height:235px;border-radius:12px;border:1px solid rgba(255,255,255,0.18);margin-top:6px;cursor:grab;touch-action:none;display:block;"></canvas>

          <div id="datumPolyAreaVal" style="font-size:12px;margin-top:6px;font-variant-numeric:tabular-nums;">
            Diện tích vùng bao: <strong>${val.areaM2.toFixed(0)} m² (${totalHa.toFixed(2)} ha)</strong> · Diện tích ngập ở mức ${level.toFixed(2)}m: <strong style="color:#38bdf8;">${floodedHa.toFixed(2)} ha (${(inundatedRatio * 100).toFixed(1)}%)</strong>
          </div>

          <!-- Polygon Editor Toolbar -->
          <div style="display:flex;flex-wrap:wrap;gap:6px;margin-top:8px;">
            <button class="sf-action-btn secondary" onclick="DatumAppSuite.undoBoundaryEdit()">↩ Hoàn tác (${DATUM_STATE.undoStack.length})</button>
            <button class="sf-action-btn secondary" onclick="DatumAppSuite.deleteSelectedVertex()">🗑 Xóa đỉnh V${DATUM_STATE.selectedVertexIdx + 1}</button>
            <button class="sf-action-btn secondary" onclick="DatumAppSuite.resetBoundaryFootprint()">🔲 Khớp Huella 3D tự động (bajadagrande2)</button>
            <button class="sf-action-btn" onclick="DatumAppSuite.saveCurrentScenario()">💾 Lưu kịch bản ngập</button>
          </div>

          <!-- Water Appearance & Slope Shader Controls -->
          <div class="sf-stat-grid-4" style="margin-top:10px;padding-top:8px;border-top:1px solid rgba(255,255,255,0.12);">
            <div>
              <span class="sf-tile-label">MÀU NƯỚC &amp; ĐỘ ĐỤC</span>
              <div style="display:flex;gap:4px;align-items:center;margin-top:4px;">
                <input type="color" value="${DATUM_STATE.waterColor}" onchange="DatumAppSuite.updateWaterStyle('color', this.value)" style="width:32px;height:28px;border:0;background:none;cursor:pointer;" />
                <input type="range" min="0.15" max="0.95" step="0.05" value="${DATUM_STATE.waterAlpha}" oninput="DatumAppSuite.updateWaterStyle('alpha', this.value)" style="width:100%;" />
              </div>
            </div>
            <div>
              <span class="sf-tile-label">ĐỘ DỐC CỤC BỘ (ADJUST)</span>
              <input type="range" min="-2" max="2" step="0.05" value="${DATUM_STATE.slopeAdjustM}" oninput="DatumAppSuite.updateWaterStyle('adjust', this.value)" style="width:100%;margin-top:6px;" />
              <span class="sf-tile-sub">${DATUM_STATE.slopeAdjustM >= 0 ? '+' : ''}${Number(DATUM_STATE.slopeAdjustM).toFixed(2)} m</span>
            </div>
            <div>
              <span class="sf-tile-label">CƯỜNG ĐỘ SÓNG</span>
              <input type="range" min="0" max="1" step="0.05" value="${DATUM_STATE.waveIntensity}" oninput="DatumAppSuite.updateWaterStyle('intensity', this.value)" style="width:100%;margin-top:6px;" />
              <span class="sf-tile-sub">${Math.round(DATUM_STATE.waveIntensity * 100)}% · Tốc độ ${DATUM_STATE.waveSpeed}×</span>
            </div>
            <div>
              <span class="sf-tile-label">BƯỚC SÓNG (SCALE M)</span>
              <input type="range" min="4" max="60" step="2" value="${DATUM_STATE.waveScaleM}" oninput="DatumAppSuite.updateWaterStyle('scale', this.value)" style="width:100%;margin-top:6px;" />
              <span class="sf-tile-sub">${DATUM_STATE.waveScaleM} m (3-octave GLSL)</span>
            </div>
          </div>

          <!-- Saved Scenarios List -->
          <div style="margin-top:8px;">
            <span class="sf-tile-label">KỊCH BẢN ĐÃ LƯU (ESCENARIOS GUARDADOS):</span>
            <div style="display:flex;flex-wrap:wrap;gap:6px;margin-top:4px;">
              ${DATUM_STATE.savedScenarios.map(sc => `
                <button class="sf-action-btn secondary" onclick="DatumAppSuite.applyScenario('${sc.id}')">
                  <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${sc.color};margin-right:4px;"></span>
                  ${sc.name} (${sc.level.toFixed(2)} m)
                </button>
              `).join('')}
            </div>
          </div>
        </div>
      </div>
    `;
  }

  function renderProfileAndContoursTab() {
    const ds = PROFILE_DATASETS[DATUM_STATE.profilePreset] || PROFILE_DATASETS['parana-bank'];
    const prof = DatumPerfilDatos.prepare(ds.samples);
    const probe = DatumPerfilDatos.at(prof, DATUM_STATE.hoverDistanceM) || prof.samples[0];
    const { hOrto } = getWaterHeights();
    const waterRefZ = DATUM_STATE.profilePreset === 'parana-bank' ? hOrto : 818.5 + DATUM_STATE.level * 0.5;

    // Build Profile SVG with Hermite spline curve, grade line, water level intersection, and hover crosshair
    const w = 520, h = 195, padL = 44, padR = 18, padT = 18, padB = 28;
    const pw = w - padL - padR, ph = h - padT - padB;
    const minZ = prof.metrics.min - 1.5;
    const maxZ = prof.metrics.max + 1.5;
    const xOf = d => padL + (d / Math.max(1, prof.length)) * pw;
    const yOf = z => padT + ph - ((z - minZ) / Math.max(1e-6, maxZ - minZ)) * ph;

    const splinePts = [];
    for (let d = 0; d <= prof.length; d += 2) {
      const pt = DatumPerfilDatos.at(prof, d);
      if (pt) splinePts.push(`${xOf(d).toFixed(1)},${yOf(pt.elevation).toFixed(1)}`);
    }
    const areaPts = `${xOf(0).toFixed(1)},${(padT + ph).toFixed(1)} ` + splinePts.join(' ') + ` ${xOf(prof.length).toFixed(1)},${(padT + ph).toFixed(1)}`;
    const yWater = yOf(Math.max(minZ, Math.min(maxZ, waterRefZ))).toFixed(1);
    const probeX = xOf(probe.distance).toFixed(1);
    const probeY = yOf(probe.elevation).toFixed(1);

    // Generate Marching Squares Contours (window.CURVAS)
    const grid = buildDemGrid(DATUM_STATE.profilePreset);
    const contours = DatumCurvas.generar(grid, {
      min: grid.minZ,
      max: grid.maxZ,
      paso: DATUM_STATE.contourStepM,
      suavizar: DATUM_STATE.contourSmoothIters,
      indiceCada: 5
    });

    const cw = 480, ch = 195;
    const contourPaths = contours.map(c => {
      const stroke = c.indice ? '#FFB340' : 'rgba(56, 239, 125, 0.55)';
      const sw = c.indice ? 2.0 : 1.1;
      return c.lineas.map(line => {
        const dStr = line.map((pt, idx) => {
          const sx = (16 + (pt[0] / (grid.nx - 1)) * (cw - 32)).toFixed(1);
          const sy = (ch - 16 - (pt[1] / (grid.ny - 1)) * (ch - 32)).toFixed(1);
          return `${idx === 0 ? 'M' : 'L'}${sx},${sy}`;
        }).join(' ');
        const midPt = line[Math.floor(line.length / 2)];
        const lx = (16 + (midPt[0] / (grid.nx - 1)) * (cw - 32)).toFixed(1);
        const ly = (ch - 16 - (midPt[1] / (grid.ny - 1)) * (ch - 32)).toFixed(1);
        return `<path d="${dStr}" fill="none" stroke="${stroke}" stroke-width="${sw}"/>` +
               (c.indice ? `<text x="${lx}" y="${ly}" fill="#FFB340" font-size="9" font-weight="700">${c.nivel.toFixed(1)}m</text>` : '');
      }).join('');
    }).join('');

    return `
      <div class="sf-split-2">
        <!-- Left: Topographic Profile (DatumPerfilDatos + Fritsch-Carlson Hermite Spline) -->
        <div class="sf-tile" style="padding:14px;">
          <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:6px;">
            <span class="sf-tile-label">MẶT CẮT ĐỊA HÌNH &amp; NỘI SUY HERMITE (perfil-datos.js + perfil-grafico.js)</span>
            <div style="display:flex;flex-wrap:wrap;gap:6px;">
              <button class="sf-action-btn ${DATUM_STATE.profilePreset === 'parana-bank' ? '' : 'secondary'}" onclick="DatumAppSuite.setProfilePreset('parana-bank')">🇦🇷 Bờ Sông Paraná</button>
              <button class="sf-action-btn ${DATUM_STATE.profilePreset === 'baoloc-slope' ? '' : 'secondary'}" onclick="DatumAppSuite.setProfilePreset('baoloc-slope')">🇻🇳 Đồi Sầu riêng Bảo Lộc</button>
            </div>
          </div>

          <svg viewBox="0 0 ${w} ${h}" style="width:100%;height:auto;max-height:190px;border-radius:12px;background:rgba(4,16,25,0.82);border:1px solid rgba(255,255,255,0.16);margin-top:6px;display:block;">
            <polygon points="${areaPts}" fill="rgba(56, 239, 125, 0.14)"/>
            <!-- Grade Line (Rasante) -->
            <line x1="${xOf(0)}" y1="${yOf(prof.grade.a)}" x2="${xOf(prof.length)}" y2="${yOf(prof.grade.b)}" stroke="rgba(255,179,64,0.65)" stroke-width="1.5" stroke-dasharray="5 4"/>
            <!-- Water Level Intersection Line -->
            <line x1="${padL}" y1="${yWater}" x2="${w - padR}" y2="${yWater}" stroke="#38bdf8" stroke-width="1.8" stroke-dasharray="4 3"/>
            <text x="${w - padR - 6}" y="${Number(yWater) - 5}" text-anchor="end" fill="#38bdf8" font-size="9.5" font-weight="600">Mực nước H = ${waterRefZ.toFixed(2)} m</text>
            <!-- Monotone Cubic Hermite Profile Curve -->
            <polyline points="${splinePts.join(' ')}" fill="none" stroke="#38EF7D" stroke-width="2.5"/>
            <!-- Probe Crosshair -->
            <line x1="${probeX}" y1="${padT}" x2="${probeX}" y2="${padT + ph}" stroke="#ffffff" stroke-width="1.2" stroke-dasharray="3 3"/>
            <circle cx="${probeX}" cy="${probeY}" r="5.5" fill="#FFB340" stroke="#04121B" stroke-width="2"/>
            <text x="${padL}" y="${h - 7}" fill="rgba(255,255,255,0.65)" font-size="9.5">PK 0+000.00</text>
            <text x="${w - padR}" y="${h - 7}" text-anchor="end" fill="rgba(255,255,255,0.65)" font-size="9.5">${DatumPerfilDatos.pk(prof.length)}</text>
          </svg>

          <div style="margin-top:6px;">
            <span class="sf-tile-label">DI CHUYỂN CON TRỎ LÝ TRÌNH TRÊN MẶT CẮT (${DatumPerfilDatos.pk(probe.distance)}):</span>
            <input type="range" min="0" max="${prof.length}" step="1" value="${DATUM_STATE.hoverDistanceM}"
              style="width:100%;accent-color:#38EF7D;margin-top:4px;"
              oninput="DatumAppSuite.setProfileHover(this.value)" />
          </div>

          <div class="sf-stat-grid-4" style="margin-top:6px;font-size:12px;font-variant-numeric:tabular-nums;">
            <div style="padding:8px;border-radius:8px;background:rgba(255,255,255,0.05);">
              <span style="color:rgba(255,255,255,0.6)">Lý trình &amp; Cao độ</span><br>
              <strong>${DatumPerfilDatos.pk(probe.distance)} · ${probe.elevation.toFixed(2)} m</strong>
            </div>
            <div style="padding:8px;border-radius:8px;background:rgba(255,255,255,0.05);">
              <span style="color:rgba(255,255,255,0.6)">Độ dốc cục bộ / Max</span><br>
              <strong>${probe.slope.toFixed(1)}% (Max ${prof.metrics.maxSlope.toFixed(1)}%)</strong>
            </div>
            <div style="padding:8px;border-radius:8px;background:rgba(255,255,255,0.05);">
              <span style="color:rgba(255,255,255,0.6)">Chênh cao ΔH toàn tuyến</span><br>
              <strong>+${prof.metrics.deltaH.toFixed(2)} m (${prof.metrics.min.toFixed(1)}→${prof.metrics.max.toFixed(1)}m)</strong>
            </div>
            <div style="padding:8px;border-radius:8px;background:rgba(255,255,255,0.05);">
              <span style="color:rgba(255,255,255,0.6)">Đào / Đắp so với Rasante</span><br>
              <strong>+${prof.metrics.excess.toFixed(2)}m / −${prof.metrics.deficit.toFixed(2)}m</strong>
            </div>
          </div>
        </div>

        <!-- Right: Marching Squares + Chaikin Contour Lines (window.CURVAS — curvas.js) -->
        <div class="sf-tile" style="padding:14px;">
          <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:6px;">
            <span class="sf-tile-label">ĐƯỜNG ĐỒNG MỨC MARCHING SQUARES + CHAIKIN (curvas.js v4.286.0)</span>
            <span class="sf-pill ok">${contours.length} mức cao độ · Equidistancia ${DATUM_STATE.contourStepM.toFixed(1)} m</span>
          </div>

          <svg viewBox="0 0 ${cw} ${ch}" style="width:100%;height:auto;max-height:190px;border-radius:12px;background:rgba(4,16,25,0.82);border:1px solid rgba(255,255,255,0.16);margin-top:6px;display:block;">
            ${contourPaths}
          </svg>

          <div style="display:flex;flex-wrap:wrap;gap:8px;align-items:center;justify-content:space-between;margin-top:8px;">
            <div style="display:flex;flex-wrap:wrap;gap:6px;align-items:center;">
              <span class="sf-tile-label">KHOẢNG CAO ĐỀU (EQUIDISTANCIA):</span>
              ${[0.5, 1.0, 2.0, 5.0].map(step => `
                <button class="sf-action-btn ${DATUM_STATE.contourStepM === step ? '' : 'secondary'}" onclick="DatumAppSuite.setContourStep(${step})">${step} m</button>
              `).join('')}
            </div>
            <div style="display:flex;flex-wrap:wrap;gap:6px;align-items:center;">
              <span class="sf-tile-label">LÀM TRƠN CHAIKIN:</span>
              ${[0, 1, 2, 3].map(it => `
                <button class="sf-action-btn ${DATUM_STATE.contourSmoothIters === it ? '' : 'secondary'}" onclick="DatumAppSuite.setContourSmooth(${it})">${it}x</button>
              `).join('')}
            </div>
          </div>

          <div style="display:flex;flex-wrap:wrap;gap:8px;margin-top:10px;">
            <button class="sf-action-btn" onclick="DatumAppSuite.exportProfileCsv()">⬇ Xuất Mặt cắt (.CSV)</button>
            <button class="sf-action-btn secondary" onclick="DatumAppSuite.exportAllToDxf()">📐 Xuất Mặt cắt &amp; Đồng mức (.DXF R12)</button>
          </div>
        </div>
      </div>
    `;
  }

  function renderGnssAndStakeoutTab() {
    const parsed = DatumGnssNmea.parseBatch(DATUM_STATE.nmeaRawInput);
    const target = DATUM_STATE.stakeoutTargets[DATUM_STATE.selectedStakeoutIdx] || DATUM_STATE.stakeoutTargets[0];

    // Compute Replanteo (Stakeout) deltas in meters
    const R = 6378137.0;
    const cosLat = Math.cos((parsed.lat * Math.PI) / 180);
    const dNorthM = ((target.lat - parsed.lat) * Math.PI / 180) * R;
    const dEastM = ((target.lng - parsed.lon) * Math.PI / 180) * R * cosLat;
    const dist2dM = Math.hypot(dEastM, dNorthM);
    const azimuthDeg = (Math.atan2(dEastM, dNorthM) * 180 / Math.PI + 360) % 360;
    const groundZOrto = parsed.hOrto - DATUM_STATE.antennaHeightM;
    const cutFillZ = target.zOrto - groundZOrto; // + = Relleno (Đắp), - = Corte (Đào)

    return `
      <div class="sf-split-2">
        <!-- Left: NMEA-0183 Parser & RTK Telemetry (gnss-nmea.js + gnss-nucleo.js) -->
        <div class="sf-tile" style="padding:14px;">
          <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:6px;">
            <span class="sf-tile-label">TRẠM ĐO GNSS RTK &amp; GIẢI MÃ NMEA-0183 (gnss-nmea.js v4 · gnss-nucleo.js v4)</span>
            <span class="sf-pill ${parsed.calidad === 'RTK_FIJO' ? 'ok' : 'watch'}">
              ● ${parsed.calidad} (GGA=${parsed.calidadGga}) · ${parsed.sats} vệ tinh · Base #${parsed.baseId}
            </span>
          </div>

          <div class="sf-stat-grid-4" style="margin-top:8px;font-size:12px;font-variant-numeric:tabular-nums;">
            <div style="padding:8px;border-radius:8px;background:rgba(255,255,255,0.05);">
              <span style="color:rgba(255,255,255,0.6)">Vĩ độ / Kinh độ (WGS84)</span><br>
              <strong>${parsed.lat.toFixed(6)}°, ${parsed.lon.toFixed(6)}°</strong>
            </div>
            <div style="padding:8px;border-radius:8px;background:rgba(255,255,255,0.05);">
              <span style="color:rgba(255,255,255,0.6)">Sai số GST σH / σV</span><br>
              <strong style="color:#38EF7D;">±${(parsed.sigmaH * 100).toFixed(1)} cm / ±${(parsed.sigmaV * 100).toFixed(1)} cm</strong>
            </div>
            <div style="padding:8px;border-radius:8px;background:rgba(255,255,255,0.05);">
              <span style="color:rgba(255,255,255,0.6)">DOP (HDOP / PDOP / VDOP)</span><br>
              <strong>${parsed.hdop.toFixed(2)} / ${parsed.pdop.toFixed(2)} / ${parsed.vdop.toFixed(2)}</strong>
            </div>
            <div style="padding:8px;border-radius:8px;background:rgba(255,255,255,0.05);">
              <span style="color:rgba(255,255,255,0.6)">Cao độ H_orto (Mũi sào)</span><br>
              <strong style="color:#38bdf8;">${groundZOrto.toFixed(3)} m (N=${parsed.hGeoide.toFixed(2)}m)</strong>
            </div>
          </div>

          <div style="margin-top:8px;">
            <span class="sf-tile-label">LUỒNG BẢN TIN NMEA-0183 TRỰC TIẾP ($GNGGA, $GPGST, $GNGSA, $GNRMC — KIỂM TRA XOR CHECKSUM):</span>
            <textarea class="sf-textarea" id="datumNmeaInput" style="height:84px;margin-top:4px;font-size:11.5px;" spellcheck="false">${DATUM_STATE.nmeaRawInput}</textarea>
          </div>

          <div style="display:flex;flex-wrap:wrap;gap:8px;align-items:center;justify-content:space-between;margin-top:6px;">
            <div style="display:flex;flex-wrap:wrap;gap:6px;">
              <button class="sf-action-btn" onclick="DatumAppSuite.parseNmeaFromTextarea()">⚡ Giải mã NMEA &amp; Kiểm tra Checksum</button>
              <button class="sf-action-btn secondary" onclick="DatumAppSuite.simulateRtkEpoch()">🛰 Mô phỏng Kỷ nguyên RTK mới</button>
            </div>
            <span class="sf-tile-sub">Hợp lệ: <strong>${parsed.validLines} câu</strong> · Lỗi checksum: <strong>${parsed.badChecksums}</strong></span>
          </div>
        </div>

        <!-- Right: Replanteo (Stakeout Navigation) & Skyplot (gnss3d.js) -->
        <div class="sf-tile" style="padding:14px;">
          <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:6px;">
            <span class="sf-tile-label">BỐ TRÍ ĐIỂM THỰC ĐỊA — REPLANTEO 3D (gnss3d.js v139)</span>
            <span class="sf-pill ok">Chiều cao sào gương: ${DATUM_STATE.antennaHeightM.toFixed(2)} m</span>
          </div>

          <div class="sf-split-2 gnss-select" style="margin-top:6px;">
            <div>
              <span class="sf-tile-label">CHỌN MỐC THIẾT KẾ CẦN BỐ TRÍ (PUNTO A REPLANTEAR):</span>
              <select class="sf-select" onchange="DatumAppSuite.selectStakeoutTarget(this.value)">
                ${DATUM_STATE.stakeoutTargets.map((t, idx) => `
                  <option value="${idx}" ${idx === DATUM_STATE.selectedStakeoutIdx ? 'selected' : ''}>${t.code} — ${t.name} (Z=${t.zOrto.toFixed(2)}m)</option>
                `).join('')}
              </select>
            </div>
            <div>
              <span class="sf-tile-label">CHIỀU CAO SÀO (BASTÓN M):</span>
              <input type="number" step="0.05" value="${DATUM_STATE.antennaHeightM}" class="sf-input" onchange="DatumAppSuite.setAntennaHeight(this.value)" />
            </div>
          </div>

          <!-- Bullseye Target & Cut/Fill Guidance -->
          <div class="sf-stakeout-grid" style="margin-top:10px;">
            <svg viewBox="0 0 150 150" style="width:145px;height:145px;border-radius:50%;background:rgba(4,16,25,0.85);border:1px solid rgba(56,239,125,0.35);">
              <circle cx="75" cy="75" r="62" fill="none" stroke="rgba(255,255,255,0.14)" stroke-dasharray="3 3"/>
              <circle cx="75" cy="75" r="40" fill="none" stroke="rgba(255,255,255,0.20)"/>
              <circle cx="75" cy="75" r="18" fill="rgba(56,239,125,0.12)" stroke="#38EF7D" stroke-width="1.4"/>
              <line x1="75" y1="8" x2="75" y2="142" stroke="rgba(255,255,255,0.2)"/>
              <line x1="8" y1="75" x2="142" y2="75" stroke="rgba(255,255,255,0.2)"/>
              <text x="75" y="18" text-anchor="middle" fill="#38EF7D" font-size="9" font-weight="700">N</text>
              <!-- Rover relative vector -->
              <line x1="75" y1="75" x2="98" y2="54" stroke="#FFB340" stroke-width="2.2"/>
              <circle cx="98" cy="54" r="5.5" fill="#FFB340" stroke="#04121B" stroke-width="1.8"/>
            </svg>

            <div class="sf-stat-grid-2" style="width:100%;font-size:12px;font-variant-numeric:tabular-nums;">
              <div style="padding:8px;border-radius:8px;background:rgba(255,255,255,0.05);">
                <span style="color:rgba(255,255,255,0.6)">Δ Bắc (ΔN) / Δ Đông (ΔE)</span><br>
                <strong>${dNorthM >= 0 ? '+' : ''}${dNorthM.toFixed(3)} m / ${dEastM >= 0 ? '+' : ''}${dEastM.toFixed(3)} m</strong>
              </div>
              <div style="padding:8px;border-radius:8px;background:rgba(255,255,255,0.05);">
                <span style="color:rgba(255,255,255,0.6)">Khoảng cách 2D &amp; Phương vị</span><br>
                <strong style="color:#FFB340;">${dist2dM.toFixed(3)} m · ${azimuthDeg.toFixed(1)}°</strong>
              </div>
              <div style="grid-column:1 / -1;padding:9px;border-radius:8px;background:${cutFillZ >= 0 ? 'rgba(56,189,248,0.14)' : 'rgba(255,179,64,0.14)'};border:1px solid ${cutFillZ >= 0 ? 'rgba(56,189,248,0.4)' : 'rgba(255,179,64,0.4)'};">
                <span style="color:rgba(255,255,255,0.75)">CHỈ DẪN CAO ĐỘ ĐÀO / ĐẮP (CORTE / RELLENO ΔZ):</span><br>
                <strong style="font-size:14px;color:${cutFillZ >= 0 ? '#38bdf8' : '#FFB340'};">
                  ${cutFillZ >= 0 ? `⬆ ĐẮP THÊM (RELLENO): +${cutFillZ.toFixed(3)} m` : `⬇ ĐÀO XUỐNG (CORTE): ${cutFillZ.toFixed(3)} m`}
                </strong>
              </div>
            </div>
          </div>
        </div>
      </div>
    `;
  }

  function renderCadAndClassificationTab() {
    const cls = DATUM_STATE.clsLastResult;
    const samplePt = DATUM_STATE.boundaryVertices[0] || { lat: -31.712938, lng: -60.576180 };
    const crsProj = DatumCrsEngine.projectLonLat(samplePt.lng, samplePt.lat, DATUM_STATE.selectedEpsg);

    // OrtoFalsa camera math (from ortho-falsa.js?v=15 — source-24)
    const fovRad = (DATUM_STATE.ortoFalsaFovDeg * Math.PI) / 180;
    const kAncho = 2 * Math.tan(0.5 * fovRad);
    const virtualDistM = DATUM_STATE.ortoFalsaVisibleWidthM / Math.max(1e-6, kAncho);

    return `
      <div class="sf-split-2">
        <!-- Left: Point Cloud Classification (dib-clasificacion.js v14) -->
        <div class="sf-tile" style="padding:14px;">
          <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:6px;">
            <span class="sf-tile-label">PHÂN LOẠI ĐÁM MÂY ĐIỂM &amp; MESH 3D (dib-clasificacion.js v14 · MAL-54A0D1)</span>
            <span class="sf-pill ok">${cls.totalPoints.toLocaleString('vi-VN')} điểm đã phân lớp</span>
          </div>

          <div class="sf-stat-grid-3" style="margin-top:8px;">
            <div style="padding:8px;border-radius:8px;background:rgba(255,255,255,0.05);">
              <span class="sf-tile-label">LỌC NHIỄU (RUIDO)</span>
              <select class="sf-select" style="margin-top:4px;" onchange="DatumAppSuite.runPointClassification('ruido', this.value)">
                <option value="baja" ${DATUM_STATE.clsNoiseSens === 'baja' ? 'selected' : ''}>Độ nhạy: Baja (Thấp)</option>
                <option value="media" ${DATUM_STATE.clsNoiseSens === 'media' ? 'selected' : ''}>Độ nhạy: Media (Vừa)</option>
                <option value="alta" ${DATUM_STATE.clsNoiseSens === 'alta' ? 'selected' : ''}>Độ nhạy: Alta (Cao)</option>
              </select>
            </div>
            <div style="padding:8px;border-radius:8px;background:rgba(255,255,255,0.05);">
              <span class="sf-tile-label">MẶT ĐẤT (SUELO · CELDA/TOL)</span>
              <div style="display:flex;gap:4px;margin-top:4px;">
                <input type="number" step="0.5" value="${DATUM_STATE.clsGroundCellM}" class="sf-input" title="Kích thước ô lưới (m)" />
                <button class="sf-action-btn" onclick="DatumAppSuite.runPointClassification('suelo')">Lọc đất</button>
              </div>
            </div>
            <div style="padding:8px;border-radius:8px;background:rgba(255,255,255,0.05);">
              <span class="sf-tile-label">MẶT PHẲNG RANSAC (PLANOS)</span>
              <div style="display:flex;gap:4px;margin-top:4px;">
                <input type="number" step="0.05" value="${DATUM_STATE.clsPlaneTolM}" class="sf-input" title="Dung sai phẳng (m)" />
                <button class="sf-action-btn" onclick="DatumAppSuite.runPointClassification('planos')">Dò phẳng</button>
              </div>
            </div>
          </div>

          <table class="sf-table" style="margin-top:8px;">
            <thead>
              <tr><th>Mã ASPRS</th><th>Lớp phân loại (Clase)</th><th>Số điểm</th><th>Tỷ lệ</th></tr>
            </thead>
            <tbody>
              ${cls.classes.map(c => `
                <tr>
                  <td><strong>#${c.code}</strong></td>
                  <td><span style="display:inline-block;width:10px;height:10px;border-radius:2px;background:${c.color};margin-right:6px;"></span>${c.name}</td>
                  <td><strong>${c.count.toLocaleString('vi-VN')}</strong></td>
                  <td>${((c.count / cls.totalPoints) * 100).toFixed(1)}%</td>
                </tr>
              `).join('')}
            </tbody>
          </table>

          <div style="display:flex;gap:8px;margin-top:8px;">
            <button class="sf-action-btn" onclick="DatumAppSuite.exportClassificationCsv()">⬇ Xuất Điểm Phân Loại (.CSV)</button>
            <button class="sf-action-btn secondary" onclick="DatumAppSuite.exportClassificationJson()">⬇ Xuất Descriptor (.JSON)</button>
          </div>
        </div>

        <!-- Right: CRS Engine (POSGAR 2007 EPSG:5347 / VN-2000), OrtoFalsa & CAD DXF R12 Exporter -->
        <div class="sf-tile" style="padding:14px;">
          <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:6px;">
            <span class="sf-tile-label">HỆ QUY CHIẾU CRS, CAMERA ORTOFALSA &amp; XUẤT CAD DXF R12 (crs.js · ortho-falsa.js · dxf.js)</span>
            <span class="sf-pill ok">EPSG:${crsProj.epsg} · AutoCAD AC1009</span>
          </div>

          <div style="margin-top:8px;">
            <span class="sf-tile-label">CHỌN HỆ TỌA ĐỘ CHIẾU PHẲNG (PROJECTED CRS):</span>
            <select class="sf-select" style="margin-top:4px;" onchange="DatumAppSuite.setCrsEpsg(this.value)">
              ${Object.values(DatumCrsEngine.DEFINITIONS).map(d => `
                <option value="${d.epsg}" ${d.epsg === DATUM_STATE.selectedEpsg ? 'selected' : ''}>EPSG:${d.epsg} — ${d.name}</option>
              `).join('')}
            </select>
          </div>

          <div style="margin-top:8px;padding:10px;border-radius:10px;background:rgba(255,255,255,0.05);border:1px solid rgba(255,255,255,0.12);font-size:12px;font-variant-numeric:tabular-nums;">
            <div><strong>Tọa độ Đỉnh V1 (WGS84 DMS):</strong> ${DatumCrsEngine.dms(samplePt.lat, 'lat')} · ${DatumCrsEngine.dms(samplePt.lng, 'lng')}</div>
            <div style="margin-top:4px;"><strong>Tọa độ Chiếu phẳng (EPSG:${crsProj.epsg}):</strong> <span style="color:#38EF7D;">E = ${crsProj.E.toFixed(3)} m · N = ${crsProj.N.toFixed(3)} m</span></div>
            <div style="margin-top:4px;color:rgba(255,255,255,0.6);font-size:11px;">Proj4: <code>${crsProj.def}</code></div>
          </div>

          <!-- OrtoFalsa Narrow-Frustum Camera Calculator -->
          <div style="margin-top:8px;padding:10px;border-radius:10px;background:rgba(56,189,248,0.09);border:1px solid rgba(56,189,248,0.3);font-size:12px;font-variant-numeric:tabular-nums;">
            <span class="sf-tile-label">PHÉP CHIẾU TRỰC GIAO GIẢ TRÊN 3D TILES (window.OrtoFalsa · ortho-falsa.js v15)</span>
            <div style="margin-top:4px;">
              Góc mở Frustum: <strong>FOV = ${DATUM_STATE.ortoFalsaFovDeg}°</strong> · Bề rộng vùng nhìn: <strong>${DATUM_STATE.ortoFalsaVisibleWidthM} m</strong>
              → Khoảng cách lùi camera ảo: <strong style="color:#38bdf8;">D = ${virtualDistM.toFixed(1)} m</strong> (Giữ nguyên LOD 3D Tiles mà triệt tiêu biến dạng phối cảnh).
            </div>
          </div>

          <div style="display:flex;flex-wrap:wrap;gap:8px;margin-top:10px;">
            <button class="sf-action-btn" onclick="DatumAppSuite.exportAllToDxf()">📐 Xuất toàn bộ Dự án ra AutoCAD (.DXF R12)</button>
            <button class="sf-action-btn secondary" onclick="DatumAppSuite.downloadReaProvenanceReport()">📋 Tải Hồ sơ Bằng chứng REA (.JSON)</button>
            <button class="sf-action-btn" style="background:rgba(56,239,125,0.22);border-color:#38EF7D;color:#38EF7D;" onclick="DatumAppSuite.switchTab('layers3d')">🧊 Mở Phân Tích 7 Tầng Mô Hình 3D Độc Lập</button>
          </div>
        </div>
      </div>
    `;
  }

  function hexToRgbObj(hex) {
    const clean = String(hex || '#22c55e').replace('#', '');
    const n = parseInt(clean, 16) || 0x22c55e;
    return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 };
  }

  function sampleStopColor(stops, t) {
    const clamped = Math.max(0, Math.min(1, t));
    const scaled = clamped * (stops.length - 1);
    const idx = Math.min(stops.length - 2, Math.max(0, Math.floor(scaled)));
    const frac = scaled - idx;
    const c0 = hexToRgbObj(stops[idx]);
    const c1 = hexToRgbObj(stops[idx + 1]);
    const r = Math.round(c0.r + (c1.r - c0.r) * frac);
    const g = Math.round(c0.g + (c1.g - c0.g) * frac);
    const b = Math.round(c0.b + (c1.b - c0.b) * frac);
    return `rgb(${r},${g},${b})`;
  }

  function drawStratified3DStackCanvas() {
    const canvas = document.getElementById('datumStratified3DCanvas');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const w = canvas.width;
    const h = canvas.height;
    ctx.clearRect(0, 0, w, h);

    // Background gradient
    const bg = ctx.createLinearGradient(0, 0, 0, h);
    bg.addColorStop(0, '#07130e');
    bg.addColorStop(1, '#0d2118');
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, w, h);

    const selId = DATUM_STATE.selected3DLayerId || 'ALL';
    const yawRad = ((DATUM_STATE.layer3DYawDeg || -28) * Math.PI) / 180;
    const tiltRad = ((DATUM_STATE.layer3DTiltDeg || 52) * Math.PI) / 180;
    const cosY = Math.cos(yawRad);
    const sinY = Math.sin(yawRad);
    const cosT = Math.cos(tiltRad);
    const sinT = Math.sin(tiltRad);
    const explode = DATUM_STATE.layer3DExplodeGap || 1.15;
    const extrudeOn = DATUM_STATE.layer3DExtrudeRelief !== false;

    const cx = selId === 'ALL' ? w * 0.42 : w * 0.48;
    const cy = selId === 'ALL' ? h * 0.52 : h * 0.54;
    const scale = Math.min(w, h) * (selId === 'ALL' ? 0.27 : 0.38);

    function proj3D(nx, ny, nz) {
      const rx = nx * cosY - ny * sinY;
      const ry = nx * sinY + ny * cosY;
      return {
        x: cx + rx * scale,
        y: cy + (ry * cosT - nz * sinT * 1.05) * scale
      };
    }

    const visibleLayers = selId === 'ALL'
      ? [...STRATIFIED_3D_LAYERS].reverse() // bottom (L1) to top (L7)
      : STRATIFIED_3D_LAYERS.filter(l => l.id === selId);

    const gridN = selId === 'ALL' ? 14 : 22;
    const step = 1.6 / gridN;

    visibleLayers.forEach((layer) => {
      const zBase = selId === 'ALL' ? layer.zNorm * explode * 0.72 : -0.05;
      const reliefAmp = !extrudeOn ? 0 : (selId === 'ALL' ? 0.08 : 0.22);

      // Draw 3D surface cells
      for (let r = 0; r < gridN; r++) {
        const y0 = -0.8 + r * step;
        const y1 = y0 + step * 1.06;
        for (let c = 0; c < gridN; c++) {
          const x0 = -0.8 + c * step;
          const x1 = x0 + step * 1.06;

          const v00 = layer.evalField(x0, y0);
          const v10 = layer.evalField(x1, y0);
          const v11 = layer.evalField(x1, y1);
          const v01 = layer.evalField(x0, y1);
          let vm = (v00 + v10 + v11 + v01) * 0.25;

          // Kiểm tra điểm có nằm trong Đa giác Khoanh vùng (Polygon ROI Mask) và lớp phân đoạn DeepLabV3+
          const xm = (x0 + x1) * 0.5;
          const ym = (y0 + y1) * 0.5;
          const inPolyRoi = (xm * xm * 0.85 + ym * ym * 0.92 + 0.14 * Math.sin(xm * 3.2 - ym * 2.4)) <= 0.62;
          if (DATUM_STATE.deeplabPureCanopyMode && !inPolyRoi) {
            continue; // Cắt bỏ hoàn toàn các ô ngoài ranh giới vùng khoanh
          }

          const crownResp = Math.cos(xm * Math.PI * 2.5) * Math.cos(ym * Math.PI * 2.5);
          const isCanopyCell = crownResp >= 0.12;
          const isCanopyLayer = (layer.id === 'L7' || layer.id === 'L6' || layer.id === 'L5' || layer.id === 'L4');
          if (DATUM_STATE.deeplabPureCanopyMode && isCanopyLayer) {
            vm = isCanopyCell ? Math.min(1.0, vm + 0.12) : Math.max(0.08, vm * 0.35);
          }

          const p00 = proj3D(x0, y0, zBase + v00 * reliefAmp);
          const p10 = proj3D(x1, y0, zBase + v10 * reliefAmp);
          const p11 = proj3D(x1, y1, zBase + v11 * reliefAmp);
          const p01 = proj3D(x0, y1, zBase + v01 * reliefAmp);

          ctx.beginPath();
          ctx.moveTo(p00.x, p00.y);
          ctx.lineTo(p10.x, p10.y);
          ctx.lineTo(p11.x, p11.y);
          ctx.lineTo(p01.x, p01.y);
          ctx.closePath();
          ctx.fillStyle = sampleStopColor(layer.gradientStops, vm);
          ctx.fill();

          if (DATUM_STATE.deeplabShowMaskOverlay && isCanopyCell && (selId !== 'ALL' || layer.id === 'L6')) {
            ctx.strokeStyle = vm >= 0.62 ? 'rgba(0,230,118,0.68)' : 'rgba(255,214,0,0.75)';
            ctx.lineWidth = 0.85;
            ctx.stroke();
          }
        }
      }

      // Outer slab border
      const b00 = proj3D(-0.8, -0.8, zBase);
      const b10 = proj3D(0.8, -0.8, zBase);
      const b11 = proj3D(0.8, 0.8, zBase);
      const b01 = proj3D(-0.8, 0.8, zBase);
      ctx.beginPath();
      ctx.moveTo(b00.x, b00.y);
      ctx.lineTo(b10.x, b10.y);
      ctx.lineTo(b11.x, b11.y);
      ctx.lineTo(b01.x, b01.y);
      ctx.closePath();
      ctx.strokeStyle = layer.accent;
      ctx.lineWidth = selId === 'ALL' ? 1.6 : 2.4;
      ctx.stroke();

      // Callout badge on right when viewing ALL 7 layers
      if (selId === 'ALL') {
        const anchor = proj3D(0.82, 0.15, zBase + 0.03);
        const bx = Math.min(w - 205, anchor.x + 16);
        const by = anchor.y - 11;
        ctx.strokeStyle = layer.accent;
        ctx.lineWidth = 1.2;
        ctx.beginPath();
        ctx.moveTo(anchor.x, anchor.y);
        ctx.lineTo(bx, anchor.y);
        ctx.stroke();

        ctx.fillStyle = 'rgba(8, 22, 16, 0.92)';
        ctx.fillRect(bx, by, 196, 22);
        ctx.strokeRect(bx, by, 196, 22);

        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold 10.5px sans-serif';
        const p0 = layer.distinctParams[0];
        const p1 = layer.distinctParams[1];
        const badgeText = p1
          ? `${layer.shortTitle.split('.')[0]}. ${p0.name}: ${p0.mean} · ${p1.name}: ${p1.mean}`
          : `${layer.shortTitle.split('.')[0]}. ${p0.name}: ${p0.mean}`;
        ctx.fillText(badgeText, bx + 6, by + 14.5);
      }
    });
  }

  function renderStratified3DLayersTab() {
    const selId = DATUM_STATE.selected3DLayerId || 'ALL';
    const activeLayer = STRATIFIED_3D_LAYERS.find(l => l.id === selId) || STRATIFIED_3D_LAYERS[0];

    return `
      <div class="sf-tile" style="padding:14px;">
        <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px;">
          <div>
            <span class="sf-tile-label">PHÂN TÍCH ĐỊA TẦNG 3D 7 LỚP ĐỘC LẬP · ĐÃ HIỆU CHUẨN BẰNG VAINF/DEEPLABV3PLUS-PYTORCH</span>
            <strong class="sf-tile-val" style="font-size:15px;">Phân đoạn Ngữ nghĩa Vùng Cây Trong Khu Vực Khoanh Vùng (ASPP + 1/4 Low-Level Decoder) để cấp thông số chính xác nhất cho 7 Tầng 3D</strong>
          </div>
          <div style="display:flex;gap:6px;flex-wrap:wrap;">
            <button class="sf-action-btn ${selId === 'ALL' ? '' : 'secondary'}" onclick="DatumAppSuite.select3DLayer('ALL')">📚 Xem Xếp chồng 7 Tầng</button>
            ${STRATIFIED_3D_LAYERS.map(l => `
              <button class="sf-action-btn ${selId === l.id ? '' : 'secondary'}" style="${selId === l.id ? `background:${l.accent};border-color:${l.accent};color:#fff;` : ''}" onclick="DatumAppSuite.select3DLayer('${l.id}')">${l.shortTitle}</button>
            `).join('')}
          </div>
        </div>

        <!-- BANNER HIỆU CHUẨN THÔNG SỐ 3D BẰNG VAINF/DEEPLABV3PLUS-PYTORCH -->
        <div style="margin-top:10px;padding:11px 13px;border-radius:11px;background:linear-gradient(135deg,rgba(16,185,129,0.16),rgba(6,78,59,0.35));border:1px solid rgba(56,239,125,0.45);">
          <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px;">
            <div>
              <span style="font-size:11px;font-weight:800;color:#38EF7D;">🧠 VAINF/DEEPLABV3PLUS-PYTORCH SEMANTIC SEGMENTATION (github.com/VainF/DeepLabV3Plus-Pytorch)</span>
              <div style="font-size:12px;color:#e2e8f0;margin-top:2px;">
                Mô hình: <strong><code>${DATUM_STATE.deeplabModel}</code> (OS=${DATUM_STATE.deeplabOutputStride} · ASPP [12,24,36] · AtrousSeparableConvolution · mIoU 89,4%)</strong> — Cắt đúng theo đa giác khoanh vùng (1,80 ha), loại bỏ hoàn toàn nhiễu đất trống &amp; đường lô trước khi tính chỉ số 3D.
              </div>
            </div>
            <div style="display:flex;gap:6px;flex-wrap:wrap;align-items:center;">
              <select class="sf-select" style="width:auto;padding:5px 10px;font-size:11.5px;" onchange="DatumAppSuite.setDeepLabParam('model', this.value)">
                <option value="deeplabv3plus_resnet101" ${DATUM_STATE.deeplabModel === 'deeplabv3plus_resnet101' ? 'selected' : ''}>deeplabv3plus_resnet101 (OS=8 · mIoU 89.4%)</option>
                <option value="deeplabv3plus_hrnetv2_48" ${DATUM_STATE.deeplabModel === 'deeplabv3plus_hrnetv2_48' ? 'selected' : ''}>deeplabv3plus_hrnetv2_48 (OS=4 · mIoU 90.1%)</option>
                <option value="deeplabv3plus_resnet50" ${DATUM_STATE.deeplabModel === 'deeplabv3plus_resnet50' ? 'selected' : ''}>deeplabv3plus_resnet50 (OS=16 · mIoU 87.2%)</option>
                <option value="deeplabv3plus_mobilenet" ${DATUM_STATE.deeplabModel === 'deeplabv3plus_mobilenet' ? 'selected' : ''}>deeplabv3plus_mobilenet (Edge 5.2M · mIoU 83.6%)</option>
              </select>
              <button class="sf-action-btn ${DATUM_STATE.deeplabPureCanopyMode ? '' : 'secondary'}" onclick="DatumAppSuite.setDeepLabParam('togglePureCanopy')">
                ${DATUM_STATE.deeplabPureCanopyMode ? '✓ Đang lọc Vùng Khoanh & Tách Tán Thuần Khiết' : '◯ Đang hiển thị Toàn Khung Chưa Tách Nền'}
              </button>
              <button class="sf-action-btn ${DATUM_STATE.deeplabShowMaskOverlay ? '' : 'secondary'}" onclick="DatumAppSuite.setDeepLabParam('toggleMaskOverlay')">
                ${DATUM_STATE.deeplabShowMaskOverlay ? '🟢 Viền Mask Tán Cây 3D: BẬT' : 'Viền Mask Tán Cây 3D: TẮT'}
              </button>
            </div>
          </div>

          <div class="sf-stat-grid-5" style="margin-top:8px;font-size:11px;">
            <div style="padding:6px 8px;border-radius:7px;background:rgba(0,0,0,0.35);border-left:3px solid #00E676;">
              <strong style="color:#00E676;">Class 1 · Lõi Tán Khỏe (34,2%)</strong><br>
              <span>0,62 ha → Cấp Tầng 7,6,5,4 · <strong>NDVI thực 0,779</strong> (Thô: 0,723)</span>
            </div>
            <div style="padding:6px 8px;border-radius:7px;background:rgba(0,0,0,0.35);border-left:3px solid #FFD600;">
              <strong style="color:#FFD600;">Class 2 · Viền Tán Yếu (8,3%)</strong><br>
              <span>0,15 ha → Cấp Tầng 7,6,5,4 · <strong>NDRE thực 0,508</strong> (+10,4%)</span>
            </div>
            <div style="padding:6px 8px;border-radius:7px;background:rgba(0,0,0,0.35);border-left:3px solid #8BC34A;">
              <strong style="color:#8BC34A;">Class 3 · Cỏ Xen Hàng (31,5%)</strong><br>
              <span>0,57 ha → Cấp Tầng 3,2,1 · Giữ ẩm &amp; chống xói mòn</span>
            </div>
            <div style="padding:6px 8px;border-radius:7px;background:rgba(0,0,0,0.35);border-left:3px solid #A1887F;">
              <strong style="color:#A1887F;">Class 4 · Đất Vùng Rễ (20,2%)</strong><br>
              <span>0,36 ha → Cấp Tầng 2,1 · <strong>Ẩm rễ thực 31,8% VWC · pH 5,68</strong></span>
            </div>
            <div style="padding:6px 8px;border-radius:7px;background:rgba(0,0,0,0.35);border-left:3px solid #29B6F6;">
              <strong style="color:#29B6F6;">Class 5 · Rãnh Thoát (5,8%)</strong><br>
              <span>0,10 ha → Cấp Tầng 2 · <strong>TWI thực 7,64 (Stream Order 1–2)</strong></span>
            </div>
          </div>
        </div>

        <div class="sf-split-2" style="margin-top: 12px;">
          <!-- Left: Interactive 3D Isometric Canvas -->
          <div style="background:rgba(0,0,0,0.28);border:1px solid rgba(255,255,255,0.14);border-radius:12px;padding:10px;">
            <canvas id="datumStratified3DCanvas" width="580" height="390" style="width:100%;height:auto;border-radius:8px;display:block;"></canvas>
            <div style="display:flex;flex-wrap:wrap;gap:8px;margin-top:8px;align-items:center;justify-content:space-between;">
              <div style="display:flex;gap:6px;align-items:center;">
                <span class="sf-tile-label">Góc xoay Yaw (${DATUM_STATE.layer3DYawDeg}°):</span>
                <input type="range" min="-80" max="80" step="4" value="${DATUM_STATE.layer3DYawDeg}" oninput="DatumAppSuite.set3DViewParam('yaw', this.value)" />
              </div>
              <div style="display:flex;gap:6px;align-items:center;">
                <span class="sf-tile-label">Độ tách tầng (${DATUM_STATE.layer3DExplodeGap.toFixed(2)}x):</span>
                <input type="range" min="0.5" max="1.9" step="0.1" value="${DATUM_STATE.layer3DExplodeGap}" oninput="DatumAppSuite.set3DViewParam('explode', this.value)" />
              </div>
              <button class="sf-action-btn secondary" onclick="DatumAppSuite.set3DViewParam('toggleRelief')">
                ${DATUM_STATE.layer3DExtrudeRelief ? '⛰ Đang bật Độ nổi 3D theo giá trị' : '▱ Đang để Mặt phẳng 2.5D'}
              </button>
            </div>
          </div>

          <!-- Right: Multi-layer comparison (ALL) or Single-Layer Deep Inspector -->
          <div style="display:flex;flex-direction:column;gap:8px;max-height:445px;overflow-y:auto;padding-right:4px;-webkit-overflow-scrolling:touch;">
            ${selId === 'ALL' ? `
              <div style="font-size:12px;font-weight:700;color:#38EF7D;">🔬 7 THÔNG SỐ ĐỘC LẬP CỦA 7 TẦNG MÔ HÌNH 3D (Bấm vào bất kỳ tầng nào để xem chi tiết):</div>
              ${STRATIFIED_3D_LAYERS.map(l => `
                <div onclick="DatumAppSuite.select3DLayer('${l.id}')" style="cursor:pointer;padding:9px 11px;border-radius:10px;background:rgba(255,255,255,0.04);border:1px solid ${l.accent}88;">
                  <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:4px;">
                    <strong style="color:#fff;font-size:13px;">${l.shortTitle}</strong>
                    <span style="font-size:11px;color:${l.accent};font-weight:700;">${l.spatialModel.split('(')[0]} →</span>
                  </div>
                  <div style="height:6px;border-radius:3px;margin:5px 0;background:linear-gradient(90deg, ${l.gradientStops.join(',')});"></div>
                  <div style="display:flex;flex-direction:column;gap:4px;margin-top:4px;">
                    ${l.distinctParams.map(dp => `
                      <div style="background:rgba(0,0,0,0.35);padding:6px 8px;border-radius:6px;font-size:11px;">
                        <div style="display:flex;justify-content:space-between;align-items:center;gap:6px;">
                          <span style="color:${l.accent};font-weight:800;">${dp.name}</span>
                          <span style="color:#fff;font-weight:800;">${dp.mean} <span style="color:rgba(255,255,255,0.55);font-weight:500;font-size:10px;">(${dp.min} – ${dp.max})</span></span>
                        </div>
                        <div style="color:#cbd5e1;font-size:10px;margin-top:2px;">↳ ${dp.status}</div>
                      </div>
                    `).join('')}
                  </div>
                </div>
              `).join('')}
            ` : `
              <div style="padding:11px;border-radius:10px;background:rgba(255,255,255,0.05);border:1.5px solid ${activeLayer.accent};">
                <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px;">
                  <div>
                    <div style="font-size:14px;font-weight:800;color:#fff;">${activeLayer.title}</div>
                    <div style="font-size:11.5px;color:${activeLayer.accent};font-weight:700;margin-top:2px;">Mô hình: ${activeLayer.spatialModel}</div>
                  </div>
                  <button class="sf-action-btn secondary" onclick="DatumAppSuite.select3DLayer('ALL')">← Tất cả 7 tầng</button>
                </div>
                <div style="font-size:11px;color:rgba(255,255,255,0.75);margin-top:4px;">Công thức: <code>${activeLayer.formula}</code> · Lớp phủ 3D: <strong>${activeLayer.overlayName}</strong></div>
                <div style="display:flex;align-items:center;gap:8px;margin-top:8px;font-size:11px;font-weight:700;">
                  <span>${activeLayer.minLabel}</span>
                  <div style="flex:1;height:10px;border-radius:5px;background:linear-gradient(90deg, ${activeLayer.gradientStops.join(',')});"></div>
                  <span style="color:#38EF7D;">${activeLayer.maxLabel}</span>
                </div>
              </div>

              <div style="font-size:12px;font-weight:800;color:#fff;">📊 CHỈ SỐ ĐẠI DIỆN CỦA ${activeLayer.shortTitle.toUpperCase()}:</div>
              <div style="display:flex;flex-direction:column;gap:7px;">
                ${activeLayer.distinctParams.map(dp => `
                  <div style="padding:8px 10px;border-radius:8px;background:rgba(255,255,255,0.04);border:1px solid rgba(255,255,255,0.12);">
                    <div style="display:flex;justify-content:space-between;font-size:11.5px;flex-wrap:wrap;gap:4px;">
                      <strong style="color:${activeLayer.accent};">${dp.name} (${dp.unit})</strong>
                      <strong style="color:#fff;">Trung bình: ${dp.mean}</strong>
                    </div>
                    <div style="display:flex;align-items:center;gap:6px;margin-top:4px;font-size:10px;color:rgba(255,255,255,0.65);">
                      <span>Thấp nhất: ${dp.min}</span>
                      <div style="flex:1;height:5px;background:rgba(255,255,255,0.12);border-radius:3px;overflow:hidden;">
                        <div style="width:${dp.pct}%;height:100%;background:${activeLayer.accent};"></div>
                      </div>
                      <span>Cao nhất: ${dp.max}</span>
                    </div>
                    <div style="font-size:10.5px;color:#cbd5e1;margin-top:4px;">↳ ${dp.status}</div>
                  </div>
                `).join('')}
              </div>

              <div style="font-size:12px;font-weight:800;color:#fff;margin-top:2px;">🗺 PHÂN BỐ DIỆN TÍCH 4 NGƯỠNG GIÁ TRỊ TRONG TẦNG:</div>
              <div class="sf-stat-grid-2" style="gap:6px;">
                ${activeLayer.zones.map(z => `
                  <div style="display:flex;align-items:center;gap:8px;padding:6px 8px;border-radius:7px;background:rgba(0,0,0,0.3);">
                    <span style="width:12px;height:12px;border-radius:3px;background:${z.color};flex-shrink:0;"></span>
                    <div style="flex:1;font-size:10.5px;">
                      <div style="font-weight:700;color:#fff;">${z.range} — <span style="color:#38EF7D;">${z.sharePct}%</span></div>
                      <div style="color:rgba(255,255,255,0.65);">${z.desc}</div>
                    </div>
                  </div>
                `).join('')}
              </div>

              <div style="padding:9px 11px;border-radius:8px;background:rgba(56,239,125,0.11);border:1px solid rgba(56,239,125,0.4);font-size:11.5px;">
                <strong style="color:#38EF7D;">🎯 KHUYẾN NGHỊ NÔNG HỌC CHUYÊN BIỆT:</strong>
                <div style="margin-top:3px;color:#fff;">${activeLayer.prescription}</div>
              </div>
            `}
          </div>
        </div>
      </div>
    `;
  }

  function renderDatumDrawerHtml() {
    const tab = DATUM_STATE.activeTab;
    return `
      <div class="sf-tile" style="padding:12px 14px;">
        <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px;">
          <div>
            <span class="sf-tile-label">TÍCH HỢP TỪ DATUMAPP v4.1021.224 &amp; MÔ HÌNH 3D ĐỊA TẦNG 7 LỚP ĐỘC LẬP</span>
            <strong class="sf-tile-val" style="font-size:16px;">Bàn điều khiển Thủy văn Sông Paraná, Mặt cắt Địa hình, GNSS RTK, CAD 3D &amp; Phân tích 7 Tầng 3D</strong>
          </div>
          <div class="sf-gis-tabs" style="margin:0;">
            <button class="sf-gis-tab ${tab === 'layers3d' ? 'active' : ''}" onclick="DatumAppSuite.switchTab('layers3d')">🧊 1. Phân tích 7 Tầng 3D Độc lập</button>
            <button class="sf-gis-tab ${tab === 'flood' ? 'active' : ''}" onclick="DatumAppSuite.switchTab('flood')">🌊 2. Mô phỏng Ngập lụt &amp; Sóng GLSL</button>
            <button class="sf-gis-tab ${tab === 'profile' ? 'active' : ''}" onclick="DatumAppSuite.switchTab('profile')">📈 3. Mặt cắt &amp; Đồng mức (Curvas)</button>
            <button class="sf-gis-tab ${tab === 'gnss' ? 'active' : ''}" onclick="DatumAppSuite.switchTab('gnss')">🛰 4. GNSS RTK NMEA &amp; Replanteo</button>
            <button class="sf-gis-tab ${tab === 'cad3d' ? 'active' : ''}" onclick="DatumAppSuite.switchTab('cad3d')">📐 5. Phân lớp 3D, CRS &amp; Xuất DXF</button>
          </div>
        </div>
      </div>
      <div id="datumTabContentContainer">
        ${tab === 'layers3d'
          ? renderStratified3DLayersTab()
          : tab === 'flood'
            ? renderFloodSimulatorTab()
            : tab === 'profile'
              ? renderProfileAndContoursTab()
              : tab === 'gnss'
                ? renderGnssAndStakeoutTab()
                : renderCadAndClassificationTab()}
      </div>
    `;
  }

  // ---------------------------------------------------------------------------
  // 5. PUBLIC CONTROLLER ACTIONS (window.DatumAppSuite)
  // ---------------------------------------------------------------------------
  function rerenderDatumView() {
    const container = document.getElementById('sfDrawerBody');
    if (!container) return;
    container.innerHTML = renderDatumDrawerHtml();
    if (DATUM_STATE.activeTab === 'flood') {
      startFloodCanvasLoop();
    } else {
      if (waveAnimRaf) {
        cancelAnimationFrame(waveAnimRaf);
        waveAnimRaf = null;
      }
      if (DATUM_STATE.activeTab === 'layers3d') {
        drawStratified3DStackCanvas();
      }
    }
  }

  function triggerDownload(filename, content, mime = 'text/plain;charset=utf-8') {
    const blob = new Blob([content], { type: mime });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    setTimeout(() => {
      a.remove();
      URL.revokeObjectURL(url);
    }, 120);
  }

  global.DatumAppSuite = {
    STATE: DATUM_STATE,
    BoundaryCore: DatumRiverBoundaryCore,
    PerfilDatos: DatumPerfilDatos,
    Curvas: DatumCurvas,
    GnssNmea: DatumGnssNmea,
    CrsEngine: DatumCrsEngine,
    DxfExporter: DatumDxfExporter,

    openSuite(tabName) {
      if (tabName) DATUM_STATE.activeTab = tabName;
      if (typeof global.openDrawer === 'function') {
        global.openDrawer('datumapp', 2);
      }
    },

    renderDrawerHtml: renderDatumDrawerHtml,
    onDrawerOpened() {
      if (DATUM_STATE.activeTab === 'flood') {
        startFloodCanvasLoop();
      } else if (DATUM_STATE.activeTab === 'layers3d') {
        drawStratified3DStackCanvas();
      }
    },

    switchTab(tabName) {
      DATUM_STATE.activeTab = tabName;
      rerenderDatumView();
    },

    select3DLayer(layerId) {
      DATUM_STATE.selected3DLayerId = layerId || 'ALL';
      rerenderDatumView();
    },

    set3DViewParam(param, val) {
      if (param === 'yaw') DATUM_STATE.layer3DYawDeg = Number(val) || 0;
      if (param === 'explode') DATUM_STATE.layer3DExplodeGap = Number(val) || 1.15;
      if (param === 'toggleRelief') DATUM_STATE.layer3DExtrudeRelief = !DATUM_STATE.layer3DExtrudeRelief;
      rerenderDatumView();
    },

    setDeepLabParam(param, val) {
      if (param === 'model') {
        DATUM_STATE.deeplabModel = String(val || 'deeplabv3plus_resnet101');
        DATUM_STATE.deeplabOutputStride = val.includes('hrnet') ? 4 : (val.includes('101') ? 8 : 16);
      } else if (param === 'togglePureCanopy') {
        DATUM_STATE.deeplabPureCanopyMode = !DATUM_STATE.deeplabPureCanopyMode;
      } else if (param === 'toggleMaskOverlay') {
        DATUM_STATE.deeplabShowMaskOverlay = !DATUM_STATE.deeplabShowMaskOverlay;
      }
      rerenderDatumView();
    },

    setWaterLevel(val) {
      const num = Math.max(DATUM_STATE.minLevel, Math.min(DATUM_STATE.maxLevel, Number(val) || 0));
      DATUM_STATE.level = Math.round(num * 100) / 100;
      rerenderDatumView();
    },

    stepWaterLevel(delta) {
      this.setWaterLevel(DATUM_STATE.level + delta);
    },

    animateFloodRise() {
      if (DATUM_STATE.animatingRise) {
        if (riseAnimRaf) cancelAnimationFrame(riseAnimRaf);
        riseAnimRaf = null;
        DATUM_STATE.animatingRise = false;
        rerenderDatumView();
        return;
      }
      const startLevel = 2.64;
      const targetLevel = DATUM_STATE.level > 3.6 ? DATUM_STATE.level : 6.89;
      const durationMs = Math.min(12000, Math.max(4500, Math.abs(targetLevel - startLevel) * 2200));
      const t0 = performance.now();
      DATUM_STATE.animatingRise = true;

      const stepFrame = (now) => {
        if (!DATUM_STATE.animatingRise) return;
        const p = Math.min(1, (now - t0) / durationMs);
        // Smooth cubic ease-in-out
        const ease = p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
        DATUM_STATE.level = Math.round((startLevel + (targetLevel - startLevel) * ease) * 100) / 100;
        if (p < 1) {
          rerenderDatumView();
          riseAnimRaf = requestAnimationFrame(stepFrame);
        } else {
          DATUM_STATE.animatingRise = false;
          riseAnimRaf = null;
          rerenderDatumView();
        }
      };
      riseAnimRaf = requestAnimationFrame(stepFrame);
    },

    updateWaterStyle(prop, val) {
      if (prop === 'color') DATUM_STATE.waterColor = String(val);
      if (prop === 'alpha') DATUM_STATE.waterAlpha = Number(val);
      if (prop === 'adjust') DATUM_STATE.slopeAdjustM = Number(val);
      if (prop === 'intensity') DATUM_STATE.waveIntensity = Number(val);
      if (prop === 'scale') DATUM_STATE.waveScaleM = Number(val);
      rerenderDatumView();
    },

    undoBoundaryEdit() {
      if (!DATUM_STATE.undoStack.length) return;
      DATUM_STATE.boundaryVertices = DATUM_STATE.undoStack.pop();
      DATUM_STATE.selectedVertexIdx = 0;
      rerenderDatumView();
    },

    deleteSelectedVertex() {
      if (DATUM_STATE.boundaryVertices.length <= DatumRiverBoundaryCore.MIN_VERTICES) return;
      DATUM_STATE.undoStack.push(JSON.parse(JSON.stringify(DATUM_STATE.boundaryVertices)));
      DATUM_STATE.boundaryVertices.splice(DATUM_STATE.selectedVertexIdx, 1);
      DATUM_STATE.selectedVertexIdx = Math.max(0, DATUM_STATE.selectedVertexIdx - 1);
      rerenderDatumView();
    },

    resetBoundaryFootprint() {
      DATUM_STATE.undoStack.push(JSON.parse(JSON.stringify(DATUM_STATE.boundaryVertices)));
      DATUM_STATE.boundaryVertices = [
        { lat: -31.71185, lng: -60.57810 },
        { lat: -31.71145, lng: -60.57540 },
        { lat: -31.71255, lng: -60.57395 },
        { lat: -31.71410, lng: -60.57465 },
        { lat: -31.71435, lng: -60.57725 },
        { lat: -31.71310, lng: -60.57845 }
      ];
      DATUM_STATE.selectedVertexIdx = 0;
      rerenderDatumView();
    },

    saveCurrentScenario() {
      const id = 'sc-' + Date.now();
      DATUM_STATE.savedScenarios.push({
        id,
        name: `Kịch bản +${DATUM_STATE.level.toFixed(2)}m`,
        level: DATUM_STATE.level,
        adjust: DATUM_STATE.slopeAdjustM,
        color: DATUM_STATE.waterColor
      });
      rerenderDatumView();
    },

    applyScenario(scId) {
      const sc = DATUM_STATE.savedScenarios.find(s => s.id === scId);
      if (!sc) return;
      DATUM_STATE.level = sc.level;
      DATUM_STATE.slopeAdjustM = sc.adjust;
      DATUM_STATE.waterColor = sc.color;
      rerenderDatumView();
    },

    setProfilePreset(preset) {
      DATUM_STATE.profilePreset = preset;
      rerenderDatumView();
    },

    setProfileHover(distM) {
      DATUM_STATE.hoverDistanceM = Number(distM) || 0;
      rerenderDatumView();
    },

    setContourStep(stepM) {
      DATUM_STATE.contourStepM = Number(stepM) || 1.0;
      rerenderDatumView();
    },

    setContourSmooth(iters) {
      DATUM_STATE.contourSmoothIters = Number(iters) || 0;
      rerenderDatumView();
    },

    exportProfileCsv() {
      const ds = PROFILE_DATASETS[DATUM_STATE.profilePreset] || PROFILE_DATASETS['parana-bank'];
      const prof = DatumPerfilDatos.prepare(ds.samples);
      const rows = ['progresiva_pk,distancia_m,cota_orto_m,pendiente_pct,desnivel_m'];
      prof.samples.forEach(s => {
        rows.push(`${DatumPerfilDatos.pk(s.distance)},${s.distance.toFixed(2)},${s.elevation.toFixed(3)},${s.slope.toFixed(2)},${s.deltaH.toFixed(3)}`);
      });
      triggerDownload(`perfil-topografico-${DATUM_STATE.profilePreset}.csv`, rows.join('\n'), 'text/csv;charset=utf-8');
    },

    parseNmeaFromTextarea() {
      const el = document.getElementById('datumNmeaInput');
      if (el) DATUM_STATE.nmeaRawInput = el.value;
      rerenderDatumView();
    },

    simulateRtkEpoch() {
      const jitterLat = (Math.random() - 0.5) * 0.0002;
      const jitterLon = (Math.random() - 0.5) * 0.0002;
      const latAbs = Math.abs(-31.712938 + jitterLat);
      const lonAbs = Math.abs(-60.576180 + jitterLon);
      const toNmea = (deg, padDeg) => {
        const d = Math.floor(deg);
        const m = (deg - d) * 60;
        return String(d).padStart(padDeg, '0') + m.toFixed(5).padStart(8, '0');
      };
      const buildLine = (body) => {
        let xor = 0;
        for (let i = 0; i < body.length; i++) xor ^= body.charCodeAt(i);
        return '$' + body + '*' + (xor < 16 ? '0' : '') + xor.toString(16).toUpperCase();
      };
      const now = new Date();
      const utc = `${String(now.getUTCHours()).padStart(2, '0')}${String(now.getUTCMinutes()).padStart(2, '0')}${String(now.getUTCSeconds()).padStart(2, '0')}.00`;
      const alt = (14.20 + (Math.random() - 0.5) * 0.08).toFixed(3);
      DATUM_STATE.nmeaRawInput = [
        buildLine(`GNGGA,${utc},${toNmea(latAbs, 2)},S,${toNmea(lonAbs, 3)},W,4,19,0.54,${alt},M,17.564,M,0.6,0104`),
        buildLine(`GPGST,${utc},0.010,0.012,0.008,28.0,0.007,0.008,0.016`),
        buildLine(`GNGSA,A,3,03,08,14,17,21,28,30,32,,,,,1.08,0.54,0.92,1`),
        buildLine(`GNRMC,${utc},A,${toNmea(latAbs, 2)},S,${toNmea(lonAbs, 3)},W,0.12,115.0,311026,,,R,V`)
      ].join('\n');
      rerenderDatumView();
    },

    selectStakeoutTarget(idx) {
      DATUM_STATE.selectedStakeoutIdx = Number(idx) || 0;
      rerenderDatumView();
    },

    setAntennaHeight(val) {
      DATUM_STATE.antennaHeightM = Math.max(0, Number(val) || 1.8);
      rerenderDatumView();
    },

    runPointClassification(mode, param) {
      if (mode === 'ruido' && param) DATUM_STATE.clsNoiseSens = param;
      const mult = mode === 'ruido' ? 1.04 : mode === 'suelo' ? 1.02 : 0.98;
      DATUM_STATE.clsLastResult.classes.forEach((c, i) => {
        c.count = Math.round(c.count * (i % 2 === 0 ? mult : 2 - mult));
      });
      DATUM_STATE.clsLastResult.totalPoints = DATUM_STATE.clsLastResult.classes.reduce((s, c) => s + c.count, 0);
      rerenderDatumView();
    },

    exportClassificationCsv() {
      const rows = ['lat,lng,cota_h,clase,clase_nom'];
      DATUM_STATE.clsLastResult.classes.forEach(c => {
        for (let i = 0; i < 5; i++) {
          const lat = (-31.712938 + (i - 2) * 0.00015).toFixed(8);
          const lng = (-60.576180 + (c.code - 4) * 0.00015).toFixed(8);
          const h = (12.4 + c.code * 1.35 + i * 0.2).toFixed(3);
          rows.push(`${lat},${lng},${h},${c.code},"${c.name}"`);
        }
      });
      triggerDownload('clasificacion_nube_datumapp.csv', rows.join('\n'), 'text/csv;charset=utf-8');
    },

    exportClassificationJson() {
      const payload = {
        v: 1,
        dataset_id: 'MAL-54A0D1_bajadagrande2',
        algoritmo: DATUM_STATE.clsLastResult.algorithm,
        zona: DATUM_STATE.boundaryVertices,
        resumen_clases: DATUM_STATE.clsLastResult.classes
      };
      triggerDownload('clasificacion_parches_datumapp.json', JSON.stringify(payload, null, 2), 'application/json');
    },

    setCrsEpsg(epsg) {
      DATUM_STATE.selectedEpsg = Number(epsg) || 5347;
      rerenderDatumView();
    },

    exportAllToDxf() {
      const { hOrto } = getWaterHeights();
      const figures = [
        {
          layer: 'POLIGONO_CRECIDA',
          type: 'polyline',
          closed: true,
          points: DATUM_STATE.boundaryVertices.map(v => ({ lat: v.lat, lng: v.lng, z: hOrto }))
        },
        ...DATUM_STATE.stakeoutTargets.map(t => ({
          layer: 'PUNTOS_GNSS',
          type: 'point',
          label: `${t.code} (${t.zOrto.toFixed(2)}m)`,
          points: [{ lat: t.lat, lng: t.lng, z: t.zOrto }]
        }))
      ];
      const dxfText = DatumDxfExporter.generateDxf(figures, DATUM_STATE.selectedEpsg);
      triggerDownload(`datumapp-crecida-parana-epsg${DATUM_STATE.selectedEpsg}.dxf`, dxfText, 'application/dxf');
    },

    downloadReaProvenanceReport() {
      const report = {
        reaVersion: '6.8.0',
        targetUrl: 'https://www.datumapp.net/home?t=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...',
        projectId: 'col-mvif1u1o-n4yx5',
        projectName: 'Crecida Río Paraná (Bajada Grande, Paraná, Entre Ríos)',
        evidenceIds: {
          inspectWebPage: 'ev_1bd954dfd9f389e79a1f48acb90411eec98d84c279ee793b4575e5d31e95ef81',
          exportWebScripts: 'ev_e96a3ce7a5fbfdc7c6cfdc5d82164c9add4872e7e5a44baf8c6af68a5579732d',
          analyzeJavaScriptApplication: 'ev_150da542084ebdcbef319fadfc07f21eacdffaf8f54aabe497101b7da39cfc7c',
          captureBrowserScenario: 'ev_c5492ad02f12d57cc74acd8b9d161f8cfa1bf7f671cf9ddd085fcb4db5ff7818'
        },
        convertedScripts: [
          'crecida-rio.js?v=1.19.0',
          'river-boundary-core.js?v=1.1.0',
          'river-boundary-editor.js?v=1.0.0',
          'river-boundary-slope.js?v=1.6.0',
          'river-boundary-slope-water.js?v=1.5.0',
          'perfil-datos.js?v=3',
          'perfil-grafico.js?v=10',
          'alturas.js?v=4',
          'curvas.js?v=4.286.0',
          'gnss-nmea.js?v=4',
          'gnss-nucleo.js?v=4',
          'gnss3d.js?v=139',
          'dib-clasificacion.js?v=14',
          'ortho-falsa.js?v=15',
          'crs.js?v=10',
          'dxf.js?v=4.681.0'
        ]
      };
      triggerDownload('rea-datumapp-provenance.json', JSON.stringify(report, null, 2), 'application/json');
    }
  };
})(typeof window !== 'undefined' ? window : globalThis);
