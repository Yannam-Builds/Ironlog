const fs = require('fs');
const path = require('path');

const root = process.argv[2] || 'src';
const needle = process.argv[3] || 'kg';

function walk(dir, out) {
  for (const entry of fs.readdirSync(dir)) {
    const full = path.join(dir, entry);
    const stat = fs.statSync(full);
    if (stat.isDirectory()) {
      walk(full, out);
      continue;
    }
    if (!/\.(js|jsx)$/.test(entry)) continue;
    const text = fs.readFileSync(full, 'utf8');
    const lines = text.split(/\r?\n/);
    lines.forEach((line, idx) => {
      if (line.includes(needle)) {
        out.push(`${full}:${idx + 1}: ${line.trim()}`);
      }
    });
  }
}

const out = [];
walk(path.resolve(root), out);
console.log(out.join('\n'));
