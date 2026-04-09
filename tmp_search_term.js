const fs = require('fs');
const path = require('path');

const term = (process.argv[2] || '').toLowerCase();

function walk(dir) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(fullPath);
      continue;
    }
    if (!entry.name.endsWith('.js')) continue;

    const lines = fs.readFileSync(fullPath, 'utf8').split(/\r?\n/);
    lines.forEach((line, index) => {
      if (line.toLowerCase().includes(term)) {
        console.log(`${fullPath}:${index + 1}: ${line.trim()}`);
      }
    });
  }
}

walk('src');
