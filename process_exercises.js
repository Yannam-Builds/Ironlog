const fs = require('fs');

const raw = JSON.parse(fs.readFileSync('C:/Users/prana/Downloads/exercises_raw.json', 'utf8'));

// The app expects some capitalized fields and consistent structure
const MUSCLE_GROUPS = [...new Set(raw.flatMap(d => d.primaryMuscles))].map(m => m.charAt(0).toUpperCase() + m.slice(1));

const EXERCISES = raw.map(ex => {
  // Free DB sometimes uses arrays, sometimes strings. Normalize them.
  let pMuscles = Array.isArray(ex.primaryMuscles) ? ex.primaryMuscles : [];
  let sMuscles = Array.isArray(ex.secondaryMuscles) ? ex.secondaryMuscles : [];
  
  // Fix naming for our muscle groups to look nice
  pMuscles = pMuscles.map(m => m.charAt(0).toUpperCase() + m.slice(1));
  sMuscles = sMuscles.map(m => m.charAt(0).toUpperCase() + m.slice(1));

  let mainGroup = pMuscles[0] || 'Other';

  return {
    id: ex.id || ex.name.replace(/\s+/g, '_'),
    name: ex.name,
    muscleGroup: mainGroup,
    primaryMuscles: pMuscles,
    secondaryMuscles: sMuscles,
    equipment: (ex.equipment || 'Bodyweight').charAt(0).toUpperCase() + (ex.equipment || 'Bodyweight').slice(1),
    category: ex.category || 'strength',
    level: ex.level || 'beginner',
    instructions: Array.isArray(ex.instructions) ? ex.instructions : [],
    images: Array.isArray(ex.images) ? ex.images : [],
    // Add a short cue from instructions
    cue: Array.isArray(ex.instructions) && ex.instructions.length > 0 ? ex.instructions[0].substring(0, 120) : ''
  };
});

// 1. Generate new exerciseLibrary.js
const fileHeader = `// Generated from free-exercise-db (873 unique exercises)

export const MUSCLE_GROUPS = ${JSON.stringify(MUSCLE_GROUPS, null, 2)};

export const EXERCISES = ${JSON.stringify(EXERCISES, null, 2)};
`;

fs.writeFileSync('z:/ironlog/src/data/exerciseLibrary.js', fileHeader, 'utf8');
console.log('Successfully wrote src/data/exerciseLibrary.js');

// 2. Generate text file for user
const plainList = EXERCISES.map((ex, i) => `${i + 1}. ${ex.name} - [${ex.muscleGroup}] (${ex.equipment})`).join('\n');
fs.writeFileSync('z:/ironlog/ironlog_873_exercises.txt', `IRONLOG: Official List of 873 Exercises\n\n${plainList}`, 'utf8');
console.log('Successfully wrote ironlog_873_exercises.txt');
