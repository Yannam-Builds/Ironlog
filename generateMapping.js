const https = require('https');

const EXISTING = [
  'Barbell Bench Press','Incline Barbell Bench Press','DB Bench Press','Incline DB Bench Press',
  'Cable Fly Low to High','Cable Fly','Pec Deck','Incline Smith Press','Push-Up','Dips (Chest)',
  'Pull-Up','Weighted Pull-Up','Chin-Up','Lat Pulldown','Single Arm Cable Pulldown','Barbell Row',
  'Single Arm DB Row','Seated Cable Row','Face Pull','Straight Arm Pulldown','Deadlift','Sumo Deadlift',
  'Hyperextension','Meadows Row','Chest Supported Row','Barbell OHP','DB OHP','Arnold Press',
  'Cable Lateral Raise','DB Lateral Raise','Lateral Raise Machine','Rear Delt Fly','Reverse Pec Deck',
  'DB Shrugs','Barbell Shrugs','Barbell Curl','EZ Bar Curl','DB Curl','Hammer Curl','Incline DB Curl',
  'Concentration Curl','Preacher Curl','Cable Curl','Spider Curl','Zottman Curl',
  'Close Grip Bench Press','Skull Crusher','Rope Pushdown','Single Arm Pushdown',
  'Rope Overhead Extension','Single Arm OHE','Dips (Triceps)','Diamond Push-Up',
  'Back Squat','Front Squat','Bulgarian Split Squat','Romanian Deadlift','Leg Press',
  'Leg Press High Feet','Hack Squat','Leg Extension','Leg Curl Machine','Nordic Curl','Hip Thrust',
  'Walking Lunge','Step Up','Lateral Band Walk','Single Leg Calf Raise','Standing Calf Raise',
  'Seated Calf Raise','Weighted Cable Crunch','Hanging Leg Raise','Ab Wheel Rollout','Plank',
  'Side Plank','Dragon Flag','L-Sit','Toes to Bar','Russian Twist','Dead Bug','V-Up',
  'Hip 90/90 Stretch',"World's Greatest Stretch",'Ankle Mobility Drill','Cat Cow',
  'Couch Stretch','Deep Squat Hold','Incline Treadmill Walk','Running',
  'Weighted Pull-Up or Lat Pulldown',
];

function normalize(s) {
  return s.toLowerCase()
    .replace(/\bdumbbell\b/g, 'db').replace(/\bdb\b/g, 'db')
    .replace(/\bbarbell\b/g, 'bb').replace(/\boverhead press\b/g, 'ohp')
    .replace(/\bohp\b/g, 'ohp').replace(/[^a-z0-9 ]/g, ' ')
    .replace(/\s+/g, ' ').trim();
}

function levenshtein(a, b) {
  const dp = Array.from({length: a.length+1}, (_, i) => [i, ...Array(b.length).fill(0)]);
  dp[0] = Array.from({length: b.length+1}, (_, i) => i);
  for (let i = 1; i <= a.length; i++)
    for (let j = 1; j <= b.length; j++)
      dp[i][j] = a[i-1] === b[j-1] ? dp[i-1][j-1] : 1 + Math.min(dp[i-1][j], dp[i][j-1], dp[i-1][j-1]);
  return dp[a.length][b.length];
}

function score(a, b) {
  const na = normalize(a), nb = normalize(b);
  if (na === nb) return 1.0;
  const dist = levenshtein(na, nb);
  const maxLen = Math.max(na.length, nb.length);
  const sim = 1 - dist / maxLen;
  // Boost if all words of shorter string appear in longer
  const wordsA = na.split(' '), wordsB = nb.split(' ');
  const shorter = wordsA.length < wordsB.length ? wordsA : wordsB;
  const longer = wordsA.length < wordsB.length ? wordsB : wordsA;
  const wordMatch = shorter.every(w => longer.some(lw => lw.includes(w) || w.includes(lw)));
  return wordMatch ? Math.max(sim, 0.75) : sim;
}

https.get('https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json', res => {
  let d = '';
  res.on('data', c => d += c);
  res.on('end', () => {
    const db = JSON.parse(d);
    const lines = ['// src/data/exerciseMapping.js', '// confidence: HIGH=≥0.85, MED=0.70-0.84, LOW=<0.70 (review carefully)', '// null = no match found in free-exercise-db', '', 'export const EXERCISE_ID_MAP = {'];

    for (const name of EXISTING) {
      let best = null, bestScore = 0;
      for (const ex of db) {
        const s = score(name, ex.name);
        if (s > bestScore) { bestScore = s; best = ex; }
      }
      const conf = bestScore >= 0.85 ? 'HIGH' : bestScore >= 0.70 ? 'MED' : 'LOW';
      const val = bestScore >= 0.60 ? `'${best.id}'` : 'null';
      const comment = bestScore >= 0.60 ? `// ${conf} (${(bestScore*100).toFixed(0)}%) → "${best.name}"` : '// NO MATCH';
      lines.push(`  '${name}': ${val}, ${comment}`);
    }
    lines.push('};');
    console.log(lines.join('\n'));
  });
}).on('error', e => console.error(e));
