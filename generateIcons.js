const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

try { require.resolve('sharp'); } catch(e) {
  execSync('npm install sharp', { stdio: 'inherit' });
}

const sharp = require('sharp');

const SRC = 'C:/Users/prana/Downloads/ChatGPT Image Mar 30, 2026, 07_53_08 PM.png';
const RES = 'android/app/src/main/res';
const BG = { r: 26, g: 26, b: 26, alpha: 1 }; // #1a1a1a

// Adaptive icon sizes (foreground layer = full canvas, icon uses ~66% safe zone with padding)
const MIPMAP = [
  { dir: 'mipmap-mdpi',    legacy: 48,  fg: 108 },
  { dir: 'mipmap-hdpi',    legacy: 72,  fg: 162 },
  { dir: 'mipmap-xhdpi',   legacy: 96,  fg: 216 },
  { dir: 'mipmap-xxhdpi',  legacy: 144, fg: 324 },
  { dir: 'mipmap-xxxhdpi', legacy: 192, fg: 432 },
];

const SPLASH = [
  { dir: 'drawable-mdpi',    size: 96  },
  { dir: 'drawable-hdpi',    size: 144 },
  { dir: 'drawable-xhdpi',   size: 192 },
  { dir: 'drawable-xxhdpi',  size: 288 },
  { dir: 'drawable-xxxhdpi', size: 384 },
];

async function makeForeground(size) {
  // Safe zone = 66.67% of total, so icon fills 66% with ~17% padding each side
  const iconSize = Math.round(size * 0.6);
  const pad = Math.round((size - iconSize) / 2);

  const resized = await sharp(SRC)
    .resize(iconSize, iconSize, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .toBuffer();

  return sharp({
    create: { width: size, height: size, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } }
  })
    .composite([{ input: resized, top: pad, left: pad }])
    .webp()
    .toBuffer();
}

async function makeLegacy(size) {
  // Legacy icon: icon on #1a1a1a background
  const iconSize = Math.round(size * 0.75);
  const pad = Math.round((size - iconSize) / 2);

  const resized = await sharp(SRC)
    .resize(iconSize, iconSize, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .toBuffer();

  return sharp({
    create: { width: size, height: size, channels: 3, background: BG }
  })
    .composite([{ input: resized, top: pad, left: pad }])
    .webp()
    .toBuffer();
}

// Write background color XML
function writeBackgroundXml() {
  const xmlPath = path.join(RES, 'drawable/ic_launcher_background.xml');
  const xml = `<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#1a1a1a"/>
</shape>`;
  fs.writeFileSync(xmlPath, xml);
  console.log('Updated background XML');
}

async function run() {
  writeBackgroundXml();

  for (const { dir, legacy, fg } of MIPMAP) {
    const d = path.join(RES, dir);
    const fgBuf = await makeForeground(fg);
    const legBuf = await makeLegacy(legacy);

    fs.writeFileSync(path.join(d, 'ic_launcher_foreground.webp'), fgBuf);
    fs.writeFileSync(path.join(d, 'ic_launcher.webp'), legBuf);
    fs.writeFileSync(path.join(d, 'ic_launcher_round.webp'), legBuf);
    console.log('Done:', dir);
  }

  for (const { dir, size } of SPLASH) {
    const d = path.join(RES, dir);
    const iconSize = Math.round(size * 0.7);
    const pad = Math.round((size - iconSize) / 2);
    const resized = await sharp(SRC)
      .resize(iconSize, iconSize, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
      .toBuffer();
    await sharp({
      create: { width: size, height: size, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } }
    })
      .composite([{ input: resized, top: pad, left: pad }])
      .png()
      .toFile(path.join(d, 'splashscreen_logo.png'));
    console.log('Splash:', dir);
  }

  // Also copy to expo assets folder
  const iconBuf = await makeLegacy(1024);
  await sharp(iconBuf).png().toFile('assets/icon.png');
  await sharp(iconBuf).png().toFile('assets/adaptive-icon.png');
  console.log('\nAll icons done!');
}

run().catch(console.error);
