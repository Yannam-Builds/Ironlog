#!/usr/bin/env node
/**
 * gemini-bridge.js
 * Shared Claude Code / Codex → Gemini bridge.
 * Collects local file context, then makes one deterministic `gemini` CLI call.
 *
 * Usage:
 *   node scripts/gemini-bridge.js [--model <name>] [--dirs <d1,d2>]
 *       [--files <glob1,glob2>] [--format text|json] -- "<task>"
 */

const { execSync, spawnSync } = require('child_process');
const fs   = require('fs');
const path = require('path');

// ── arg parsing ───────────────────────────────────────────────────────────────
const args   = process.argv.slice(2);
let model    = 'gemini-2.5-pro';
let dirs     = [];
let files    = [];
let format   = 'text';
let taskParts = [];
let pastSep  = false;

for (let i = 0; i < args.length; i++) {
  if (args[i] === '--') { pastSep = true; continue; }
  if (pastSep) { taskParts.push(args[i]); continue; }
  if (args[i] === '--model'  && args[i+1]) { model  = args[++i]; continue; }
  if (args[i] === '--dirs'   && args[i+1]) { dirs   = args[++i].split(',').map(s => s.trim()); continue; }
  if (args[i] === '--files'  && args[i+1]) { files  = args[++i].split(',').map(s => s.trim()); continue; }
  if (args[i] === '--format' && args[i+1]) { format = args[++i]; continue; }
  taskParts.push(args[i]);
}

const task = taskParts.join(' ').trim();
if (!task) { console.error('Error: no task provided.'); process.exit(1); }

// ── file collection ───────────────────────────────────────────────────────────
const MAX_FILE_BYTES = 80_000;   // per-file cap to stay within token limits
const MAX_TOTAL_BYTES = 900_000; // total context cap

let totalBytes = 0;
let context = '';

function appendFile(filePath) {
  if (totalBytes >= MAX_TOTAL_BYTES) return;
  try {
    const rel  = path.relative(process.cwd(), filePath);
    const stat = fs.statSync(filePath);
    if (stat.size === 0) return;
    let content = fs.readFileSync(filePath, 'utf8');
    if (content.length > MAX_FILE_BYTES) content = content.slice(0, MAX_FILE_BYTES) + '\n... [truncated]';
    const chunk = `\n\n### FILE: ${rel}\n\`\`\`\n${content}\n\`\`\``;
    totalBytes += chunk.length;
    context   += chunk;
  } catch (_) { /* skip unreadable */ }
}

function walkDir(dir) {
  const SKIP = new Set(['node_modules', '.git', 'android', 'ios', 'build', 'dist', 'archive', '.gradle']);
  if (!fs.existsSync(dir)) return;
  for (const entry of fs.readdirSync(dir)) {
    if (SKIP.has(entry)) continue;
    const full = path.join(dir, entry);
    const stat = fs.statSync(full);
    if (stat.isDirectory()) walkDir(full);
    else if (/\.(js|ts|jsx|tsx|json|md|txt|gradle|kt)$/.test(entry)) appendFile(full);
  }
}

// inline dirs
for (const d of dirs) walkDir(path.resolve(d));

// inline explicit file globs (basic glob: * wildcard only)
for (const pattern of files) {
  if (!pattern.includes('*')) {
    appendFile(path.resolve(pattern));
  } else {
    // use node glob if available, else skip
    try {
      const { globSync } = require('glob');
      for (const f of globSync(pattern)) appendFile(path.resolve(f));
    } catch (_) {
      console.warn(`Warning: glob pattern "${pattern}" skipped (install 'glob' package for glob support)`);
    }
  }
}

// ── build prompt ──────────────────────────────────────────────────────────────
const formatInstruction = format === 'json'
  ? '\n\nRespond with valid JSON only, no markdown wrapper.'
  : '';

const prompt = context
  ? `You are reviewing a React Native / WatermelonDB Android codebase called IronlogDB.\n\nCODEBASE CONTEXT:\n${context}\n\n---\n\nTASK: ${task}${formatInstruction}`
  : `You are reviewing the IronlogDB codebase (React Native, WatermelonDB, Android-first).\n\nTASK: ${task}${formatInstruction}`;

// ── call gemini CLI ───────────────────────────────────────────────────────────
console.error(`[gemini-bridge] model=${model} dirs=[${dirs}] files=[${files}] format=${format} contextBytes=${totalBytes}`);
console.error(`[gemini-bridge] task: ${task}`);

// Resolve the gemini CLI entry point.
// Prefer the JS bundle (works cross-platform, no .cmd wrapper needed):
//   <npm-prefix>/node_modules/@google/gemini-cli/bundle/gemini.js
// Fallback: scan PATH for a runnable binary.
function findGeminiJs() {
  // Common npm global prefix locations
  const prefixCandidates = [];
  try { prefixCandidates.push(execSync('npm config get prefix', { encoding: 'utf8' }).trim()); } catch (_) {}
  prefixCandidates.push(
    path.join(require('os').homedir(), 'AppData', 'Roaming', 'npm'),
    '/usr/local',
    '/usr',
  );
  for (const prefix of prefixCandidates) {
    const p = path.join(prefix, 'node_modules', '@google', 'gemini-cli', 'bundle', 'gemini.js');
    if (fs.existsSync(p)) return p;
  }
  return null;
}

const geminiJs = findGeminiJs();
if (!geminiJs) {
  console.error('[gemini-bridge] @google/gemini-cli not found. Run: npm install -g @google/gemini-cli');
  process.exit(1);
}
console.error(`[gemini-bridge] using gemini entry: ${geminiJs}`);

// Run `node gemini.js -m <model>` with prompt on stdin — no shell, no arg-length limits
const result = spawnSync(process.execPath, [geminiJs, '-m', model], {
  input: prompt,
  encoding: 'utf8',
  maxBuffer: 10 * 1024 * 1024,
  timeout: 180_000,
  stdio: ['pipe', 'pipe', 'pipe'],
});

if (result.error) {
  console.error('[gemini-bridge] Failed to spawn gemini CLI:', result.error.message);
  process.exit(1);
}

if (result.stderr) process.stderr.write(result.stderr);
if (result.stdout) process.stdout.write(result.stdout);
process.exit(result.status ?? 0);
