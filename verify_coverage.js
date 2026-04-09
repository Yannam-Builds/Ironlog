const { generateExerciseMap } = require('./src/utils/intelligenceEngine');
const { EXERCISES } = require('./src/data/exerciseLibrary');

const map = generateExerciseMap(EXERCISES);
const blindSpots = Object.entries(map).filter(([n, d]) => Object.keys(d.contribution).length === 0);
const patternMapped = Object.entries(map).filter(([n, d]) => d.patternKey !== 'unknown');
const fallbackMapped = Object.entries(map).filter(([n, d]) => d.patternKey === 'unknown' && Object.keys(d.contribution).length > 0);

console.log('--- Heatmap Coverage Report ---');
console.log(`Total Exercises: ${EXERCISES.length}`);
console.log(`Pattern Mapped (High Quality): ${patternMapped.length}`);
console.log(`Fallback Mapped (Basic): ${fallbackMapped.length}`);
console.log(`Blind Spots (No Heatmap): ${blindSpots.length}`);
console.log('-------------------------------');

if (blindSpots.length > 0) {
  console.log('Exercises with NO Heatmap Activation:');
  blindSpots.slice(0, 30).forEach(([name]) => console.log(` - ${name}`));
} else {
  console.log('SUCCESS: 100% Heatmap Activation Coverage Achieved!');
}
