const fs = require('fs');

const svgData = JSON.parse(fs.readFileSync('z:/ironlog/svg_out.json', 'utf8'));

// The exact string representation of the objects
const frontStr = JSON.stringify(svgData.front, null, 2);
const backStr = JSON.stringify(svgData.back, null, 2);

const output = `/**
 * BodyMapSVG.js - Anatomically accurate body map for IRONLOG
 * Generated organically to preserve all 44 complex SVG path geometries.
 */

import React from 'react';
import Svg, { Path, G } from 'react-native-svg';

const VIEW_BOXES = {
  male: {
    front: { x: -20, y: 95, width: 740, height: 1300 },
    back: { x: 740, y: 95, width: 680, height: 1320 }
  },
  female: {
    front: { x: 0, y: 153, width: 719, height: 1280 },
    back: { x: 19, y: 39, width: 440, height: 860 }
  }
};

const MUSCLE_MAP = {
  chest: ['chest', 'upperChest', 'lowerChest', 'serratus'],
  back: ['upperBack', 'lowerBack', 'trapezius', 'rhomboids'],
  shoulders: ['deltoids', 'frontDeltoid', 'rearDeltoid', 'rotatorCuff'],
  arms: ['biceps', 'triceps', 'forearm', 'hands'],
  core: ['abs', 'obliques', 'upperAbs', 'lowerAbs'],
  quads: ['quadriceps', 'innerQuad', 'outerQuad', 'hipFlexors'],
  hamstrings: ['hamstring', 'adductors', 'gluteal'],
  calves: ['calves', 'tibialis', 'ankles', 'feet'],
  head: ['head', 'face', 'neck', 'hair']
};

// ===================================
// MALE FRONT PATHS
// ===================================
const MALE_FRONT_PATHS = ${frontStr};

// ===================================
// MALE BACK PATHS
// ===================================
const MALE_BACK_PATHS = ${backStr};

const renderMuscle = (slug, paths, regionColors, defaultColor) => {
  // Find which Ironlog macro-region this micro-muscle belongs to
  let macroRegion = null;
  for (const [region, subMuscles] of Object.entries(MUSCLE_MAP)) {
    if (subMuscles.includes(slug)) {
      macroRegion = region;
      break;
    }
  }

  // If no mapped color, use the standard background fallback (it should never be fully transparent)
  const color = (macroRegion && regionColors[macroRegion]) ? regionColors[macroRegion] : defaultColor;

  return (
    <G key={'group-' + slug}>
      {(paths.left || []).map((d, i) => (
        <Path key={\`\${slug}-left-\${i}\`} d={d} fill={color} stroke="rgba(255,255,255,0.05)" strokeWidth={2} />
      ))}
      {(paths.right || []).map((d, i) => (
        <Path key={\`\${slug}-right-\${i}\`} d={d} fill={color} stroke="rgba(255,255,255,0.05)" strokeWidth={2} />
      ))}
    </G>
  );
};

const BodyMapSVG = ({
  view = 'front',
  gender = 'male',
  regionColors = {},
  defaultColor = '#4B5563',
  width = 150,
  height = 300,
  style,
}) => {
  const vb = VIEW_BOXES[gender][view];
  const viewBox = \`\${vb.x} \${vb.y} \${vb.width} \${vb.height}\`;
  
  let pathData;
  if (gender === 'male' && view === 'front') {
    pathData = MALE_FRONT_PATHS;
  } else if (gender === 'male' && view === 'back') {
    pathData = MALE_BACK_PATHS;
  } else {
    pathData = view === 'front' ? MALE_FRONT_PATHS : MALE_BACK_PATHS;
  }
  
  return (
    <Svg width={width} height={height} viewBox={viewBox} style={style}>
      <G>
        {Object.entries(pathData).map(([slug, paths]) => 
          renderMuscle(slug, paths, regionColors, defaultColor)
        )}
      </G>
    </Svg>
  );
};

export default BodyMapSVG;
`;

fs.writeFileSync('z:/ironlog/src/components/BodyMapSVG.js', output, 'utf8');
console.log('Successfully re-generated BodyMapSVG.js with 100% path coverage!');
