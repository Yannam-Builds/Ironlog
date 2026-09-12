# Vercel production deployment — 12 September 2026

Current clean-redeploy URL: `https://ironlog-pro-fresh.vercel.app`

Previous production URL retained for rollback: `https://ironlogpro.vercel.app`

## Clean redeployment

- Vercel project: `pranav-yannam/ironlog-pro-fresh`
- Project ID: `prj_HJlkDUo7OEgGgw9lY9lo4kQncMcF`
- Deployment: `dpl_2JNpGqLuGcwzYFQaZ51MZtCDehaa`
- Immutable deployment URL: `https://ironlog-pro-fresh-dhr412hrp-pranav-yannam.vercel.app`
- State after deployment: Ready
- Vercel SSO deployment protection: disabled
- GitHub repository: connected by Vercel during project creation
- Vercel Root Directory: `web`, matching the repository's monorepo layout
- Verification before upload: 24 test files and 138 tests passed; the production build and 110-file output check passed.
- Verification after upload: Vercel returned HTTP 200. Cloudflare DNS-over-HTTPS returned `216.198.79.195` and `64.29.17.195`; direct TLS requests through the public record returned HTTP 200 for `/`, `/app/`, and `/manifest.webmanifest`.

The clean project reproduces the same local access failure because the router returns `127.0.0.1` for the new hostname too. This confirms that deleting or rebuilding the Vercel project cannot repair access from this network. Public DNS and direct TLS verification prove the deployment itself is available.

## Previous deployment

The previous `pranav-yannam/web` project and all of its deployments were deleted at the owner's request after the clean replacement was Ready. The details below remain only as an audit record; its URLs are no longer expected to work.

- Vercel project: `pranav-yannam/web`
- Deployment: `dpl_C1ZTDtohn4hGa7trumhBzGtpzt3V`
- Immutable deployment URL: `https://web-qvqqtitg7-pranav-yannam.vercel.app`
- State after deployment: Ready
- Remote build: completed in 11 seconds; 159 modules transformed; PWA service worker generated with 89 precache entries.

The build selects root paths on Vercel and retains `/Ironlog/` paths for GitHub Pages. This covers HTML icons, bundled font URLs, the web manifest, service-worker navigation fallback and the full-screen `/app/` entry.

Post-deploy verification returned HTTP 200 for `/`, `/app/`, `/manifest.webmanifest`, `/sw.js`, the logo and the Lexend font. Chromium then completed the live first-launch/theme-transfer journey and a persisted workout/reload/history journey against the generated production alias.

The Vercel CLI could not attach the GitHub repository automatically because the Vercel GitHub integration did not report access to `Yannam-Builds/Ironlog`. Manual production deployment is linked locally through the ignored `.vercel/project.json`. Automatic Vercel deploys require granting that integration repository access or configuring the project root as `web` in the Vercel dashboard.

Vercel SSO deployment protection was disabled after the first public-alias check showed a login redirect. A direct TLS request to Vercel then returned HTTP 200 for `ironlogpro.vercel.app`.

This workstation's router DNS at `192.168.0.1` returns `127.0.0.1` for every `*.vercel.app` hostname, including randomized nonexistent names. The Windows hosts file was not changed: an attempted append was denied before any write. Direct DNS-over-HTTPS queries return Vercel's public addresses and direct TLS verification succeeds, isolating the remaining Chrome error to the router's wildcard DNS response. Chrome Secure DNS or a separately owned custom domain is required to bypass that local network policy.
