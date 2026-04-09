import { MALE_BACK_PATHS, MALE_FRONT_PATHS, VIEW_BOXES } from '../components/BodyMapSVG';

const MUSCLE_LABELS = {
  abs: 'Abs',
  adductors: 'Adductors',
  ankles: 'Ankles',
  biceps: 'Biceps',
  calves: 'Calves',
  chest: 'Chest',
  deltoids: 'Side Delts',
  feet: 'Feet',
  forearm: 'Forearms',
  frontDeltoid: 'Front Delts',
  gluteal: 'Glutes',
  hamstring: 'Hamstrings',
  hands: 'Hands',
  head: 'Head',
  hipFlexors: 'Hip Flexors',
  innerQuad: 'Inner Quads',
  knees: 'Knees',
  lowerAbs: 'Lower Abs',
  lowerBack: 'Lower Back',
  lowerChest: 'Lower Chest',
  lowerTrapezius: 'Lower Traps',
  neck: 'Neck',
  obliques: 'Obliques',
  outerQuad: 'Outer Quads',
  quadriceps: 'Quads',
  rearDeltoid: 'Rear Delts',
  rhomboids: 'Rhomboids',
  rotatorCuff: 'Rotator Cuff',
  serratus: 'Serratus',
  tibialis: 'Tibialis',
  trapezius: 'Traps',
  triceps: 'Triceps',
  upperAbs: 'Upper Abs',
  upperBack: 'Upper Back',
  upperChest: 'Upper Chest',
  upperTrapezius: 'Upper Traps',
};

const VIEW_PATHS = {
  front: MALE_FRONT_PATHS,
  back: MALE_BACK_PATHS,
};

const PARAMS_PER_COMMAND = {
  A: 7,
  C: 6,
  H: 1,
  L: 2,
  M: 2,
  Q: 4,
  S: 4,
  T: 2,
  V: 1,
  Z: 0,
};

const numberToken = /-?\d*\.?\d+(?:e[-+]?\d+)?/gi;

function titleCaseSlug(slug) {
  return String(slug || '')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/[-_]+/g, ' ')
    .split(' ')
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(' ');
}

function splitCommandBlocks(path) {
  const blocks = [];
  let current = null;

  for (let i = 0; i < path.length; i += 1) {
    const ch = path[i];
    if (/[a-zA-Z]/.test(ch)) {
      if (current) blocks.push(current);
      current = { cmd: ch, payload: '' };
    } else if (current) {
      current.payload += ch;
    }
  }

  if (current) blocks.push(current);
  return blocks;
}

function parseNumbers(payload) {
  const out = [];
  if (!payload) return out;
  numberToken.lastIndex = 0;
  let match = numberToken.exec(payload);
  while (match) {
    out.push(Number(match[0]));
    match = numberToken.exec(payload);
  }
  return out;
}

function collectPointsFromPath(pathData) {
  const points = [];
  let cx = 0;
  let cy = 0;
  let sx = 0;
  let sy = 0;

  const pushPoint = (x, y) => {
    if (!Number.isFinite(x) || !Number.isFinite(y)) return;
    points.push({ x, y });
    cx = x;
    cy = y;
  };

  const blocks = splitCommandBlocks(pathData || '');

  blocks.forEach(({ cmd, payload }) => {
    const isRelative = cmd === cmd.toLowerCase();
    const op = cmd.toUpperCase();
    const arity = PARAMS_PER_COMMAND[op];
    const values = parseNumbers(payload);
    if (arity === undefined) return;

    if (op === 'Z') {
      pushPoint(sx, sy);
      return;
    }

    if (!arity || !values.length) return;

    for (let i = 0; i + arity - 1 < values.length; i += arity) {
      if (op === 'M') {
        const x = isRelative ? cx + values[i] : values[i];
        const y = isRelative ? cy + values[i + 1] : values[i + 1];
        pushPoint(x, y);
        sx = x;
        sy = y;
        continue;
      }

      if (op === 'L') {
        const x = isRelative ? cx + values[i] : values[i];
        const y = isRelative ? cy + values[i + 1] : values[i + 1];
        pushPoint(x, y);
        continue;
      }

      if (op === 'H') {
        const x = isRelative ? cx + values[i] : values[i];
        pushPoint(x, cy);
        continue;
      }

      if (op === 'V') {
        const y = isRelative ? cy + values[i] : values[i];
        pushPoint(cx, y);
        continue;
      }

      if (op === 'C') {
        const x1 = isRelative ? cx + values[i] : values[i];
        const y1 = isRelative ? cy + values[i + 1] : values[i + 1];
        const x2 = isRelative ? cx + values[i + 2] : values[i + 2];
        const y2 = isRelative ? cy + values[i + 3] : values[i + 3];
        const x = isRelative ? cx + values[i + 4] : values[i + 4];
        const y = isRelative ? cy + values[i + 5] : values[i + 5];
        points.push({ x: x1, y: y1 }, { x: x2, y: y2 });
        pushPoint(x, y);
        continue;
      }

      if (op === 'S') {
        const x2 = isRelative ? cx + values[i] : values[i];
        const y2 = isRelative ? cy + values[i + 1] : values[i + 1];
        const x = isRelative ? cx + values[i + 2] : values[i + 2];
        const y = isRelative ? cy + values[i + 3] : values[i + 3];
        points.push({ x: x2, y: y2 });
        pushPoint(x, y);
        continue;
      }

      if (op === 'Q') {
        const x1 = isRelative ? cx + values[i] : values[i];
        const y1 = isRelative ? cy + values[i + 1] : values[i + 1];
        const x = isRelative ? cx + values[i + 2] : values[i + 2];
        const y = isRelative ? cy + values[i + 3] : values[i + 3];
        points.push({ x: x1, y: y1 });
        pushPoint(x, y);
        continue;
      }

      if (op === 'T') {
        const x = isRelative ? cx + values[i] : values[i];
        const y = isRelative ? cy + values[i + 1] : values[i + 1];
        pushPoint(x, y);
        continue;
      }

      if (op === 'A') {
        const rx = Math.abs(values[i]);
        const ry = Math.abs(values[i + 1]);
        const x = isRelative ? cx + values[i + 5] : values[i + 5];
        const y = isRelative ? cy + values[i + 6] : values[i + 6];
        points.push(
          { x: cx - rx, y: cy - ry },
          { x: cx + rx, y: cy + ry },
          { x: x - rx, y: y - ry },
          { x: x + rx, y: y + ry }
        );
        pushPoint(x, y);
      }
    }
  });

  return points;
}

function specificityBias(slug) {
  if (/upper|lower|inner|outer|front|rear|rhomboids|rotator|serratus|adductors|tibialis|hipFlexors/i.test(slug)) {
    return 0;
  }
  if (/back|quadriceps|hamstring|trapezius|deltoids|obliques|forearm|gluteal/i.test(slug)) {
    return 0.06;
  }
  if (/hands|head|hair/.test(slug)) {
    return 0.25;
  }
  return 0.12;
}

function buildHitZones(view) {
  const vb = VIEW_BOXES?.male?.[view];
  const musclePaths = VIEW_PATHS[view] || {};
  const zones = [];

  Object.entries(musclePaths).forEach(([slug, pathSet]) => {
    const allPaths = [
      ...(Array.isArray(pathSet?.common) ? pathSet.common : []),
      ...(Array.isArray(pathSet?.left) ? pathSet.left : []),
      ...(Array.isArray(pathSet?.right) ? pathSet.right : []),
    ];

    let minX = Number.POSITIVE_INFINITY;
    let minY = Number.POSITIVE_INFINITY;
    let maxX = Number.NEGATIVE_INFINITY;
    let maxY = Number.NEGATIVE_INFINITY;

    allPaths.forEach((d) => {
      collectPointsFromPath(d).forEach((point) => {
        const x = point.x - vb.x;
        const y = point.y;
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
      });
    });

    if (!Number.isFinite(minX) || !Number.isFinite(maxX) || !Number.isFinite(minY) || !Number.isFinite(maxY)) {
      return;
    }

    const width = Math.max(1, maxX - minX);
    const height = Math.max(1, maxY - minY);
    const area = width * height;

    zones.push({
      slug,
      label: MUSCLE_LABELS[slug] || titleCaseSlug(slug),
      minX,
      maxX,
      minY,
      maxY,
      width,
      height,
      cx: minX + width / 2,
      cy: minY + height / 2,
      area,
      bias: specificityBias(slug),
    });
  });

  const maxArea = zones.reduce((acc, zone) => Math.max(acc, zone.area), 1);
  return zones.map((zone) => ({
    ...zone,
    areaRatio: zone.area / maxArea,
  }));
}

const HIT_ZONES = {
  front: buildHitZones('front'),
  back: buildHitZones('back'),
};

function normalizedCenterDistance(x, y, zone) {
  const dx = (x - zone.cx) / (zone.width + 20);
  const dy = (y - zone.cy) / (zone.height + 20);
  return Math.sqrt(dx * dx + dy * dy);
}

function dynamicPenalty(view, zone, x, y) {
  let penalty = 0;
  const vb = VIEW_BOXES?.male?.[view];
  if (!vb) return penalty;

  const centerX = vb.width / 2;
  const nearCenter = Math.abs(x - centerX) <= vb.width * 0.12;

  if (zone.slug === 'hands') {
    // Don't let hand paths steal forearm touches.
    penalty += 0.2;
    if (y < zone.cy) penalty += 0.25;
  }

  if (view === 'back') {
    // Midline upper back should prefer traps/upper-back over rear delts.
    if (nearCenter && zone.slug === 'deltoids') penalty += 0.2;
    if (nearCenter && /trapezius|upperBack|rhomboids/.test(zone.slug)) penalty -= 0.1;
    // Lower posterior touches should favor glutes/hamstrings over broad back regions.
    if (y > vb.y + vb.height * 0.58 && /upperBack|lowerBack|trapezius|rhomboids/.test(zone.slug)) {
      penalty += 0.2;
    }
    if (y > vb.y + vb.height * 0.55 && /gluteal|hamstring/.test(zone.slug)) {
      penalty -= 0.08;
    }
  }

  if (view === 'front') {
    // Midline chest/abs should not accidentally map to arms.
    if (nearCenter && /biceps|triceps|forearm|hands/.test(zone.slug)) penalty += 0.12;
    if (nearCenter && /chest|abs|obliques|upperAbs|lowerAbs/.test(zone.slug)) penalty -= 0.06;
  }

  return penalty;
}

function scoreZone(view, zone, x, y, isInside) {
  const center = normalizedCenterDistance(x, y, zone);
  const areaPenalty = zone.areaRatio * (isInside ? 0.22 : 0.5);
  return center + areaPenalty + zone.bias + dynamicPenalty(view, zone, x, y);
}

function maybePreferRearDelts(view, x, y, chosenZone, candidates) {
  if (view !== 'back' || !chosenZone) return chosenZone;
  if (!['deltoids', 'rearDeltoid'].includes(chosenZone.slug)) return chosenZone;

  const rearDeltCandidate = candidates.find((zone) => zone.slug === 'rearDeltoid');
  if (!rearDeltCandidate) return chosenZone;

  const chosenScore = scoreZone(view, chosenZone, x, y, true);
  const rearScore = scoreZone(view, rearDeltCandidate, x, y, true);
  return rearScore <= chosenScore + 0.08 ? rearDeltCandidate : chosenZone;
}

export function getMuscleAtPoint({ view, x, y }) {
  const zones = HIT_ZONES[view] || [];
  if (!zones.length || !Number.isFinite(x) || !Number.isFinite(y)) return null;

  const insidePadding = 6;
  const inside = zones.filter(
    (zone) =>
      x >= zone.minX - insidePadding &&
      x <= zone.maxX + insidePadding &&
      y >= zone.minY - insidePadding &&
      y <= zone.maxY + insidePadding
  );

  if (inside.length) {
    const chosen = inside.reduce((best, zone) => {
      if (!best) return zone;
      return scoreZone(view, zone, x, y, true) < scoreZone(view, best, x, y, true) ? zone : best;
    }, null);
    return maybePreferRearDelts(view, x, y, chosen, inside);
  }

  const nearest = zones.reduce((best, zone) => {
    if (!best) return zone;
    return scoreZone(view, zone, x, y, false) < scoreZone(view, best, x, y, false) ? zone : best;
  }, null);

  if (!nearest) return null;
  const dist = normalizedCenterDistance(x, y, nearest);
  return dist <= 1.2 ? nearest : null;
}

export function getMuscleAtTouch({ view, mapWidth, mapHeight, locationX, locationY }) {
  if (!mapWidth || !mapHeight) return null;
  const vb = VIEW_BOXES?.male?.[view];
  if (!vb) return null;

  // BodyMapSVG uses preserveAspectRatio="xMidYMid meet" by default.
  // So the visible body may not fill the entire gesture surface.
  const scale = Math.min(mapWidth / vb.width, mapHeight / vb.height);
  const renderedWidth = vb.width * scale;
  const renderedHeight = vb.height * scale;
  const offsetX = (mapWidth - renderedWidth) / 2;
  const offsetY = (mapHeight - renderedHeight) / 2;

  if (
    locationX < offsetX ||
    locationX > offsetX + renderedWidth ||
    locationY < offsetY ||
    locationY > offsetY + renderedHeight
  ) {
    return null;
  }

  const localX = locationX - offsetX;
  const localY = locationY - offsetY;
  const svgX = (localX / renderedWidth) * vb.width;
  const svgY = vb.y + (localY / renderedHeight) * vb.height;

  return getMuscleAtPoint({ view, x: svgX, y: svgY });
}
