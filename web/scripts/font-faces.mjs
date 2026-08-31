import { readFile, writeFile } from 'node:fs/promises';
const registry=JSON.parse(await readFile(new URL('../src/generated/fonts.json',import.meta.url),'utf8'));
const css=registry.fonts.filter(f=>f.id!=='lexend').map(f=>`@font-face {
  font-family: "IronLog ${f.id}";
  src: url("/Ironlog/${f.file}") format("truetype");
  font-weight: ${f.minWeight} ${f.maxWeight};
  font-style: normal;
  font-display: swap;
}`).join('\n');
await writeFile(new URL('../src/generated/font-faces.css',import.meta.url),`/* Generated from fonts.json. Original font files and OFL licenses retained. */\n${css}\n`);
