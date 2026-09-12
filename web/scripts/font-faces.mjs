import { readFile, writeFile } from 'node:fs/promises';
const registry=JSON.parse(await readFile(new URL('../src/generated/fonts.json',import.meta.url),'utf8'));
const publicBase=process.env.VERCEL ? '/' : '/Ironlog/';
const css=registry.fonts.map(f=>`@font-face {
  font-family: "${f.id==='lexend' ? 'Lexend' : `IronLog ${f.id}`}";
  src: url("${publicBase}${f.file}") format("truetype");
  font-weight: ${f.minWeight} ${f.maxWeight};
  font-style: normal;
  font-display: swap;
}`).join('\n');
await writeFile(new URL('../src/generated/font-faces.css',import.meta.url),`/* Generated from fonts.json. Original font files and OFL licenses retained. */\n${css}\n`);
