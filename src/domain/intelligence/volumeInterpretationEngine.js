const ROTATION = ['vehicles', 'animals', 'objects', 'industrial'];

const DATASET = [
  { name: 'Dumbbell (heavy pair)', weightKg: 40, category: 'objects' },
  { name: 'Barbell', weightKg: 20, category: 'objects' },
  { name: 'Gym bench', weightKg: 35, category: 'objects' },
  { name: 'Washing machine', weightKg: 65, category: 'objects' },
  { name: 'Dishwasher', weightKg: 50, category: 'objects' },
  { name: 'Office chair', weightKg: 15, category: 'objects' },
  { name: 'Gaming PC', weightKg: 10, category: 'objects' },
  { name: 'Backpack (loaded)', weightKg: 12, category: 'objects' },
  { name: 'Suitcase (full)', weightKg: 25, category: 'objects' },
  { name: 'Microwave', weightKg: 19, category: 'objects' },
  { name: 'Vacuum cleaner', weightKg: 13, category: 'objects' },
  { name: 'Electric guitar', weightKg: 5, category: 'objects' },
  { name: 'Dog (medium)', weightKg: 20, category: 'animals' },
  { name: 'Child (5-7 yrs)', weightKg: 20, category: 'objects' },
  { name: 'Adult human', weightKg: 70, category: 'objects' },
  { name: 'Bicycle', weightKg: 12, category: 'objects' },
  { name: 'Road bike', weightKg: 8, category: 'objects' },
  { name: 'Mattress', weightKg: 30, category: 'objects' },
  { name: 'Sofa (2-seater)', weightKg: 45, category: 'objects' },
  { name: 'Refrigerator', weightKg: 100, category: 'objects' },
  { name: 'Vending machine', weightKg: 300, category: 'objects' },
  { name: 'Motorcycle', weightKg: 180, category: 'vehicles' },
  { name: 'Horse', weightKg: 500, category: 'animals' },
  { name: 'Cow', weightKg: 700, category: 'animals' },
  { name: 'Small piano (upright)', weightKg: 300, category: 'objects' },
  { name: 'Grand piano', weightKg: 500, category: 'objects' },
  { name: 'Smart car', weightKg: 900, category: 'vehicles' },
  { name: 'Golf cart', weightKg: 400, category: 'vehicles' },
  { name: 'Industrial fridge', weightKg: 250, category: 'industrial' },
  { name: 'Jet ski', weightKg: 350, category: 'vehicles' },
  { name: 'ATV', weightKg: 300, category: 'vehicles' },
  { name: 'Large safe', weightKg: 400, category: 'industrial' },
  { name: 'Industrial generator', weightKg: 600, category: 'industrial' },
  { name: 'Bear (adult)', weightKg: 300, category: 'animals' },
  { name: 'Lion', weightKg: 190, category: 'animals' },
  { name: 'Tiger', weightKg: 220, category: 'animals' },
  { name: 'Server rack', weightKg: 250, category: 'industrial' },
  { name: 'Concrete slab (1m3 section)', weightKg: 500, category: 'industrial' },
  { name: 'Brick pallet', weightKg: 800, category: 'industrial' },
  { name: 'Large desk setup', weightKg: 120, category: 'objects' },
  { name: 'Small car', weightKg: 1300, category: 'vehicles' },
  { name: 'Sedan', weightKg: 1500, category: 'vehicles' },
  { name: 'SUV', weightKg: 2000, category: 'vehicles' },
  { name: 'Pickup truck', weightKg: 2500, category: 'vehicles' },
  { name: 'Van', weightKg: 3000, category: 'vehicles' },
  { name: 'Elephant (small)', weightKg: 3000, category: 'animals' },
  { name: 'Elephant (average)', weightKg: 5000, category: 'animals' },
  { name: 'Rhino', weightKg: 2300, category: 'animals' },
  { name: 'Hippopotamus', weightKg: 3000, category: 'animals' },
  { name: 'Small boat', weightKg: 2000, category: 'vehicles' },
  { name: 'Forklift', weightKg: 3000, category: 'industrial' },
  { name: 'Shipping pallet load', weightKg: 1500, category: 'industrial' },
  { name: 'Tractor', weightKg: 4000, category: 'vehicles' },
  { name: 'Large tree trunk', weightKg: 2000, category: 'industrial' },
  { name: 'Mini excavator', weightKg: 3500, category: 'industrial' },
  { name: 'Light aircraft', weightKg: 3000, category: 'vehicles' },
  { name: 'Food truck', weightKg: 3500, category: 'vehicles' },
  { name: 'Ice cream truck', weightKg: 3000, category: 'vehicles' },
  { name: 'Horse trailer', weightKg: 2000, category: 'vehicles' },
  { name: 'Delivery truck (empty)', weightKg: 4000, category: 'vehicles' },
  { name: 'Bus', weightKg: 8000, category: 'vehicles' },
  { name: 'City bus', weightKg: 12000, category: 'vehicles' },
  { name: 'School bus', weightKg: 8000, category: 'vehicles' },
  { name: 'Fire truck', weightKg: 14000, category: 'vehicles' },
  { name: 'Garbage truck', weightKg: 16000, category: 'vehicles' },
  { name: 'Semi truck (no trailer)', weightKg: 7000, category: 'vehicles' },
  { name: 'Bulldozer', weightKg: 10000, category: 'industrial' },
  { name: 'Tank', weightKg: 60000, category: 'vehicles' },
  { name: 'Train carriage', weightKg: 30000, category: 'vehicles' },
  { name: 'Large excavator', weightKg: 20000, category: 'industrial' },
  { name: 'Blue whale', weightKg: 100000, category: 'animals' },
  { name: 'African elephant (large)', weightKg: 6000, category: 'animals' },
  { name: 'Loaded shipping container', weightKg: 20000, category: 'industrial' },
  { name: 'Yacht (small luxury)', weightKg: 15000, category: 'vehicles' },
  { name: 'Concrete mixer truck', weightKg: 12000, category: 'vehicles' },
  { name: 'Mobile crane', weightKg: 18000, category: 'industrial' },
  { name: 'Wind turbine blade', weightKg: 12000, category: 'industrial' },
  { name: 'Large statue', weightKg: 10000, category: 'industrial' },
  { name: 'Steel beam bundle', weightKg: 8000, category: 'industrial' },
  { name: 'Industrial press machine', weightKg: 15000, category: 'industrial' },
  { name: 'Boeing 737', weightKg: 40000, category: 'vehicles' },
  { name: 'Boeing 747', weightKg: 180000, category: 'vehicles', extreme: true },
  { name: 'Cargo ship (small)', weightKg: 5000000, category: 'industrial', extreme: true },
  { name: 'Blue whale (large)', weightKg: 150000, category: 'animals', extreme: true },
  { name: 'Space shuttle', weightKg: 2000000, category: 'vehicles', extreme: true },
  { name: 'Eiffel Tower (partial structure)', weightKg: 7300000, category: 'industrial', extreme: true },
  { name: 'Oil tanker', weightKg: 300000000, category: 'vehicles', extreme: true },
  { name: 'Cruise ship', weightKg: 100000000, category: 'vehicles', extreme: true },
  { name: 'Locomotive engine', weightKg: 200000, category: 'vehicles', extreme: true },
  { name: 'Rocket (Falcon 9)', weightKg: 550000, category: 'vehicles', extreme: true },
  { name: 'Saturn V rocket', weightKg: 3000000, category: 'vehicles', extreme: true },
  { name: 'Aircraft carrier', weightKg: 100000000, category: 'vehicles', extreme: true },
  { name: 'Stadium structure', weightKg: 50000000, category: 'industrial', extreme: true },
  { name: 'Skyscraper section', weightKg: 10000000, category: 'industrial', extreme: true },
  { name: 'Offshore oil rig', weightKg: 50000000, category: 'industrial', extreme: true },
  { name: 'Suspension bridge section', weightKg: 20000000, category: 'industrial', extreme: true },
  { name: 'Mountain (small mass estimate segment)', weightKg: 1000000000, category: 'industrial', extreme: true },
  { name: 'Iceberg (small)', weightKg: 10000000, category: 'industrial', extreme: true },
  { name: 'Glacier section', weightKg: 1000000000, category: 'industrial', extreme: true },
  { name: 'Planet-scale (Earth chunk metaphor)', weightKg: 0, category: 'industrial', extreme: true, disabled: true },
];

function formatCount(value) {
  if (value >= 10) return Math.round(value);
  return Math.round(value * 10) / 10;
}

function formatKg(value) {
  if (value >= 1000) return `${Math.round(value).toLocaleString()} kg`;
  return `${Math.round(value)} kg`;
}

function formatTons(value) {
  return `${(value / 1000).toFixed(1)} tons`;
}

function formatPerformanceInsight({ changePct = null, baselineLabel = 'last week' } = {}) {
  if (typeof changePct !== 'number' || !Number.isFinite(changePct)) return 'first tracked comparison';
  if (Math.abs(changePct) < 1) return `flat vs ${baselineLabel}`;
  if (changePct > 0) return `up ${Math.round(changePct)}% from ${baselineLabel}`;
  return `down ${Math.abs(Math.round(changePct))}% from ${baselineLabel}`;
}

function withArticle(name) {
  const lower = String(name || '').trim().toLowerCase();
  if (!lower) return name;
  if (/^[aeiou]/.test(lower) || /^(adult|african|industrial|office|electric|upright|average|iceberg)/.test(lower)) return `an ${name}`;
  return `a ${name}`;
}

function pluralize(name, count) {
  if (count <= 1.1 || /s$/i.test(name)) return name;
  return `${name}s`;
}

function getRotationCategory(entry) {
  if (entry.category === 'vehicles') return 'vehicles';
  if (entry.category === 'animals') return 'animals';
  if (entry.category === 'industrial') return 'industrial';
  return 'objects';
}

function getDesiredRotation(recentUsage = []) {
  const lastCategory = recentUsage[0]?.rotationCategory;
  const index = ROTATION.indexOf(lastCategory);
  return index === -1 ? ROTATION[0] : ROTATION[(index + 1) % ROTATION.length];
}

function chooseComparisonEntry(totalKg, { recentUsage = [], maxKg = 100000, allowExtreme = false } = {}) {
  const blockedNames = new Set((recentUsage || []).slice(0, 3).map((entry) => String(entry.objectName || '').toLowerCase()));
  const desiredRotation = getDesiredRotation(recentUsage);

  const candidates = DATASET.filter((entry) => {
    if (entry.disabled) return false;
    if (!allowExtreme && entry.extreme) return false;
    if (!allowExtreme && entry.weightKg > maxKg) return false;
    if (!entry.weightKg || entry.weightKg <= 0) return false;
    if (blockedNames.has(entry.name.toLowerCase())) return false;
    return true;
  });

  if (!candidates.length) return null;

  return candidates
    .map((entry) => {
      const distance = Math.abs(totalKg - entry.weightKg) / Math.max(totalKg, entry.weightKg);
      const rotationBonus = getRotationCategory(entry) === desiredRotation ? -0.12 : 0;
      const antiSpamPenalty = /(car|piano)/i.test(entry.name) ? 0.08 : 0;
      return { entry, score: distance + rotationBonus + antiSpamPenalty };
    })
    .sort((a, b) => a.score - b.score)[0]?.entry || null;
}

export function buildVolumeInterpretation(totalKg, options = {}) {
  const comparison = chooseComparisonEntry(totalKg, options);
  if (!comparison) {
    return { text: `You lifted ${formatTons(totalKg)} this week.`, objectName: null, category: null, ratio: null, fallback: true, rotationCategory: null };
  }

  const ratio = totalKg / comparison.weightKg;
  let comparisonText = `roughly ${withArticle(comparison.name)}`;
  if (ratio < 0.75) {
    comparisonText = `about ${Math.max(5, Math.round(ratio * 100))}% of ${withArticle(comparison.name)}`;
  } else if (ratio > 1.25 && ratio <= 5) {
    comparisonText = `like lifting ~${formatCount(ratio)} ${pluralize(comparison.name, ratio)}`;
  } else if (ratio > 5) {
    comparisonText = `like lifting ${formatCount(ratio)} ${pluralize(comparison.name, ratio)} stacked`;
  }

  return {
    text: comparisonText,
    objectName: comparison.name,
    category: comparison.category,
    ratio,
    fallback: false,
    rotationCategory: getRotationCategory(comparison),
  };
}

export function buildVolumeInterpretationSentence({
  totalKg,
  changePct = null,
  baselineLabel = 'last week',
  recentUsage = [],
  maxKg = 100000,
  allowExtreme = false,
} = {}) {
  const interpretation = buildVolumeInterpretation(totalKg, { recentUsage, maxKg, allowExtreme });
  if (interpretation.fallback) return interpretation.text;
  return `You lifted ${formatKg(totalKg)}, ${formatPerformanceInsight({ changePct, baselineLabel })}, ${interpretation.text}.`;
}

export function getVolumeComparisonDataset() {
  return DATASET.slice();
}
