const fs = require('fs');
const path = require('path');

const sourcePath = 'C:/Users/prana/Downloads/ironlog_exercises_youtube_links.json';
const outputPath = path.join(process.cwd(), 'src/data/exerciseYoutubeLinks.js');

function normalizeName(value) {
  return String(value || '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '');
}

const rows = JSON.parse(fs.readFileSync(sourcePath, 'utf8'));
const byName = {};

for (const row of rows) {
  const key = normalizeName(row.exercise_name);
  if (!key) continue;
  byName[key] = {
    youtubeLink: row.youtube_link || null,
    youtubeShortsLink: row.youtube_shorts_link || null,
    youtubeSearchQuery: row.search_query || null,
    category: row.category || null,
  };
}

const fileContent = [
  '// Auto-generated from C:/Users/prana/Downloads/ironlog_exercises_youtube_links.json',
  `export const EXERCISE_YOUTUBE_BY_NORMALIZED_NAME = ${JSON.stringify(byName, null, 2)};`,
  '',
].join('\n');

fs.writeFileSync(outputPath, fileContent);
console.log(`Generated ${Object.keys(byName).length} exercise YouTube entries at ${outputPath}`);
