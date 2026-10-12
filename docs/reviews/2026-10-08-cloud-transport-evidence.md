# Browser cloud transport evidence — 8 October 2026

Official sources checked during whole-plan implementation:
- OpenAI API overview: https://developers.openai.com/api/reference/overview
- OpenAI TypeScript SDK browser support: https://github.com/openai/openai-node#requirements
- Google API key security: https://ai.google.dev/gemini-api/docs/api-key
- Anthropic TypeScript SDK browser support: https://github.com/anthropics/anthropic-sdk-typescript#requirements
- OpenRouter REST fetch example: https://openrouter.ai/docs/quickstart

OpenAI and Anthropic SDKs document opt-in browser support while warning about credential exposure. Google advises server-side protection of production keys. This app must never distribute a project/provider credential in its bundle. A user's optional own credential can be entered explicitly for a session and used only for an action they request; the UI must disclose that browser scripts can access it. Configuration can be portable, credentials cannot. No arbitrary-URL credential proxy is authorized or required.

Provider presets are configuration, not a guarantee that an endpoint accepts the deployed browser origin. Transport failures (including CORS) must preserve manual prompt/paste, offer retry or configuration changes, and never claim generation succeeded. Custom endpoints need HTTPS (loopback HTTP only for local development), no URL credentials/query keys, no redirects carrying auth, bounded responses, timeout/cancellation and obsolete-result suppression. Requests must use headers for keys, avoid logs/raw provider error echo, and exclude photos/unrelated historical data. Built-in mode makes zero requests.

Use native protocols/presets as the parity reference; let users edit current model IDs. The above verification does not prove every region, provider plan, model or live paid account works. Tests use synthetic responses; no owner key or paid request is used in QA.
