import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { resolve } from "node:path";
import { VitePWA } from "vite-plugin-pwa";

const publicBase = process.env.VERCEL ? "/" : "/Ironlog/";
const appPath = `${publicBase}app/`;

export default defineConfig({
  base: publicBase,
  plugins: [
    react(),
    VitePWA({
      registerType: "prompt",
      injectRegister: null,
      includeManifestIcons: false,
      // All public assets are matched below. Duplicating them in includeAssets
      // creates conflicting null/hash revisions and prevents Workbox installation.
      manifest: {
        name: "IronLog",
        short_name: "IronLog",
        description: "Train. Recover. Prove it.",
        start_url: appPath,
        scope: publicBase,
        display: "standalone",
        background_color: "#121212",
        theme_color: "#121212",
        icons: [
          {
            src: "assets/icon-192.png",
            sizes: "192x192",
            type: "image/png",
            purpose: "any",
          },
          {
            src: "assets/icon-512.png",
            sizes: "512x512",
            type: "image/png",
            purpose: "any",
          },
        ],
      },
      workbox: {
        // Public native artwork has stable names: revision it on every content change.
        dontCacheBustURLsMatching: /-[A-Za-z0-9_-]{8}\.(?:js|css)$/,
        globPatterns: ["**/*.{js,css,html,png,webp,svg,ttf,json}", "licenses/fonts/*.txt", "licenses/Lexend-OFL.txt"],
        globIgnores: [
          "**/assets/forgefox_*.png",
          "**/assets/iron_grade_*.png",
          "**/assets/recovery_circuit_emblem.png",
          "**/assets/ic_forge_streak_dumbbell.png",
          "**/assets/splashscreen_logo.png",
        ],
        navigateFallback: `${appPath}index.html`,
        navigateFallbackAllowlist: [
          publicBase === "/" ? /^\/app\/?$/ : /^\/Ironlog\/app\/?$/,
        ],
        cleanupOutdatedCaches: false,
        skipWaiting: false,
        clientsClaim: false,
      },
    }),
  ],
  build: {
    rollupOptions: {
      input: {
        landing: resolve(import.meta.dirname, "index.html"),
        app: resolve(import.meta.dirname, "app/index.html"),
      },
    },
  },
  test: {
    include: ["tests/**/*.test.{ts,tsx}"],
    environment: "happy-dom",
    setupFiles: ["tests/setup.ts"],
  },
});
